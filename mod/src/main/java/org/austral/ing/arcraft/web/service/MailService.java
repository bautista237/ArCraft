package org.austral.ing.arcraft.web.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.austral.ing.arcraft.ArcraftConfig;
import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.EventView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends ArCraft's HTML emails (in-game verification codes + event reminders) over SMTP using
 * Angus Mail, with the same Thymeleaf templates as before ({@code email/verification},
 * {@code email/event-reminder}). Replaces Spring's JavaMailSender + @Scheduled: a small
 * {@link ScheduledExecutorService} polls for pending verifications (30s) and due event
 * reminders (10 min). Started/stopped with the web server; inert unless [mail] is configured.
 */
public final class MailService {

    public static final MailService INSTANCE = new MailService();

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private ScheduledExecutorService scheduler;

    private MailService() {
    }

    public synchronized void start() {
        if (scheduler != null || !ArcraftConfig.mailConfigured()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ArCraft-Mail");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::safeSendPendingVerifications, 15, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::safeSendDueEventReminders, 1, 10, TimeUnit.MINUTES);
        log.info("[ArCraft] Mail scheduler started ({} as {})",
                ArcraftConfig.MAIL_HOST.get(), ArcraftConfig.MAIL_USERNAME.get());
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void safeSendPendingVerifications() {
        try {
            sendPendingVerifications();
        } catch (Exception e) {
            log.warn("[ArCraft] verification mail sweep failed: {}", e.toString());
        }
    }

    private void safeSendDueEventReminders() {
        try {
            sendDueEventReminders();
        } catch (Exception e) {
            log.warn("[ArCraft] event reminder sweep failed: {}", e.toString());
        }
    }

    // ── Verification codes (every 30s) ───────────────────────────────────────

    private record Pending(UUID id, String username, String email, String code) {}

    private void sendPendingVerifications() {
        List<Pending> pending = Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT id, username, email, verification_code FROM player
                        WHERE verification_code IS NOT NULL AND verification_sent = FALSE
                        """)
                .map((rs, c) -> new Pending(Daos.uuid(rs, "id"), rs.getString("username"),
                        rs.getString("email"), rs.getString("verification_code")))
                .list());
        for (Pending p : pending) {
            boolean ok = false;
            if (p.email() != null && !p.email().isBlank()) {
                String html = Renderer.renderToString("email/verification", Map.of(
                        "username", p.username(),
                        "code", p.code(),
                        "baseUrl", ArcraftConfig.baseUrl()));
                ok = sendHtml(p.email(), "[ArCraft] Your verification code", html);
            }
            // mark attempted either way so we don't loop
            Database.jdbi().useHandle(h -> h
                    .createUpdate("UPDATE player SET verification_sent = TRUE WHERE id = :id")
                    .bind("id", p.id()).execute());
            if (ok) log.info("[ArCraft] Sent verification code to {} ({})", p.username(), p.email());
        }
    }

    // ── Event reminders (every 10 min) ───────────────────────────────────────

    private record Recipient(String username, String email) {}

    private void sendDueEventReminders() {
        List<Recipient> recipients = Database.jdbi().withHandle(h -> h
                .createQuery("SELECT username, email FROM player WHERE email_verified = TRUE")
                .map((rs, c) -> new Recipient(rs.getString("username"), rs.getString("email")))
                .list());
        if (recipients.isEmpty()) return;
        Instant now = Instant.now();

        for (EventView ev : EventsService.INSTANCE.getAllEvents()) {
            // "Starts in N days" reminder
            if (ev.getRemindDaysBefore() != null && !ev.isBeforeReminderSent()) {
                Instant remindAt = ev.getStartDate().minus(ev.getRemindDaysBefore(), ChronoUnit.DAYS);
                if (!now.isBefore(remindAt) && now.isBefore(ev.getStartDate())) {
                    long days = Math.max(0, ChronoUnit.DAYS.between(now, ev.getStartDate()));
                    blast(recipients, "[ArCraft] Upcoming event: " + ev.getTitle(), ev,
                            "starts in " + days + " day" + (days == 1 ? "" : "s")
                                    + " (" + FMT.format(ev.getStartDate()) + ")");
                    markSent(ev.getId(), "before_reminder_sent");
                }
            }
            // "Happening now" reminder
            if (ev.isRemindDuring() && !ev.isDuringReminderSent()
                    && !now.isBefore(ev.getStartDate()) && !now.isAfter(ev.getEndDate())) {
                blast(recipients, "[ArCraft] Event happening now: " + ev.getTitle(), ev,
                        "is happening now! Ends " + FMT.format(ev.getEndDate()));
                markSent(ev.getId(), "during_reminder_sent");
            }
        }
    }

    private void markSent(UUID eventId, String column) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("UPDATE event SET " + column + " = TRUE WHERE id = :id")
                .bind("id", eventId).execute());
    }

    private void blast(List<Recipient> recipients, String subject, EventView ev, String when) {
        int sent = 0;
        for (Recipient p : recipients) {
            if (p.email() == null || p.email().isBlank()) continue;
            String html = Renderer.renderToString("email/event-reminder", Map.of(
                    "username", p.username(),
                    "title", ev.getTitle(),
                    "when", when,
                    "description", ev.getDescription(),
                    "baseUrl", ArcraftConfig.baseUrl()));
            if (sendHtml(p.email(), subject, html)) sent++;
        }
        log.info("[ArCraft] Event reminder '{}' sent to {} recipients", ev.getTitle(), sent);
    }

    // ── SMTP ─────────────────────────────────────────────────────────────────

    private Session smtpSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", ArcraftConfig.MAIL_HOST.get());
        props.put("mail.smtp.port", String.valueOf(ArcraftConfig.MAIL_PORT.get()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        ArcraftConfig.MAIL_USERNAME.get(), ArcraftConfig.MAIL_PASSWORD.get());
            }
        });
    }

    /** Sends an HTML email. Returns whether it was accepted by the SMTP server. */
    private boolean sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = new MimeMessage(smtpSession());
            String from = ArcraftConfig.MAIL_FROM.get().isBlank()
                    ? ArcraftConfig.MAIL_USERNAME.get() : ArcraftConfig.MAIL_FROM.get();
            msg.setFrom(new InternetAddress(from, "ArCraft"));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject(subject, "UTF-8");
            msg.setContent(html, "text/html; charset=UTF-8");
            Transport.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("[ArCraft] Failed to send email to {}: {}", to, e.toString());
            return false;
        }
    }
}
