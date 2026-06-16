package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Makes ArCraft a true "drag-and-drop" mod: drop the single jar into {@code mods/}, start the
 * server, and the web dashboard comes up on port 8080 — no extra script to run.
 *
 * <p>NeoForge's modular classloader is incompatible with Spring Boot's nested-jar launcher, so
 * we cannot run the backend in-process. Instead the Spring Boot fat jar is bundled as a resource
 * ({@code /arcraft-backend.jar}) inside this mod jar; on server start we extract it and launch it
 * as a child JVM, pointing it at the same shared H2 file the mod uses. The child is shut down when
 * the Minecraft server stops.</p>
 *
 * <p>If the backend jar was not bundled (e.g. a slim build), this launcher logs a notice and does
 * nothing, so the mod still works with the legacy two-process {@code arcraft-server.sh} setup.</p>
 */
public final class WebBackendLauncher {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMBEDDED_JAR = "/arcraft-backend.jar";

    private static Process process;
    private static Thread shutdownHook;

    private WebBackendLauncher() {}

    public static synchronized void start(String h2JdbcUrl) {
        if (process != null && process.isAlive()) return;

        try (InputStream in = WebBackendLauncher.class.getResourceAsStream(EMBEDDED_JAR)) {
            if (in == null) {
                LOGGER.info("[ArCraft] No embedded backend jar found ({}). Skipping web auto-launch; " +
                        "use arcraft-server.sh if you want the dashboard.", EMBEDDED_JAR);
                return;
            }

            File webDir = new File("arcraft-web");
            if (!webDir.exists() && !webDir.mkdirs()) {
                LOGGER.warn("[ArCraft] Could not create {} — web dashboard not started", webDir.getAbsolutePath());
                return;
            }
            Path jarPath = webDir.toPath().resolve("arcraft-backend.jar");
            Files.copy(in, jarPath, StandardCopyOption.REPLACE_EXISTING);

            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-Dspring.datasource.url=" + h2JdbcUrl,
                    "-Dserver.port=" + System.getProperty("arcraft.web.port", "8080"),
                    "-jar", jarPath.toAbsolutePath().toString()
            );
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(webDir.toPath().resolve("arcraft-web.log").toFile()));

            process = pb.start();
            LOGGER.info("[ArCraft] Web dashboard launched (pid {}). Logs: {}",
                    process.pid(), webDir.toPath().resolve("arcraft-web.log").toAbsolutePath());

            // Ensure the child dies even if the JVM is killed without a clean ServerStopping event.
            shutdownHook = new Thread(WebBackendLauncher::stop, "ArCraft-Web-Shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] Failed to launch web dashboard", e);
        }
    }

    public static synchronized void stop() {
        if (process != null) {
            try {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
                LOGGER.info("[ArCraft] Web dashboard stopped");
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            } finally {
                process = null;
            }
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM already shutting down
            }
            shutdownHook = null;
        }
    }
}
