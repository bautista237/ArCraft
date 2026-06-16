package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Event;
import org.austral.ing.arcraft.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private static final DateTimeFormatter END_FMT = DateTimeFormatter.ofPattern("MMM d");

    private final EventRepository eventRepository;

    /** View model for dashboard banners: days remaining + end date (month + day, no year). */
    public record BannerView(String title, String description,
                             boolean upcoming, long daysLeft, long startsInDays,
                             String endLabel) {}

    public List<Event> getAllEvents() {
        return eventRepository.findAllByOrderByStartDateDesc();
    }

    /** Events that have not ended yet — shown as banners on the dashboard. */
    @Transactional(readOnly = true)
    public List<Event> getActiveAndUpcoming() {
        return eventRepository.findByEndDateAfterOrderByStartDateAsc(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<BannerView> getActiveBanners() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        return getActiveAndUpcoming().stream().map(e -> {
            boolean upcoming = e.getStartDate().isAfter(now);
            long daysLeft = Math.max(0, ChronoUnit.DAYS.between(now, e.getEndDate()));
            long startsIn = Math.max(0, ChronoUnit.DAYS.between(now, e.getStartDate()));
            String endLabel = e.getEndDate().atZone(zone).format(END_FMT);
            return new BannerView(e.getTitle(), e.getDescription(), upcoming, daysLeft, startsIn, endLabel);
        }).toList();
    }

    public String createEvent(String title, String description, Instant startDate, Instant endDate,
                              Integer remindDaysBefore, boolean remindDuring) {
        if (title == null || title.isBlank()) {
            return "Title is required.";
        }
        if (startDate == null || endDate == null) {
            return "Start and end dates are required.";
        }
        if (endDate.isBefore(startDate)) {
            return "End date must be after the start date.";
        }
        Event event = new Event();
        event.setTitle(title.trim());
        event.setDescription(description == null ? "" : description.trim());
        event.setStartDate(startDate);
        event.setEndDate(endDate);
        event.setRemindDaysBefore(remindDaysBefore != null && remindDaysBefore > 0 ? remindDaysBefore : null);
        event.setRemindDuring(remindDuring);
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
        return null;
    }

    public void deleteEvent(UUID id) {
        eventRepository.deleteById(id);
    }
}
