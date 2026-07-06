package org.austral.ing.arcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ArCraft's configuration, following the Minecraft mod convention: NeoForge materialises this
 * spec as {@code config/arcraft-common.toml} (created automatically on first launch, with the
 * comments below inlined). Server admins edit that file; the in-game Mods → ArCraft → Config
 * screen (see {@link ArcraftClient}) edits the same values.
 *
 * <p>Feature gating: MercadoPago / Gemini / mail are OPTIONAL. When their credentials are left
 * blank the matching features are hidden from the web dashboard entirely — the mod works out of
 * the box with zero configuration.</p>
 */
public final class ArcraftConfig {

    public static final ModConfigSpec SPEC;

    // ── [web] ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue WEB_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WEB_HOST;
    public static final ModConfigSpec.IntValue WEB_PORT;
    public static final ModConfigSpec.ConfigValue<String> WEB_BASE_URL;

    // ── [database] ─────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> DB_TYPE;
    public static final ModConfigSpec.ConfigValue<String> DB_FOLDER;
    public static final ModConfigSpec.ConfigValue<String> DB_HOST;
    public static final ModConfigSpec.IntValue DB_PORT;
    public static final ModConfigSpec.ConfigValue<String> DB_NAME;
    public static final ModConfigSpec.ConfigValue<String> DB_USER;
    public static final ModConfigSpec.ConfigValue<String> DB_PASSWORD;

    // ── [mail] ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue MAIL_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> MAIL_HOST;
    public static final ModConfigSpec.IntValue MAIL_PORT;
    public static final ModConfigSpec.ConfigValue<String> MAIL_USERNAME;
    public static final ModConfigSpec.ConfigValue<String> MAIL_PASSWORD;
    public static final ModConfigSpec.ConfigValue<String> MAIL_FROM;

    // ── [mercadopago] ──────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> MP_ACCESS_TOKEN;
    public static final ModConfigSpec.ConfigValue<String> MP_PUBLIC_KEY;
    public static final ModConfigSpec.ConfigValue<String> MP_CURRENCY;

    // ── [gemini] ───────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> GEMINI_MODELS;

    // ── [general] ──────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue SEED_DEMO_DATA;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(" ArCraft — drop-in server stats + web dashboard.",
                  " The dashboard starts automatically with the server. Optional integrations",
                  " (mail, MercadoPago, Gemini AI) activate when you fill in their credentials;",
                  " left blank, those features are hidden from the website.");

        b.comment(" Embedded web dashboard.").push("web");
        WEB_ENABLED = b.comment(" Serve the web dashboard from inside the Minecraft server process.")
                .define("enabled", true);
        WEB_HOST = b.comment(" Bind address. 0.0.0.0 = reachable from other machines (port-forward to publish).")
                .define("host", "0.0.0.0");
        WEB_PORT = b.comment(" HTTP port for the dashboard.")
                .defineInRange("port", 8080, 1, 65535);
        WEB_BASE_URL = b.comment(" Public base URL used in emails and payment redirects,",
                        " e.g. \"http://my-server.example.com:8080\". Blank = http://localhost:<port>.")
                .define("baseUrl", "");
        b.pop();

        b.comment(" Where stats are stored. The default embedded database needs no setup.",
                  " For very large servers point ArCraft at PostgreSQL / MySQL / MariaDB instead.")
                .push("database");
        DB_TYPE = b.comment(" One of: h2 (embedded, default), postgresql, mysql, mariadb.")
                .define("type", "h2");
        DB_FOLDER = b.comment(" Data folder (relative to the server dir) for the embedded database and logs.")
                .define("folder", "arcraft");
        DB_HOST = b.comment(" External database host (ignored for h2).").define("host", "localhost");
        DB_PORT = b.comment(" External database port (ignored for h2). 0 = driver default.")
                .defineInRange("port", 0, 0, 65535);
        DB_NAME = b.comment(" External database name (ignored for h2).").define("name", "arcraft");
        DB_USER = b.comment(" External database user (ignored for h2).").define("user", "arcraft");
        DB_PASSWORD = b.comment(" External database password (ignored for h2).").define("password", "");
        b.pop();

        b.comment(" Outgoing mail (email verification + event reminders). Optional.",
                  " For Gmail use smtp.gmail.com:587 with an app password.").push("mail");
        MAIL_ENABLED = b.define("enabled", false);
        MAIL_HOST = b.define("host", "");
        MAIL_PORT = b.defineInRange("port", 587, 1, 65535);
        MAIL_USERNAME = b.define("username", "");
        MAIL_PASSWORD = b.define("password", "");
        MAIL_FROM = b.comment(" From address; blank = username.").define("from", "");
        b.pop();

        b.comment(" MercadoPago Checkout Pro for buying coins in the store. Optional —",
                  " blank token hides the \"Buy coins\" section on the website.").push("mercadopago");
        MP_ACCESS_TOKEN = b.define("accessToken", "");
        MP_PUBLIC_KEY = b.define("publicKey", "");
        MP_CURRENCY = b.define("currency", "ARS");
        b.pop();

        b.comment(" Gemini AI assistant that answers questions about your server's stats.",
                  " Optional — blank key hides the Assistant page. Models = fallback chain.").push("gemini");
        GEMINI_API_KEY = b.define("apiKey", "");
        GEMINI_MODELS = b.define("models", "gemini-2.5-flash,gemini-2.5-flash-lite,gemini-flash-latest");
        b.pop();

        b.push("general");
        SEED_DEMO_DATA = b.comment(" Seed a small demo world (fake players/clans/fights) when the database",
                        " is empty. Useful for trying the dashboard; leave off for real servers.")
                .define("seedDemoData", false);
        b.pop();

        SPEC = b.build();
    }

    private ArcraftConfig() {
    }

    /** Public base URL for links in mails / payment redirects; falls back to localhost. */
    public static String baseUrl() {
        String v = WEB_BASE_URL.get().trim();
        return v.isEmpty() ? "http://localhost:" + WEB_PORT.get() : v.replaceAll("/+$", "");
    }

    public static boolean mailConfigured() {
        return MAIL_ENABLED.get() && !MAIL_HOST.get().isBlank() && !MAIL_USERNAME.get().isBlank();
    }

    public static boolean mercadoPagoConfigured() {
        return !MP_ACCESS_TOKEN.get().isBlank();
    }

    public static boolean geminiConfigured() {
        return !GEMINI_API_KEY.get().isBlank();
    }
}
