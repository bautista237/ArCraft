package org.austral.ing.arcraft.db;

import com.mojang.logging.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.austral.ing.arcraft.ArcraftConfig;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The single in-JVM datasource shared by the mod's trackers and the embedded web dashboard.
 * One process, one pool — the old two-process/"two H2 files out of sync" failure mode is gone
 * by construction.
 *
 * <p>H2 (default) lives in the {@code arcraft/} data folder; large servers can switch to
 * PostgreSQL / MariaDB in {@code config/arcraft-common.toml}. Pooling is HikariCP; SQL access
 * for the web layer goes through jdbi's fluent API.</p>
 */
public final class Database {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Dev/test override, e.g. -Darcraft.db.url=jdbc:h2:file:./arcraft-data;AUTO_SERVER=TRUE */
    private static final String URL_OVERRIDE = System.getProperty("arcraft.db.url", "");

    private static HikariDataSource pool;
    private static Jdbi jdbi;
    private static Dialect dialect;
    private static Path dataFolder;

    private Database() {
    }

    public static synchronized void open() {
        if (pool != null) return;

        dataFolder = Path.of(ArcraftConfig.DB_FOLDER.get().isBlank() ? "arcraft" : ArcraftConfig.DB_FOLDER.get());
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create ArCraft data folder " + dataFolder.toAbsolutePath(), e);
        }

        String url;
        String user;
        String pass;
        if (!URL_OVERRIDE.isBlank()) {
            dialect = Dialect.H2;
            url = URL_OVERRIDE;
            user = "sa";
            pass = "";
        } else {
            dialect = Dialect.fromConfig(ArcraftConfig.DB_TYPE.get());
            if (dialect == Dialect.H2) {
                migrateLegacyH2File();
                // AUTO_SERVER keeps the file inspectable by external tools while the server runs.
                url = "jdbc:h2:file:" + dataFolder.resolve("arcraft-data").toAbsolutePath() + ";AUTO_SERVER=TRUE";
                user = "sa";
                pass = "";
            } else {
                int port = ArcraftConfig.DB_PORT.get();
                String host = ArcraftConfig.DB_HOST.get();
                String name = ArcraftConfig.DB_NAME.get();
                String scheme = dialect == Dialect.POSTGRESQL ? "postgresql" : "mariadb";
                int effectivePort = port != 0 ? port : (dialect == Dialect.POSTGRESQL ? 5432 : 3306);
                url = "jdbc:" + scheme + "://" + host + ":" + effectivePort + "/" + name;
                user = ArcraftConfig.DB_USER.get();
                pass = ArcraftConfig.DB_PASSWORD.get();
            }
        }

        HikariConfig hc = new HikariConfig();
        hc.setPoolName("ArCraft-Hikari");
        // The driver DataSource is built via direct class reference — see Dialect#createDataSource.
        hc.setDataSource(dialect.createDataSource(url, user, pass));
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(10_000);

        pool = new HikariDataSource(hc);
        jdbi = Jdbi.create(pool);

        try (Connection c = pool.getConnection()) {
            SchemaInit.run(c, dialect);
        } catch (SQLException e) {
            throw new IllegalStateException("ArCraft schema initialisation failed", e);
        }
        LOGGER.info("[ArCraft] Database ready ({}): {}", dialect, url);
    }

    /**
     * Loss-free upgrade from the old two-process layout: the H2 file used to live in the server
     * root as ./arcraft-data.mv.db; it now lives inside the data folder. Move it (plus trace
     * file) once, before the pool opens. All players/stats/clans are preserved.
     */
    private static void migrateLegacyH2File() {
        Path newDb = dataFolder.resolve("arcraft-data.mv.db");
        Path oldDb = Path.of("arcraft-data.mv.db");
        if (Files.exists(newDb) || !Files.exists(oldDb)) return;
        try {
            Files.move(oldDb, newDb, StandardCopyOption.ATOMIC_MOVE);
            Path oldTrace = Path.of("arcraft-data.trace.db");
            if (Files.exists(oldTrace)) {
                Files.move(oldTrace, dataFolder.resolve("arcraft-data.trace.db"), StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("[ArCraft] Migrated existing database into {} — no data lost", dataFolder);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to migrate old arcraft-data.mv.db into " + dataFolder
                    + " — move it manually and restart", e);
        }
    }

    public static synchronized void close() {
        if (pool != null) {
            pool.close();
            pool = null;
            jdbi = null;
            LOGGER.info("[ArCraft] Database pool closed");
        }
    }

    public static Jdbi jdbi() {
        if (jdbi == null) throw new IllegalStateException("Database not opened");
        return jdbi;
    }

    public static javax.sql.DataSource pool() {
        if (pool == null) throw new IllegalStateException("Database not opened");
        return pool;
    }

    public static Dialect dialect() {
        return dialect;
    }

    public static Path dataFolder() {
        return dataFolder;
    }
}
