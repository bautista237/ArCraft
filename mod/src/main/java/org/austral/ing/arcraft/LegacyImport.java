package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loss-free upgrade from the old two-process ArCraft: if the server root still has the old
 * external {@code application.properties} (mail/MercadoPago/Gemini credentials for the Spring
 * backend) and the TOML config is still blank, import those values into
 * {@code config/arcraft-common.toml} once — so upgrading is literally "replace the jar",
 * no retyping of credentials. (The old H2 file is migrated separately by Database.)
 */
final class LegacyImport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path OLD_PROPS = Path.of("application.properties");

    private LegacyImport() {
    }

    static void run() {
        if (!Files.exists(OLD_PROPS)) return;
        // Only import into a fresh config — never overwrite values the admin already set.
        boolean fresh = ArcraftConfig.MAIL_USERNAME.get().isBlank()
                && ArcraftConfig.MP_ACCESS_TOKEN.get().isBlank()
                && ArcraftConfig.GEMINI_API_KEY.get().isBlank();
        if (!fresh) return;

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(OLD_PROPS)) {
            p.load(in);
        } catch (IOException e) {
            LOGGER.warn("[ArCraft] Could not read legacy application.properties: {}", e.toString());
            return;
        }

        int imported = 0;
        // Mail
        String mailHost = p.getProperty("spring.mail.host", "");
        String mailUser = p.getProperty("spring.mail.username", "");
        if (!mailHost.isBlank() && !mailUser.isBlank()) {
            ArcraftConfig.MAIL_HOST.set(mailHost);
            ArcraftConfig.MAIL_USERNAME.set(mailUser);
            ArcraftConfig.MAIL_PASSWORD.set(p.getProperty("spring.mail.password", ""));
            try {
                ArcraftConfig.MAIL_PORT.set(Integer.parseInt(p.getProperty("spring.mail.port", "587").trim()));
            } catch (NumberFormatException ignored) {
            }
            ArcraftConfig.MAIL_ENABLED.set("true".equalsIgnoreCase(p.getProperty("arcraft.mail.enabled", "true")));
            imported++;
        }
        // Base URL
        String baseUrl = p.getProperty("arcraft.app.base-url", "");
        if (!baseUrl.isBlank() && !baseUrl.contains("localhost")) {
            ArcraftConfig.WEB_BASE_URL.set(baseUrl);
            imported++;
        }
        // MercadoPago
        String mpToken = p.getProperty("arcraft.mercadopago.access-token", "");
        if (!mpToken.isBlank()) {
            ArcraftConfig.MP_ACCESS_TOKEN.set(mpToken);
            ArcraftConfig.MP_PUBLIC_KEY.set(p.getProperty("arcraft.mercadopago.public-key", ""));
            ArcraftConfig.MP_CURRENCY.set(p.getProperty("arcraft.mercadopago.currency", "ARS"));
            imported++;
        }
        // Gemini
        String geminiKey = p.getProperty("arcraft.gemini.api-key", "");
        if (!geminiKey.isBlank()) {
            ArcraftConfig.GEMINI_API_KEY.set(geminiKey);
            String models = p.getProperty("arcraft.gemini.model", "");
            if (!models.isBlank()) ArcraftConfig.GEMINI_MODELS.set(models);
            imported++;
        }

        if (imported > 0) {
            // ConfigValue.set() only updates the in-memory config — persist explicitly so the
            // imported credentials survive after the old application.properties is deleted.
            try {
                ArcraftConfig.SPEC.save();
            } catch (Exception e) {
                LOGGER.warn("[ArCraft] Could not persist imported config to disk: {}", e.toString());
            }
            LOGGER.info("[ArCraft] Imported {} credential group(s) from the old application.properties "
                    + "into config/arcraft-common.toml. The old backend files (arcraft-web/, start.sh, "
                    + "stop.sh, application.properties) are no longer used and can be deleted.", imported);
        }
    }
}
