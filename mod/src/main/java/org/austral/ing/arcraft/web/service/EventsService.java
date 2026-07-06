package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.EventView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Admin-scheduled events: dashboard banners + CRUD (reminder mails live in MailScheduler). */
public final class EventsService {

    public static final EventsService INSTANCE = new EventsService();

    private static final DateTimeFormatter END_FMT = DateTimeFormatter.ofPattern("MMM d");

    private static final String COLS = "e.id, e.title, e.description, e.start_date, e.end_date, "
            + "e.created_at, e.remind_days_before, e.remind_during, e.before_reminder_sent, e.during_reminder_sent";

    private EventsService() {
    }

    public static EventView map(ResultSet rs) throws SQLException {
        int remind = rs.getInt("remind_days_before");
        boolean remindNull = rs.wasNull();
        return new EventView(Daos.uuid(rs, "id"), rs.getString("title"), rs.getString("description"),
                Daos.instant(rs, "start_date"), Daos.instant(rs, "end_date"), Daos.instant(rs, "created_at"),
                remindNull ? null : remind, rs.getBoolean("remind_during"),
                rs.getBoolean("before_reminder_sent"), rs.getBoolean("during_reminder_sent"));
    }

    /** View model for dashboard banners: days remaining + end date (month + day, no year). */
    public record BannerView(String title, String description,
                             boolean upcoming, long daysLeft, long startsInDays,
                             String endLabel) {}

    public List<EventView> getAllEvents() {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT " + COLS + " FROM event e ORDER BY e.start_date DESC")
                .map((rs, c) -> map(rs))
                .list());
    }

    /** Events that have not ended yet — shown as banners on the dashboard. */
    public List<EventView> getActiveAndUpcoming() {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT " + COLS + " FROM event e WHERE e.end_date > :now ORDER BY e.start_date ASC")
                .bind("now", Instant.now())
                .map((rs, c) -> map(rs))
                .list());
    }

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

    /** Creates an event; returns an error message or null on success. */
    public String createEvent(String title, String description, Instant startDate, Instant endDate,
                              Integer remindDaysBefore, boolean remindDuring) {
        if (title == null || title.isBlank()) return "Title is required.";
        if (startDate == null || endDate == null) return "Start and end dates are required.";
        if (endDate.isBefore(startDate)) return "End date must be after the start date.";
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        INSERT INTO event (id, title, description, start_date, end_date, created_at,
                                           reminder_sent, remind_days_before, remind_during,
                                           before_reminder_sent, during_reminder_sent)
                        VALUES (:id, :title, :desc, :start, :end, :created, FALSE, :remindDays, :remindDuring, FALSE, FALSE)
                        """)
                .bind("id", UUID.randomUUID())
                .bind("title", title.trim())
                .bind("desc", description == null ? "" : description.trim())
                .bind("start", startDate)
                .bind("end", endDate)
                .bind("created", Instant.now())
                .bind("remindDays", remindDaysBefore != null && remindDaysBefore > 0 ? remindDaysBefore : null)
                .bind("remindDuring", remindDuring)
                .execute());
        return null;
    }

    public void deleteEvent(UUID id) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("DELETE FROM event WHERE id = :id").bind("id", id).execute());
    }
}
