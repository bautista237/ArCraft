package org.austral.ing.arcraft.service;

import jakarta.mail.internet.MimeMessage;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

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
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${arcraft.app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Verification codes (every 30s) ───────────────────────────────────────
    @Scheduled(fixedRate = 30_000L)
    @Transactional
    public void sendPendingVerifications() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        for (Player p : playerRepository.findByVerificationCodeIsNotNullAndVerificationSentFalse()) {
            if (p.getEmail() == null || p.getEmail().isBlank()) { p.setVerificationSent(true); continue; }
            Context ctx = new Context();
            ctx.setVariable("username", p.getUsername());
            ctx.setVariable("code", p.getVerificationCode());
            ctx.setVariable("baseUrl", baseUrl);
            boolean ok = sendHtml(sender, p.getEmail(), "[ArCraft] Your verification code",
                    templateEngine.process("email/verification", ctx));
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
        int sent = 0;
        for (Player p : recipients) {
            if (p.getEmail() == null || p.getEmail().isBlank()) continue;
            Context ctx = new Context();
            ctx.setVariable("username", p.getUsername());
            ctx.setVariable("title", ev.getTitle());
            ctx.setVariable("when", when);
            ctx.setVariable("description", ev.getDescription());
            ctx.setVariable("baseUrl", baseUrl);
            if (sendHtml(sender, p.getEmail(), subject, templateEngine.process("email/event-reminder", ctx))) sent++;
        }
        log.info("[ArCraft] Event reminder '{}' sent to {} recipients", ev.getTitle(), sent);
    }

    /** Sends an HTML email (rendered from a Thymeleaf template). */
    private boolean sendHtml(JavaMailSender sender, String to, String subject, String html) {
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress, "ArCraft");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML
            sender.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("[ArCraft] Failed to send email to {}: {}", to, e.toString());
            return false;
        }
    }
}
