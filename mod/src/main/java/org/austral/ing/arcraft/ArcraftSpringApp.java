package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ArcraftSpringApp {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ConfigurableApplicationContext context;

    public static void start() {
        if (context != null) return;
        LOGGER.info("[ArCraft] Starting Spring Boot context");
        SpringApplication app = new SpringApplication(ArcraftSpringApp.class);
        app.setLogStartupInfo(false);
        context = app.run();
        LOGGER.info("[ArCraft] Spring Boot context started");
    }

    public static void stop() {
        if (context == null) return;
        LOGGER.info("[ArCraft] Stopping Spring Boot context");
        try {
            context.close();
        } catch (Exception e) {
            LOGGER.error("[ArCraft] Error closing Spring context", e);
        } finally {
            context = null;
        }
    }
}
