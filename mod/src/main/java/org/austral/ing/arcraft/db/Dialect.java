package org.austral.ing.arcraft.db;

import javax.sql.DataSource;

/**
 * The SQL dialects ArCraft can store data in. H2 (embedded, zero-config) is the default —
 * the same "works out of the box, switch to a real RDBMS for scale" model used by LuckPerms
 * and CoreProtect. PostgreSQL is the recommended external database; MariaDB (10.7+, for its
 * native UUID type) also covers MySQL-compatible setups via the LGPL MariaDB driver.
 *
 * <p>The schema DDL is shared across dialects via the tokens {@code ${UUID}}, {@code ${TIMESTAMP}}
 * and {@code ${DOUBLE}} (see {@link SchemaInit}); everything else in our SQL is portable.</p>
 */
public enum Dialect {
    H2, POSTGRESQL, MARIADB;

    public static Dialect fromConfig(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase()) {
            case "", "h2", "embedded" -> H2;
            case "postgres", "postgresql" -> POSTGRESQL;
            case "mariadb", "mysql" -> MARIADB;
            default -> throw new IllegalArgumentException(
                    "Unknown database.type '" + type + "' — use h2, postgresql or mariadb");
        };
    }

    /** Expands the portable DDL tokens for this dialect. */
    public String ddl(String template) {
        return template
                .replace("${UUID}", "UUID")                                  // native on all three
                .replace("${TIMESTAMP}", this == MARIADB ? "DATETIME(3)" : "TIMESTAMP")
                .replace("${DOUBLE}", "DOUBLE PRECISION");
    }

    /**
     * Builds the driver's DataSource with a DIRECT class reference (no DriverManager).
     * DriverManager only sees drivers visible to the caller's classloader, which under
     * NeoForge's modular classloading meant "No suitable driver found"; constructing the
     * DataSource directly sidesteps that entirely (the Jar-in-Jar'd driver modules are
     * readable from mod code).
     */
    public DataSource createDataSource(String url, String user, String password) {
        try {
            switch (this) {
                case H2 -> {
                    org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
                    ds.setURL(url);
                    ds.setUser(user);
                    ds.setPassword(password);
                    return ds;
                }
                case POSTGRESQL -> {
                    org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
                    ds.setUrl(url);
                    ds.setUser(user);
                    ds.setPassword(password);
                    return ds;
                }
                case MARIADB -> {
                    org.mariadb.jdbc.MariaDbDataSource ds = new org.mariadb.jdbc.MariaDbDataSource(url);
                    ds.setUser(user);
                    ds.setPassword(password);
                    return ds;
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Failed to create " + this + " DataSource for " + url, e);
        }
        throw new IllegalStateException("Unhandled dialect " + this);
    }
}
