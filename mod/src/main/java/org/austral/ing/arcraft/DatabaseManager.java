package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import org.austral.ing.arcraft.db.Database;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Static façade the mod's trackers/commands write through. Since the single-jar rewrite the
 * actual datasource lives in {@link Database} (HikariCP, shared with the embedded web server);
 * this class keeps its historical API — a dedicated long-lived connection driven by a
 * single-writer executor — so the event-handler call sites stay unchanged.
 */
public final class DatabaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Connection connection;
    private static ExecutorService writer;

    private DatabaseManager() {}

    public static synchronized void init() {
        if (connection != null) return;
        try {
            Database.open();
            org.austral.ing.arcraft.db.Seeder.run();
            // One pooled connection reserved for the writer thread (returned on close()).
            connection = Database.pool().getConnection();
            connection.setAutoCommit(true);
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ArCraft-DB-Writer");
                t.setDaemon(true);
                return t;
            });
            LOGGER.info("[ArCraft] Database writer ready ({})", Database.dialect());
        } catch (Exception e) {
            LOGGER.error("[ArCraft] Failed to initialize database", e);
            throw new RuntimeException(e);
        }
    }

    public static synchronized void close() {
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
        if (connection != null) {
            try {
                connection.close(); // returns to the pool
            } catch (SQLException e) {
                LOGGER.error("[ArCraft] Error closing DB connection", e);
            }
            connection = null;
        }
        Database.close();
    }

    public static Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("DatabaseManager has not been initialized");
        }
        return connection;
    }

    /** Records the server's online-mode into server_config (single row) for the dashboard. */
    public static void recordOnlineMode(boolean onlineMode) {
        if (connection == null) return;
        try {
            boolean exists;
            try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM server_config");
                 ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
            if (exists) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE server_config SET online_mode = ?")) {
                    ps.setBoolean(1, onlineMode);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO server_config (id, online_mode) VALUES (?, ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setBoolean(2, onlineMode);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] Failed to record online_mode", e);
        }
    }

    public static void submit(Runnable task) {
        if (writer == null) {
            LOGGER.warn("[ArCraft] DB writer not running, dropping task");
            return;
        }
        writer.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                LOGGER.error("[ArCraft] DB task failed", t);
            }
        });
    }
}
