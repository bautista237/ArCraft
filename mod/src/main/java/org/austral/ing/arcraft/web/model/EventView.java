package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.UUID;

/** Read-model of an event row (admin-scheduled events shown as dashboard banners). */
public class EventView {

    private final UUID id;
    private final String title;
    private final String description;
    private final Instant startDate;
    private final Instant endDate;
    private final Instant createdAt;
    private final Integer remindDaysBefore;
    private final boolean remindDuring;
    private final boolean beforeReminderSent;
    private final boolean duringReminderSent;

    public EventView(UUID id, String title, String description, Instant startDate, Instant endDate,
                     Instant createdAt, Integer remindDaysBefore, boolean remindDuring,
                     boolean beforeReminderSent, boolean duringReminderSent) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
        this.remindDaysBefore = remindDaysBefore;
        this.remindDuring = remindDuring;
        this.beforeReminderSent = beforeReminderSent;
        this.duringReminderSent = duringReminderSent;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getRemindDaysBefore() { return remindDaysBefore; }
    public boolean isRemindDuring() { return remindDuring; }
    public boolean isBeforeReminderSent() { return beforeReminderSent; }
    public boolean isDuringReminderSent() { return duringReminderSent; }
}
