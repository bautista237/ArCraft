package org.austral.ing.arcraft.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates/upgrades the full ArCraft schema. Idempotent ({@code IF NOT EXISTS} everywhere) and
 * portable across H2 / PostgreSQL / MariaDB via {@link Dialect#ddl(String)} tokens. This is the
 * union of the schema the mod historically created plus the tables the old Spring backend used
 * to add via JPA (event, achievement, player_achievement, store_item, player_purchase,
 * coin_order) — so both a fresh server and an existing database end up identical.
 */
final class SchemaInit {

    private SchemaInit() {
    }

    static void run(Connection c, Dialect d) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String template : TABLES) {
                st.execute(d.ddl(template));
            }
            for (String template : UPGRADES) {
                st.execute(d.ddl(template));
            }
            for (String template : INDEXES) {
                st.execute(d.ddl(template));
            }
        }
    }

    private static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS clan (
                id ${UUID} PRIMARY KEY,
                name VARCHAR(255) NOT NULL UNIQUE,
                tag VARCHAR(255) NOT NULL,
                leader_id ${UUID} NOT NULL,
                friendly_fire_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                created_at ${TIMESTAMP} NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS player (
                id ${UUID} PRIMARY KEY,
                username VARCHAR(255) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                player_password_plain VARCHAR(255),
                is_admin BOOLEAN NOT NULL DEFAULT FALSE,
                clan_id ${UUID},
                created_at ${TIMESTAMP} NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS player_stats (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL UNIQUE,
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
            )""",
            """
            CREATE TABLE IF NOT EXISTS block_stat_entry (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL,
                block_type VARCHAR(255) NOT NULL,
                mined BIGINT NOT NULL DEFAULT 0,
                placed BIGINT NOT NULL DEFAULT 0,
                UNIQUE (player_id, block_type)
            )""",
            """
            CREATE TABLE IF NOT EXISTS item_stat_entry (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL,
                item_type VARCHAR(255) NOT NULL,
                count BIGINT NOT NULL DEFAULT 0,
                UNIQUE (player_id, item_type)
            )""",
            """
            CREATE TABLE IF NOT EXISTS mob_stat_entry (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL,
                mob_type VARCHAR(255) NOT NULL,
                count BIGINT NOT NULL DEFAULT 0,
                UNIQUE (player_id, mob_type)
            )""",
            """
            CREATE TABLE IF NOT EXISTS pvp_event (
                id ${UUID} PRIMARY KEY,
                killer_id ${UUID} NOT NULL,
                victim_id ${UUID} NOT NULL,
                started_at ${TIMESTAMP} NOT NULL,
                ended_at ${TIMESTAMP} NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS pvp_hit (
                id ${UUID} PRIMARY KEY,
                pvp_event_id ${UUID} NOT NULL,
                attacker_id ${UUID} NOT NULL,
                damage REAL NOT NULL,
                weapon VARCHAR(255) NOT NULL,
                hit_at ${TIMESTAMP} NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS event_log (
                id ${UUID} PRIMARY KEY,
                type VARCHAR(64) NOT NULL,
                description VARCHAR(1024) NOT NULL,
                player_id ${UUID},
                occurred_at ${TIMESTAMP} NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS server_config (
                id ${UUID} PRIMARY KEY,
                server_start_date ${TIMESTAMP},
                server_name VARCHAR(255)
            )""",
            """
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
                first_visited ${TIMESTAMP} NOT NULL,
                last_visited  ${TIMESTAMP} NOT NULL,
                UNIQUE (player_id, chunk_x, chunk_z, dimension)
            )""",
            """
            CREATE TABLE IF NOT EXISTS clan_message (
                id ${UUID} PRIMARY KEY,
                clan_id ${UUID} NOT NULL,
                sender_id ${UUID} NOT NULL,
                content VARCHAR(1024) NOT NULL,
                sent_at ${TIMESTAMP} NOT NULL
            )""",
            // ── formerly created by the Spring backend (JPA ddl-auto) ──
            """
            CREATE TABLE IF NOT EXISTS event (
                id ${UUID} PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                description VARCHAR(1024) NOT NULL,
                start_date ${TIMESTAMP} NOT NULL,
                end_date ${TIMESTAMP} NOT NULL,
                created_at ${TIMESTAMP} NOT NULL,
                reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
                remind_days_before INT,
                remind_during BOOLEAN NOT NULL DEFAULT FALSE,
                before_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
                during_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE
            )""",
            """
            CREATE TABLE IF NOT EXISTS achievement (
                id ${UUID} PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                description VARCHAR(255) NOT NULL,
                metric_key VARCHAR(255) NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS player_achievement (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL,
                achievement_id ${UUID} NOT NULL,
                earned_at ${TIMESTAMP} NOT NULL,
                UNIQUE (player_id, achievement_id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS store_item (
                id ${UUID} PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                description VARCHAR(1024) NOT NULL,
                price BIGINT NOT NULL DEFAULT 0,
                category VARCHAR(32) NOT NULL,
                effect VARCHAR(255),
                created_at ${TIMESTAMP} NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS player_purchase (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL,
                item_id ${UUID} NOT NULL,
                price_paid BIGINT NOT NULL DEFAULT 0,
                equipped BOOLEAN NOT NULL DEFAULT FALSE,
                purchased_at ${TIMESTAMP} NOT NULL,
                UNIQUE (player_id, item_id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS coin_order (
                id ${UUID} PRIMARY KEY,
                player_id ${UUID} NOT NULL,
                username VARCHAR(255) NOT NULL,
                coins BIGINT NOT NULL,
                amount ${DOUBLE} NOT NULL,
                status VARCHAR(16) NOT NULL,
                preference_id VARCHAR(255),
                payment_id VARCHAR(255),
                created_at ${TIMESTAMP} NOT NULL,
                paid_at ${TIMESTAMP}
            )""",
    };

    /**
     * Columns added over the mod's history. ADD COLUMN IF NOT EXISTS is supported by H2,
     * PostgreSQL and MariaDB alike, so existing databases upgrade in place with no data loss.
     */
    private static final String[] UPGRADES = {
            "ALTER TABLE player ADD COLUMN IF NOT EXISTS player_password_plain VARCHAR(255)",
            "ALTER TABLE player ADD COLUMN IF NOT EXISTS coins BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE player ADD COLUMN IF NOT EXISTS email VARCHAR(255)",
            "ALTER TABLE player ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE player ADD COLUMN IF NOT EXISTS verification_code VARCHAR(16)",
            "ALTER TABLE player ADD COLUMN IF NOT EXISTS verification_sent BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS blocks_mined BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS blocks_placed BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS stay_ticks BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE chunk_visit ADD COLUMN IF NOT EXISTS surface_y INT DEFAULT 0",
            "ALTER TABLE event_log ADD COLUMN IF NOT EXISTS image_url VARCHAR(512)",
            "ALTER TABLE pvp_hit ADD COLUMN IF NOT EXISTS victim_id ${UUID}",
            "ALTER TABLE server_config ADD COLUMN IF NOT EXISTS online_mode BOOLEAN",
            "ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS pvp_deaths BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS pvp_damage_dealt REAL NOT NULL DEFAULT 0",
            "ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS pvp_damage_received REAL NOT NULL DEFAULT 0",
    };

    /** Indexes for the hot web queries (profiles, PvP breakdowns, live feed, clan chat, map). */
    private static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS idx_pvp_event_killer ON pvp_event (killer_id)",
            "CREATE INDEX IF NOT EXISTS idx_pvp_event_victim ON pvp_event (victim_id)",
            "CREATE INDEX IF NOT EXISTS idx_pvp_hit_event ON pvp_hit (pvp_event_id)",
            "CREATE INDEX IF NOT EXISTS idx_event_log_time ON event_log (occurred_at)",
            "CREATE INDEX IF NOT EXISTS idx_event_log_player ON event_log (player_id)",
            "CREATE INDEX IF NOT EXISTS idx_clan_message_clan ON clan_message (clan_id, sent_at)",
            "CREATE INDEX IF NOT EXISTS idx_chunk_visit_dim ON chunk_visit (dimension)",
            "CREATE INDEX IF NOT EXISTS idx_coin_order_pref ON coin_order (preference_id)",
    };
}
