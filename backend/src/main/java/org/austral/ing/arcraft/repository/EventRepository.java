package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findAllByOrderByStartDateDesc();

    // Active or upcoming events (haven't ended yet), soonest-ending first — used for dashboard banners.
    List<Event> findByEndDateAfterOrderByStartDateAsc(Instant now);

    // Events starting within a window that still need a reminder email sent.
    List<Event> findByReminderSentFalseAndStartDateLessThanEqual(Instant threshold);
}
