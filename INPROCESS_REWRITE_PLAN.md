# ArCraft — Single-Jar In-Process Rewrite Plan

> Goal: ArCraft becomes a **fully-fledged, drag-and-drop NeoForge mod**. Drop one jar into
> `mods/`, start the server, and it: starts recording data, **brings up the web dashboard
> in-process**, creates its own config + data folders following Minecraft conventions, and shows
> a logo + description + in-game Config screen in the mods list. No scripts, no second process,
> no manual steps.
>
> Status: **PLAN ONLY — not yet implemented.** Work happens on branch `inprocess-web`; the
> proven subprocess build on `main` stays as the fallback for the final.

---

## 1. Background & why we're changing the architecture

Today ArCraft runs as **two processes**: the NeoForge mod + a separate Spring Boot backend that
share an H2 file via `AUTO_SERVER`. A "drag-and-drop" mode already exists (`WebBackendLauncher`
bundles the Spring Boot fat jar inside the mod and launches it as a **child JVM**). That works,
but it is a pragmatic hack, not the ecosystem standard, and it carries the whole class of
"two processes / two DB files out of sync" bugs (the email-verification bug was one of these).

We investigated what the Minecraft modding community actually does (see §9 Sources):

- **Embedded web servers run in-process.** Dynmap embeds **Jetty**; BlueMap runs its web server
  inside the mod process (binds `0.0.0.0:8100`, serves a `./bluemap/` folder, config toggle,
  renders async off the server thread). **None spawn a child JVM; none use Spring Boot.**
- **The mod-classloader problem has a tiny, documented fix** (Javalin even has a "Javalin and
  Minecraft Servers" guide): switch the thread context classloader during server start.
  ```java
  ClassLoader prev = Thread.currentThread().getContextClassLoader();
  Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
  Javalin app = Javalin.create(...).start(host, port);
  Thread.currentThread().setContextClassLoader(prev);
  ```
  This is the same wall Spring Boot's fat-jar launcher hit — but Javalin/Jetty clears it in 4
  lines, which is exactly why web-capable mods use lightweight servers instead of Spring.
- **Single jar with bundled deps = NeoForge Jar-in-Jar** (the project already uses it for
  H2/jBCrypt). Users still drop one file.
- **"Fully-fledged" polish is built in:** `logoFile=...` in `neoforge.mods.toml` → mod-list icon;
  one line `modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`
  → working in-game Config button (NeoForge 1.21.1 auto-generates the screen from the config spec).

**Decision: rewrite the web layer to run in-process on Javalin (Jetty), drop Spring Boot.**
A major bonus falls out: with one process the mod and website share a single in-JVM datasource,
so `AUTO_SERVER` and the two-file bug class disappear permanently.

---

## 2. Target architecture

```
 ┌─────────────────────────── single NeoForge mod jar ───────────────────────────┐
 │  Minecraft/NeoForge mod (event handlers, commands, tracking)                    │
 │  ─ on ServerStarting:                                                           │
 │       • load ModConfigSpec (config/arcraft-*.toml)                              │
 │       • run one-time migration from the old layout                              │
 │       • open HikariCP datasource (H2 default / external optional)               │
 │       • start in-process Javalin (Jetty) web server on the configured port      │
 │  ─ on ServerStopping: stop Javalin, close the pool                              │
 │                                                                                 │
 │  Web layer (in-process): Javalin routes → services → jdbc/jdbi DAOs → DB        │
 │  Thymeleaf templates render the dashboard. Auth = sessions + BCrypt.            │
 │                                                                                 │
 │  Bundled via Jar-in-Jar: Javalin/Jetty, jdbi, HikariCP, H2 (+ optional          │
 │  Postgres/MySQL drivers), Thymeleaf, Jackson, jBCrypt, Angus Mail.              │
 └─────────────────────────────────────────────────────────────────────────────────┘
```

Single process, single datasource, one jar.

---

## 3. Database & persistence (investigated; chosen for scale)

Scale target stated by the user: 200+ concurrent players, millions of rows over years (write-heavy).

What high-scale plugins do: **CoreProtect** = SQLite by default, "migrate to MySQL/MariaDB past
~50 players / a few million rows." **LuckPerms** = H2 by default, configurable to
MySQL/MariaDB/PostgreSQL. Both use **HikariCP** + portable SQL behind a storage abstraction
(not a heavy ORM).

**Decision — pluggable storage, embedded by default, external for scale:**
- **Default: H2 (embedded, file-based)** — keeps "drop one jar, zero config." H2 over SQLite
  because it is **pure-Java (no per-OS native libs to bundle)**, has better write concurrency
  (MVStore/MVCC), and is already integrated.
- **Optional: PostgreSQL / MySQL / MariaDB** selectable via TOML — the answer to the scale
  concern. Big servers point ArCraft at a real RDBMS; same codebase.
- **Pooling: HikariCP** (the ecosystem standard).
- **Access: jdbi + a thin storage interface**, SQL-first and classloader-friendly. The few
  dialect-divergent statements (mainly upserts: H2/MySQL `MERGE`/`ON DUPLICATE KEY` vs Postgres
  `ON CONFLICT`) are isolated per dialect. **Not Hibernate** (heavy, fragile under modular
  classloaders). jOOQ is the heavier "max type-safety / auto multi-dialect" alternative if ever
  wanted; not chosen now to avoid codegen friction in the mod build.
- **Scale write-path:** keep the existing async single-writer and add **batched flushing** of
  high-frequency counters (blocks/mobs/items) — accumulate in memory, flush in batches — so 200
  players don't issue one DB round-trip per event. (This matters more for sustained load than the
  engine choice.) Add appropriate indexes; consider periodic aggregation for very old data later.

