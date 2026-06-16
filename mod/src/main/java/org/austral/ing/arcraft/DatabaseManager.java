package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Defaults to the server's working directory (production). Can be overridden for
    // local development via -Darcraft.db.url=... (see mod/build.gradle runs block).
    private static final String JDBC_URL = System.getProperty(
            "arcraft.db.url", "jdbc:h2:file:./arcraft-data;AUTO_SERVER=TRUE");
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASS = "";

    private static Connection connection;
    private static ExecutorService writer;

    private DatabaseManager() {}

    /**
     * Opens the H2 connection. Under NeoForge's modular classloading the mod class and
     * its library jars end up on different classloaders, so DriverManager.getConnection
     * (which only accepts drivers visible to the *calling* classloader) fails with
     * "No suitable driver found" even though H2 is on the classpath. We first try the
     * normal DriverManager path (works in a plain/production classloader), and fall back
     * to instantiating the H2 driver via a reachable classloader and calling connect()
     * directly, which bypasses DriverManager's classloader check.
     */
    private static Connection openConnection() throws SQLException {
        try {
            return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
        } catch (SQLException primary) {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", JDBC_USER);
            props.setProperty("password", JDBC_PASS);
            ClassLoader[] candidates = {
                    Thread.currentThread().getContextClassLoader(),
                    DatabaseManager.class.getClassLoader(),
                    ClassLoader.getSystemClassLoader()
            };
            for (ClassLoader cl : candidates) {
                if (cl == null) continue;
                try {
                    Class<?> driverClass = Class.forName("org.h2.Driver", true, cl);
                    java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
                    Connection c = driver.connect(JDBC_URL, props);
                    if (c != null) {
                        LOGGER.info("[ArCraft] H2 driver loaded via {}", cl);
                        return c;
                    }
                } catch (Throwable ignored) {
                    // try the next classloader
                }
            }
            throw primary;
        }
    }

    public static synchronized void init() {
        if (connection != null) return;
        try {
            connection = openConnection();
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

    /** The shared H2 URL — passed to the web backend so both processes use the same file. */
    public static String getJdbcUrl() {
        return JDBC_URL;
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
                ALTER TABLE player ADD COLUMN IF NOT EXISTS coins BIGINT NOT NULL DEFAULT 0
                """);

            // Email registration & verification (set in-game via /email, verified by code).
            st.execute("ALTER TABLE player ADD COLUMN IF NOT EXISTS email VARCHAR(255)");
            st.execute("ALTER TABLE player ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE");
            st.execute("ALTER TABLE player ADD COLUMN IF NOT EXISTS verification_code VARCHAR(16)");
            st.execute("ALTER TABLE player ADD COLUMN IF NOT EXISTS verification_sent BOOLEAN NOT NULL DEFAULT FALSE");

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

            st.execute("""
                CREATE TABLE IF NOT EXISTS chunk_visit (
                    id            VARCHAR(36) PRIMARY KEY,
                    player_id     VARCHAR(36) NOT NULL,
                    chunk_x       INT NOT NULL,
                    chunk_z       INT NOT NULL,
                    dimension     VARCHAR(64) NOT NULL DEFAULT 'minecraft:overworld',
                    biome         VARCHAR(64),
                    top_block     VARCHAR(64),
                    map_color_r   INT DEFAULT 100,
                    map_color_g   INT DEFAULT 140,
                    map_color_b   INT DEFAULT 100,
                    first_visited TIMESTAMP NOT NULL,
                    last_visited  TIMESTAMP NOT NULL,
                    UNIQUE (player_id, chunk_x, chunk_z, dimension)
                )
                """);

            // Heatmap counters (added incrementally so existing DBs upgrade cleanly).
            st.execute("ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS blocks_mined BIGINT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS blocks_placed BIGINT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS stay_ticks BIGINT NOT NULL DEFAULT 0");

            // Clan chat — shared by the web and in-game (the mod bridges both directions).
            st.execute("""
                CREATE TABLE IF NOT EXISTS clan_message (
                    id UUID PRIMARY KEY,
                    clan_id UUID NOT NULL,
                    sender_id UUID NOT NULL,
                    content VARCHAR(1024) NOT NULL,
                    sent_at TIMESTAMP NOT NULL
                )
                """);

            // Optional photo attached to live-feed events (e.g. the player's skin face).
            st.execute("ALTER TABLE event_log ADD COLUMN IF NOT EXISTS image_url VARCHAR(512)");

            // Chunk surface height — lets the web map shade terrain like a Minecraft map.
            st.execute("ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS surface_y INT DEFAULT 0");

            // Per-hit recipient, so the web can show who received each PvP hit.
            st.execute("ALTER TABLE pvp_hit ADD COLUMN IF NOT EXISTS victim_id UUID");

            // Server online-mode (premium vs cracked) drives which skin source the dashboard uses.
            st.execute("ALTER TABLE server_config ADD COLUMN IF NOT EXISTS online_mode BOOLEAN");

            // Differentiate total vs PvP deaths/damage.
            st.execute("ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS pvp_deaths BIGINT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS pvp_damage_dealt REAL NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS pvp_damage_received REAL NOT NULL DEFAULT 0");
        }
    }
}
