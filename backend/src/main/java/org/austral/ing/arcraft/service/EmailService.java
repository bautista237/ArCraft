package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Event;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.EventRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sends ArCraft emails: in-game email verification codes and event reminders.
 * Only active when {@code arcraft.mail.enabled=true} and a JavaMailSender (spring.mail.*) is
 * configured. The mod records requests in the shared DB (it has no mail sender); this service,
 * running in the backend, dispatches the actual messages.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arcraft.mail.enabled", havingValue = "true")
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final EventRepository eventRepository;
    private final PlayerRepository playerRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    // ── Verification codes (every 30s) ───────────────────────────────────────
    @Scheduled(fixedRate = 30_000L)
    @Transactional
    public void sendPendingVerifications() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        for (Player p : playerRepository.findByVerificationCodeIsNotNullAndVerificationSentFalse()) {
            if (p.getEmail() == null || p.getEmail().isBlank()) { p.setVerificationSent(true); continue; }
            boolean ok = send(sender, p.getEmail(), "[ArCraft] Your verification code",
                    "Hi " + p.getUsername() + ",\n\n"
                    + "Your ArCraft email verification code is: " + p.getVerificationCode() + "\n\n"
                    + "In-game, run:  /email verify " + p.getVerificationCode() + "\n\n"
                    + "If you didn't request this, you can ignore this email.\n— ArCraft");
            p.setVerificationSent(true); // mark attempted either way so we don't loop
            playerRepository.save(p);
            if (ok) log.info("[ArCraft] Sent verification code to {} ({})", p.getUsername(), p.getEmail());
        }
    }

    // ── Event reminders (every 10 min) ───────────────────────────────────────
    @Scheduled(fixedRate = 10 * 60 * 1000L)
    @Transactional
    public void sendDueEventReminders() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        List<Player> recipients = playerRepository.findByEmailVerifiedTrue();
        if (recipients.isEmpty()) return;
        Instant now = Instant.now();

        for (Event ev : eventRepository.findAll()) {
            // "Starts in N days" reminder
            if (ev.getRemindDaysBefore() != null && !ev.isBeforeReminderSent()) {
                Instant remindAt = ev.getStartDate().minus(ev.getRemindDaysBefore(), ChronoUnit.DAYS);
                if (!now.isBefore(remindAt) && now.isBefore(ev.getStartDate())) {
                    long days = Math.max(0, ChronoUnit.DAYS.between(now, ev.getStartDate()));
                    blast(sender, recipients, "[ArCraft] Upcoming event: " + ev.getTitle(),
                            ev, "starts in " + days + " day" + (days == 1 ? "" : "s")
                                    + " (" + FMT.format(ev.getStartDate()) + ")");
                    ev.setBeforeReminderSent(true);
                    eventRepository.save(ev);
                }
            }
            // "Happening now" reminder
            if (ev.isRemindDuring() && !ev.isDuringReminderSent()
                    && !now.isBefore(ev.getStartDate()) && !now.isAfter(ev.getEndDate())) {
                blast(sender, recipients, "[ArCraft] Event happening now: " + ev.getTitle(),
                        ev, "is happening now! Ends " + FMT.format(ev.getEndDate()));
                ev.setDuringReminderSent(true);
                eventRepository.save(ev);
            }
        }
    }

    private void blast(JavaMailSender sender, List<Player> recipients, String subject, Event ev, String when) {
        String body = "The event \"" + ev.getTitle() + "\" " + when + ".\n\n"
                + (ev.getDescription() == null || ev.getDescription().isBlank() ? "" : ev.getDescription() + "\n\n")
                + "See you on the server!\n— ArCraft";
        int sent = 0;
        for (Player p : recipients) {
            if (p.getEmail() != null && !p.getEmail().isBlank() && send(sender, p.getEmail(), subject, body)) sent++;
        }
        log.info("[ArCraft] Event reminder '{}' sent to {} recipients", ev.getTitle(), sent);
    }

    private boolean send(JavaMailSender sender, String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("[ArCraft] Failed to send email to {}: {}", to, e.toString());
            return false;
        }
    }
}
