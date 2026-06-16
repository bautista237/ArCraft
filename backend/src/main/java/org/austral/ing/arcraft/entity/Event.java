package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event")
@Getter @Setter @NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(nullable = false)
    private Instant startDate;

    @Column(nullable = false)
    private Instant endDate;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Legacy flag (kept for schema compatibility).
    @Column(nullable = false)
    private boolean reminderSent = false;

    // Email reminders: send one "starts in N days" mail (if set) and/or one "happening now" mail.
    @Column
    private Integer remindDaysBefore;

    @Column(nullable = false)
    private boolean remindDuring = false;

    @Column(nullable = false)
    private boolean beforeReminderSent = false;

    @Column(nullable = false)
    private boolean duringReminderSent = false;
}
