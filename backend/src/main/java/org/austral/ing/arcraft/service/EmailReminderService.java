package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Event;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.EventRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * Sends a one-time reminder email to every player with an address on file when an event is
 * about to start. Only active when {@code arcraft.mail.enabled=true} AND a JavaMailSender is
 * configured (spring.mail.*), so the rest of the app is unaffected when email is off.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arcraft.mail.enabled", havingValue = "true")
public class EmailReminderService {

    private static final Logger log = LoggerFactory.getLogger(EmailReminderService.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final EventRepository eventRepository;
    private final PlayerRepository playerRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** Runs hourly; reminds players of events starting within the next 24 hours. */
    @Scheduled(fixedRate = 60 * 60 * 1000L)
    @Transactional
    public void sendUpcomingEventReminders() {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return; // mail not configured
        }
        Instant threshold = Instant.now().plus(24, ChronoUnit.HOURS);
        List<Event> due = eventRepository.findByReminderSentFalseAndStartDateLessThanEqual(threshold);
        if (due.isEmpty()) return;

        List<Player> recipients = playerRepository.findByEmailIsNotNull();

        for (Event event : due) {
            for (Player p : recipients) {
                if (p.getEmail() == null || p.getEmail().isBlank()) continue;
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setTo(p.getEmail());
                    msg.setSubject("[ArCraft] Event starting soon: " + event.getTitle());
                    msg.setText("Hi " + p.getUsername() + ",\n\n"
                            + "The event \"" + event.getTitle() + "\" starts at " + FMT.format(event.getStartDate()) + ".\n\n"
                            + (event.getDescription() == null ? "" : event.getDescription() + "\n\n")
                            + "See you on the server!\n— ArCraft");
                    mailSender.send(msg);
                } catch (Exception e) {
                    log.warn("[ArCraft] Failed to email {} about event {}", p.getEmail(), event.getTitle(), e);
                }
            }
            event.setReminderSent(true);
            eventRepository.save(event);
            log.info("[ArCraft] Sent reminders for event '{}' to {} recipients", event.getTitle(), recipients.size());
        }
    }
}