---

## 4. Config (Minecraft convention) + feature gating

- New `Config` class using **`ModConfigSpec`**, registered `ModConfig.Type.COMMON` (global,
  server-side), auto-generating `config/arcraft-common.toml` with inline comments.
- Sections: `[web]` (enabled, host, port, baseUrl), `[database]` (type, embedded folder/path,
  or host/port/name/user/pass for external), `[mail]` (enabled, host, port, username, password,
  from), `[mercadopago]` (accessToken, publicKey, currency), `[gemini]` (apiKey, models).
- **Feature gating = HIDE ENTIRELY** (user's choice): blank token ⇒ that feature is absent from
  the UI. No MercadoPago "Buy coins" panel without a token; no Assistant page/nav without a Gemini
  key; mail/`/email` flow inert without SMTP. (The services already expose `isConfigured()`; the
  web layer reads config directly now that it's in-process — no env round-trip needed.)
- **In-game Config screen:** register `IConfigScreenFactory` → NeoForge's built-in
  `ConfigurationScreen` (Mods list → ArCraft → Config). Client-side extension point (shows when
  the mod is on a client; harmless on a dedicated server).
- Config edits apply on next server start (document this); optional later: hot-reload restarts the
  web server on NeoForge's config-reload event.

---

## 5. Data & file layout (Minecraft convention)

```
<server>/
  config/arcraft-common.toml      ← settings + tokens (user edits this)
  arcraft/                        ← data folder (created automatically)
    arcraft-data.mv.db            ← embedded H2 (when using the default)
    web.log                       ← web server log
  mods/arcraft-x.y.z.jar          ← the single jar
```

- `database.folder` defaults to `arcraft/`. With an external DB, no local DB file is created.
- Logo asset: `src/main/resources/arcraft_logo.png` referenced by `neoforge.mods.toml`.

---

## 6. Loss-free upgrade (from current setup → in-process)

Run once, early, idempotent, before DB open:
1. Create `arcraft/` if missing.
2. **DB:** if `arcraft/arcraft-data.mv.db` is absent but an old `./arcraft-data.mv.db` exists in
   the server root → move it (and `.trace.db`) into `arcraft/`. All players/stats/clans preserved.
3. **Secrets:** if the TOML is fresh (blank tokens) and an old `application.properties` exists in
   the server root → parse it and write mail/MercadoPago/Gemini/baseUrl values into the TOML, then
   save. No retyping of credentials.
4. **Cleanup/notify:** log that the old `arcraft-web/arcraft-backend.jar` and `start.sh`/`stop.sh`
   are no longer needed (do not delete user files).
5. Schema is compatible (same tables); `ddl`/`CREATE TABLE IF NOT EXISTS` plus column adds run as
   today. No data format change ⇒ downgrade to the `main` fallback still reads the same DB.

---

## 7. What carries over vs. what gets rewritten

**Survives largely intact**
- JPA **entities** → reused as plain POJOs/records for jdbi row mapping (annotations dropped).
- **MercadoPago / Gemini / AiContext** services — already plain `java.net.http` + Jackson; port as-is.
- Business logic for rankings/profiles/PvP analytics/store math.
- Most **Thymeleaf templates** (layout, dashboard, rankings, player, pvp-detail, clans, store,
  assistant, admin, email templates).

**Rewritten (off Spring)**
- Spring MVC controllers → **Javalin routes**.
- Spring Data JPA repositories → **jdbi DAOs** (each finder becomes SQL; ~15 repos).
- Spring Security → **session auth + BCrypt** (jBCrypt already bundled) + a **CSRF** filter +
  `/admin` role guard (before-handlers).
- `@Service`/`@Autowired` → **manual wiring** in a bootstrap/service-registry class.
- `@Scheduled` (email tasks) → **`ScheduledExecutorService`**.
- `@Value` → read from the TOML config.
- **Templates:** `sec:authorize` → model flags; **`@{...}` link expressions** → either a custom
  `IWebContext` wrapper for Thymeleaf or convert to plain string paths (Javalin's renderer uses a
  non-web context by default — confirmed). `th:*` standard attributes are unaffected.
- Spring mail → **Angus/Jakarta Mail** directly (HTML MimeMessage, same templates).

---

## 8. Packaging, build & risks

**Packaging**
- Add deps as `implementation` (dev classpath) + `jarJar` (bundled): Javalin (+ Jetty), jdbi,
  HikariCP, Thymeleaf, Jackson, Angus Mail, H2 (already), optional Postgres/MySQL drivers.
- Single jar via **Jar-in-Jar** (user's choice). Watch for version dedup with other mods' libs;
  if conflicts appear, fall back to Shadow + relocation for the web libs.
- Expect a larger jar; document the `-Xmx` note for tiny servers.

**Risks & mitigations**
- *Classloader* → TCCL switch around Javalin start (documented fix); set on the web worker threads
  too if needed.
- *Jetty/Netty or logging (slf4j) conflicts with Minecraft libs* → align slf4j, relocate if needed.
- *Thymeleaf link/security dialects* → handled in §7 (WebContext or plain paths; model flags).
- *Config screen is client-side* → fine; server admins use the TOML.
- *Scale* → HikariCP sizing, batched writes, indexes; external DB option for big servers.
- *Effort/regression* → phased, each phase testable; `main` fallback preserved.

---

## 9. Phased implementation (each independently testable)

- **Phase 1 — Foundation:** build.gradle deps (Javalin/jdbi/HikariCP/Thymeleaf/Jackson/Angus,
  jarJar); `ModConfigSpec` TOML; `neoforge.mods.toml` logo + displayName/description/displayURL/
  credits; register `IConfigScreenFactory`; add logo png.
- **Phase 2 — Storage:** HikariCP datasource from config (H2 default; Postgres/MySQL/MariaDB
  optional); schema init (portable DDL + dialect upserts); jdbi DAOs replacing all repositories;
  entities → POJO mappers; batched async write path.
- **Phase 3 — Web server:** in-process Javalin start/stop tied to Server lifecycle (TCCL fix);
  static assets; Thymeleaf rendering (WebContext/link handling); error pages.
- **Phase 4 — Auth:** session login/logout, BCrypt, `/admin` guard, CSRF.
- **Phase 5 — Port features:** controllers→routes for dashboard, rankings, player, pvp-detail,
  clans, store (+MercadoPago), assistant (+Gemini), admin, map; services wired manually;
  scheduling for emails; Angus mail.
- **Phase 6 — Migration + gating:** one-time upgrade importer (DB move + secrets import); hide
  features when tokens blank.
- **Phase 7 — Packaging + QA:** make the bundled in-process jar the default build; full smoke test
  (clean drop-in with no tokens → features hidden; upgrade from current setup → data + secrets
  preserved); verify mod-list logo + Config screen; load/sanity check.
- **Phase 8 — Docs:** README/DEPLOY/INFO updated for the single-jar workflow; keep the two-process
  path documented as legacy/fallback.

---

## 10. Decisions locked in
- Approach: **in-process Javalin (Jetty)**, drop Spring Boot. Build on `inprocess-web`, keep
  `main` (subprocess) as the fallback for the final.
- DB: **HikariCP + jdbi**, **H2 embedded default**, **Postgres/MySQL/MariaDB optional** for scale.
- Packaging: **Jar-in-Jar** (single file).
- Gating: **hide features entirely** when their token is absent.
- Data folder: **`<server>/arcraft/`**; config: **`config/arcraft-common.toml`**.

## 11. Sources
- Dynmap (Jetty): https://github.com/webbukkit/dynmap
- BlueMap: https://bluemap.bluecolored.de/
- Javalin + Minecraft servers (classloader fix): https://javalin.io/tutorials/javalin-and-minecraft-servers
- Javalin rendering (Thymeleaf, web-context caveat): https://javalin.io/plugins/rendering
- NeoForge Jar-in-Jar: https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/
- NeoForge config + config screen: https://docs.neoforged.net/docs/1.21.1/misc/config/
- CoreProtect (SQLite→MySQL at scale): https://modrinth.com/plugin/coreprotect
- LuckPerms (H2 default → MySQL/MariaDB/PostgreSQL): https://jangro.com/2024/08/02/how-to-set-up-luckperms-on-your-minecraft-1-21-server
