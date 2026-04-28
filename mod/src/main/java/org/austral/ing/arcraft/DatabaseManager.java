package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JDBC_URL = "jdbc:h2:file:./arcraft-data;AUTO_SERVER=TRUE";
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASS = "";

    private static Connection connection;
    private static ExecutorService writer;

    private DatabaseManager() {}

    public static synchronized void init() {
        if (connection != null) return;
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
            connection.setAutoCommit(true);
            createSchema();
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ArCraft-DB-Writer");
                t.setDaemon(true);
                return t;
            });
            LOGGER.info("[ArCraft] H2 database opened at {}", JDBC_URL);
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
                connection.close();
            } catch (SQLException e) {
                LOGGER.error("[ArCraft] Error closing DB connection", e);
            }
            connection = null;
            LOGGER.info("[ArCraft] H2 database closed");
        }
    }

    public static Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("DatabaseManager has not been initialized");
        }
        return connection;
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

    private static void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS clan (
                    id UUID PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    tag VARCHAR(255) NOT NULL,
                    leader_id UUID NOT NULL,
                    friendly_fire_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP NOT NULL
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS player (
                    id UUID PRIMARY KEY,
                    username VARCHAR(255) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    player_password_plain VARCHAR(255),
                    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
                    clan_id UUID,
                    created_at TIMESTAMP NOT NULL
                )
                """);

            st.execute("""
                ALTER TABLE player ADD COLUMN IF NOT EXISTS player_password_plain VARCHAR(255)
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    id UUID PRIMARY KEY,
                    player_id UUID NOT NULL UNIQUE,
                    kills BIGINT NOT NULL DEFAULT 0,
                    deaths BIGINT NOT NULL DEFAULT 0,
                    damage_dealt REAL NOT NULL DEFAULT 0,
                    damage_received REAL NOT NULL DEFAULT 0,
                    mobs_killed BIGINT NOT NULL DEFAULT 0,
                    blocks_placed BIGINT NOT NULL DEFAULT 0,
                    blocks_mined BIGINT NOT NULL DEFAULT 0,
                    items_crafted BIGINT NOT NULL DEFAULT 0,
                    distance_walked BIGINT NOT NULL DEFAULT 0,
                    distance_swum BIGINT NOT NULL DEFAULT 0,
                    distance_flown BIGINT NOT NULL DEFAULT 0,
                    distance_sailed BIGINT NOT NULL DEFAULT 0,
                    shots_fired BIGINT NOT NULL DEFAULT 0,
                    shots_hit BIGINT NOT NULL DEFAULT 0,
                    longest_shot_blocks BIGINT NOT NULL DEFAULT 0
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS block_stat_entry (
                    id UUID PRIMARY KEY,
                    player_id UUID NOT NULL,
                    block_type VARCHAR(255) NOT NULL,
                    mined BIGINT NOT NULL DEFAULT 0,
                    placed BIGINT NOT NULL DEFAULT 0,
                    UNIQUE (player_id, block_type)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS item_stat_entry (
                    id UUID PRIMARY KEY,
                    player_id UUID NOT NULL,
                    item_type VARCHAR(255) NOT NULL,
                    count BIGINT NOT NULL DEFAULT 0,
                    UNIQUE (player_id, item_type)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS mob_stat_entry (
                    id UUID PRIMARY KEY,
                    player_id UUID NOT NULL,
                    mob_type VARCHAR(255) NOT NULL,
                    count BIGINT NOT NULL DEFAULT 0,
                    UNIQUE (player_id, mob_type)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS pvp_event (
                    id UUID PRIMARY KEY,
                    killer_id UUID NOT NULL,
                    victim_id UUID NOT NULL,
                    started_at TIMESTAMP NOT NULL,
                    ended_at TIMESTAMP NOT NULL
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS pvp_hit (
                    id UUID PRIMARY KEY,
                    pvp_event_id UUID NOT NULL,
                    attacker_id UUID NOT NULL,
                    damage REAL NOT NULL,
                    weapon VARCHAR(255) NOT NULL,
                    hit_at TIMESTAMP NOT NULL
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS event_log (
                    id UUID PRIMARY KEY,
                    type VARCHAR(64) NOT NULL,
                    description VARCHAR(1024) NOT NULL,
                    player_id UUID,
                    occurred_at TIMESTAMP NOT NULL
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS server_config (
                    id UUID PRIMARY KEY,
                    server_start_date TIMESTAMP,
                    server_name VARCHAR(255)
                )
                """);
        }
    }
}
