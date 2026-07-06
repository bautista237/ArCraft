# ArCraft — Architecture & Developer Guide (single-jar edition)

> Deep documentation for anyone (including future us) who needs to modify or extend ArCraft.
> Covers the architecture, every package, the conventions the code follows, and step-by-step
> recipes for the most common changes. The legacy two-process design (Spring Boot backend) is
> documented at the end for context; it lives on `main` as a fallback.

---

## 1. What ArCraft is

A **NeoForge 1.21.1 server mod** that records everything that happens on a Minecraft server
(joins, PvP fights with per-hit detail, blocks, mobs, items, arrows, chunk exploration, clans)
and serves a **full web dashboard from inside the server process**: rankings, player profiles,
PvP combat breakdowns, clan pages with chat bridged to in-game, a live map, a coin store with
MercadoPago payments, a Gemini-powered AI assistant, HTML email notifications, and an admin
panel.

**One jar.** Drop `arcraft-x.y.z-all.jar` into `mods/`, start the server, open
`http://<server>:8080`. No scripts, no second process, no database install.

```
                       ┌────────────────────── Minecraft server JVM ──────────────────────┐
 players ── play ────▶ │ NeoForge + ArCraft mod                                            │
                       │   ArcraftEventHandler ─┐ (writes, async single-writer thread)     │
                       │   Arcraft/EmailCommands┤                                          │
                       │   ClanChat bridge ─────┘                                          │
                       │            ▼                                                      │
                       │   db.Database — ONE HikariCP pool ── H2 file in ./arcraft/        │
                       │            ▲              (or PostgreSQL / MariaDB via config)    │
 browsers ── http ───▶ │   web.WebServer (Javalin/Jetty, embedded, TCCL fix)               │
                       │     routes ─▶ services ─▶ jdbi SQL ─▶ pool                        │
                       │     Thymeleaf templates ─▶ HTML                                   │
                       │   web.service.MailService (SMTP, ScheduledExecutorService)        │
                       └───────────────────────────────────────────────────────────────────┘
```

Same model as Dynmap/BlueMap (embedded Jetty in-process). **One process ⇒ one datasource ⇒
the old "two processes opened two different H2 files" class of bugs is impossible.**

## 2. Module layout

```
ArCraft/
  mod/        ← THE product: NeoForge mod + embedded web (everything below is here)
  backend/    ← legacy Spring Boot backend (fallback, kept on main; not built into the jar)
  DEPLOY.md   ← free-tier deploy decision + how-to (Oracle Cloud)
  task.md     ← objectives index
  INPROCESS_REWRITE_PLAN.md ← the plan this rewrite followed (history/rationale)
```

### 2.1 `mod/src/main/java/org/austral/ing/arcraft/` — package map

| Package / class | Responsibility |
|---|---|
| `ArcraftMod` | Mod entry; registers config; on ServerStarting: LegacyImport → DatabaseManager.init (opens Database + seeds) → WebServer.start. Reverse on stop. |
| `ArcraftClient` | Client-only (`@Mod(dist=CLIENT)`): registers the auto-generated in-game Config screen. |
| `ArcraftConfig` | `ModConfigSpec` → `config/arcraft-common.toml`. Sections `[web] [database] [mail] [mercadopago] [gemini] [general]`. Helpers: `baseUrl()`, `mailConfigured()`, `mercadoPagoConfigured()`, `geminiConfigured()`. |
| `LegacyImport` | One-time upgrade: old `application.properties` (server root) → TOML. Only fills blank values; calls `SPEC.save()` (⚠ `ConfigValue.set()` alone does NOT persist). |
| `DatabaseManager` | Static façade for the mod's write path (unchanged API: `init/close/getConnection/submit`). Holds ONE pooled connection driven by the `ArCraft-DB-Writer` single-writer executor. |
| `ArcraftEventHandler` (1139 loc) | All gameplay tracking; raw JDBC through `DatabaseManager.submit(...)`. Portable SQL (`INSERT … WHERE NOT EXISTS`, `UPDATE x = x + ?`). |
| `ArcraftCommands`, `EmailCommands`, `ClanChat` | In-game commands (`/email set|verify`, clan chat bridge web↔game). |
| `db.Dialect` | `H2 | POSTGRESQL | MARIADB`; DDL tokens `${UUID} ${TIMESTAMP} ${DOUBLE}`; builds driver DataSources by **direct class reference** (never DriverManager — it's classloader-blind under NeoForge). |
| `db.Database` | Opens the HikariCP pool + jdbi from config; creates `arcraft/` data folder; **moves a legacy root `arcraft-data.mv.db` into it** (loss-free upgrade); runs SchemaInit. |
| `db.SchemaInit` | Whole schema, idempotent, portable: 18 `CREATE TABLE IF NOT EXISTS` + `ALTER … ADD COLUMN IF NOT EXISTS` upgrades + indexes. Includes the 6 tables JPA used to own (event, achievement, player_achievement, store_item, player_purchase, coin_order). |
| `db.Seeder` | First boot: `admin/admin` + store catalog + welcome banner + server_config when empty; full demo world when `general.seedDemoData=true` **and** ≤1 player. |
| `web.WebServer` | Javalin lifecycle. **TCCL swap around start** (Jetty service discovery can't see Jar-in-Jar libs through NeoForge's loader otherwise). Web failure never kills the game. Sessions: HttpOnly + SameSite=Lax. |
| `web.WebRoutes` | Route table: registers Auth + all feature route classes. |
| `web.Auth` | `before` gate: everything needs login except `/login /css /js /images /health /favicon /store/coins/webhook`; `/admin/**` needs the admin flag; mutating requests must pass an **Origin/Referer same-host check** (CSRF, with SameSite=Lax). Login = BCrypt vs `player.password_hash` (written by the mod in-game). |
| `web.SessionUser` | `record(id, username, admin)` in the Jetty session. `login()` invalidates + recreates the session (fixation). |
| `web.Renderer` | Thymeleaf. Pages: real servlet `WebContext` via `JakartaServletWebApplication` ⇒ `@{...}`, `${param.x}` work unchanged. Emails: `renderToString` with a plain Context. Templates live in `resources/web/templates/`, static files in `resources/web/static/` (served at `/`). |
| `web.BaseModel` | Per-request model injected into every render: `navUsername navCoins navClanTag isAdmin`, feature flags `mpEnabled geminiEnabled mailEnabled`, helper beans `skinService iconService`, popped flash `error/success`. |
| `web.Flash` | One-shot messages across redirects (ex Spring RedirectAttributes). |
| `web.dao.Daos` | Shared jdbi queries + column mappers (`playerCols/mapPlayer`, `clanCols/mapClan`, `statsCols/mapStats`, `recentEvents`, `auth`). All UUID/timestamp reads go through `Daos.uuid()/instant()` for cross-DB portability. |
| `web.model.*` | Read-models with **JPA-entity-compatible getters** so templates carried over unchanged: `PlayerView ClanView StatsView EventLogView EventView StoreItemView PurchaseView PvPEventView Stat.{Block,Item,Mob}`. Enums preserved where templates call `.name()` (StoreItemView.Category, EventLogView.EventType). |
| `web.service.*` | Feature logic, singletons (`X.INSTANCE`): Skin, Icon, Store, Title, Rankings, Dashboard, Events, PlayerProfile, Clan, Map, AiContext, Gemini, MercadoPago, Mail, CoinPackage, Admin. |
| `web.routes.*` | HTTP endpoints per feature: `PageRoutes` (/, dashboard, rankings, players/{u}, info), `ClanRoutes`, `PvPRoutes`, `StoreRoutes` (+MercadoPago checkout/return/webhook), `MiscRoutes` (assistant, map), `AdminRoutes`. |

### 2.2 Threading model

- **Server thread**: game events fire here; handlers *enqueue* DB work.
- **ArCraft-DB-Writer** (1 thread): executes all mod-side writes sequentially on its reserved
  pooled connection — no lock contention with gameplay.
- **JettyServerThreadPool (virtual threads)**: web requests; each borrows/returns pool
  connections via jdbi per query/transaction.
- **ArCraft-Mail** (1 scheduled thread): verification sweep every 30 s, event reminders every
  10 min. Only started when `[mail]` is configured.

## 3. Database

- **Default: embedded H2** at `arcraft/arcraft-data.mv.db` (`AUTO_SERVER=TRUE` so external
  tools can inspect it while the server runs). Zero config, fits the drop-in promise.
- **Scale-out: `database.type = "postgresql"` or `"mariadb"`** (MariaDB 10.7+, its driver also
  serves MySQL-compatible setups; Connector/J is GPL so we bundle the LGPL MariaDB driver).
  This mirrors LuckPerms/CoreProtect: embedded by default, real RDBMS for 200+ player servers.
- Pooling: HikariCP (`ArCraft-Hikari`, max 10). Access: jdbi fluent API, SQL-first, no ORM.
- Portability rules (follow these when adding SQL):
  - UUID/Instant columns: read via `Daos.uuid()/Daos.instant()`; bind UUID/Instant objects directly.
  - `chunk_visit.player_id` is `VARCHAR(36)` (historical): bind `uuid.toString()`, join via
    `CAST(p.id AS VARCHAR(36))`.
  - DDL goes in `SchemaInit` using the `${UUID}/${TIMESTAMP}/${DOUBLE}` tokens; new columns are
    `ALTER TABLE … ADD COLUMN IF NOT EXISTS` entries in `UPGRADES` (works on all 3 dialects).
  - Prefer plain SQL that works everywhere; if a statement must diverge per dialect, branch on
    `Database.dialect()`.

## 4. Configuration & gating

`config/arcraft-common.toml` (auto-created, commented) — also editable in-game via
Mods → ArCraft → Config (NeoForge auto-generates the screen from the spec).

**Gating convention:** optional integrations are **invisible until configured** — blank Gemini
key ⇒ no Assistant nav entry; blank MercadoPago token ⇒ no "Buy coins" panel; `[mail]` off ⇒
mail scheduler never starts. Gating flags are computed in ONE place (`ArcraftConfig.xConfigured()`
→ `BaseModel`) — reuse them, don't re-derive.

Config values are read live (`ArcraftConfig.X.get()`); edits apply on next server start.
⚠ If you ever *write* config values from code, call `ArcraftConfig.SPEC.save()` afterwards.

## 5. Security model

- All pages require login except the public list in `Auth`. `/admin/**` additionally requires
  `player.is_admin`. The webhook is public by design (server-to-server; idempotent crediting).
- Credentials: BCrypt hashes created by the mod when a player first joins (in-game password
  flow) or by the admin panel / seeder.
- CSRF: session cookie is `SameSite=Lax` + `HttpOnly`, and every non-GET request must present
  an Origin/Referer whose host matches the request Host (webhook exempt).
- Session fixation: new session id on login. Redirect-based flash avoids URL-leaked state.
- The H2 web console does not exist in this stack (nothing to disable when exposing :8080).
- OAuth2 "Login with Minecraft" from the Spring build is **not ported** (was disabled in
  production anyway); `minecraftLoginEnabled=false` hides its button. Port path if ever needed:
  implement the MS auth-code flow + the old `MinecraftAuthService` chain (it was Spring-free).

## 6. Templates (Thymeleaf without Spring) — READ THIS before editing HTML

The expression language is **OGNL, not SpringEL**. Differences that already bit us:
- Static calls: `T(java.lang.String).format(...)` ❌ → `@java.lang.String@format(...)` ✅
- No `@beanName` references: helpers are plain model entries (`skinService`, `iconService`).
- No `sec:authorize`: use the model flags (`isAdmin`, `navUsername`).
- `${param.x}`, `@{/links(...)}`, `#numbers`, `#temporals` all work (real WebContext).
- Java **records work** in expressions (`${t.text}` on `record Title(String text, …)`) — OGNL
  3.3.4 resolves record components (verified by test).
- Model objects must expose **getters** (or be records); templates were written against JPA
  entities, so `web.model.*` mirrors those getters exactly.

## 7. How-to recipes

**Add a page**: template in `resources/web/templates/foo.html` (use the navbar fragment) →
queries in a `web.service` singleton (SQL via `Database.jdbi()`, mappers from `Daos`) → route
in the matching `web.routes` class (`Renderer.render(ctx, "foo", model)`) → register in
`WebRoutes` if it's a new class. Auth is automatic (opt OUT via `Auth.PUBLIC_PREFIXES`).

**Add a config option**: field + builder entry in `ArcraftConfig` → read with `.get()`. It
appears in the TOML and the in-game screen automatically.

**Add a DB column**: `ALTER TABLE … ADD COLUMN IF NOT EXISTS …` line in `SchemaInit.UPGRADES`
(+ column in the matching `web.model` view + mapper in `Daos`). Never edit existing CREATEs
for upgrades — old servers won't re-run them.

**Add a bundled library**: add the GAV to `webStack` in `mod/build.gradle` — **including its
transitive deps** (Jar-in-Jar does not resolve transitives; verify with
`./gradlew dependencies --configuration runtimeClasspath`, then check
`unzip -l build/libs/*-all.jar | grep jarjar`). Never bundle `org.slf4j:slf4j-api` (Minecraft
provides it; a second copy breaks module resolution).

**Track a new game stat**: handler in `ArcraftEventHandler` (write via `DatabaseManager.submit`,
portable SQL) → surface it through a service/DAO → template.

## 8. Build, test, release

```bash
cd mod
./gradlew build            # → build/libs/arcraft-<version>-all.jar  (the release artifact)
```
- The `-all` jar (~16 MB) contains the mod + 36 nested libraries under `META-INF/jarjar/`.
- Version/metadata: `mod/gradle.properties`; mod-list logo/description: `neoforge.mods.toml`
  + `arcraft_logo.png`.
- Smoke test: drop the jar in a 1.21.1 NeoForge server, start, then
  `curl localhost:8080/health` → `ok`; log shows `[ArCraft] Web dashboard up`.
- Upgrade test expectations (from the two-process layout): log lines
  `Imported N credential group(s)` + `Migrated existing database into arcraft` and the old
  root `arcraft-data.mv.db` is gone (moved), players intact.
- In THIS dev sandbox: gradle/pkill need `dangerouslyDisableSandbox`; background runs need
  `run_in_background` (see memory notes / build-run-gotchas).

## 9. Known limitations / deliberate choices

- Config edits need a server restart (no hot reload of the web server).
- The in-game Config screen exists on clients; dedicated-server admins edit the TOML.
- OAuth2 Minecraft login not ported (§5). Xbox/manual flow documented in old backend code.
- `player_password_plain` column is legacy (kept for schema compat; not written by new code).
- Sessions are in-memory: restart logs everyone out (fine for this scale).
- The mod's own write path stays H2-flavored-but-portable raw JDBC; if a statement ever fails
  on Postgres/MariaDB it belongs in the dialect-branching pattern of §3.

## 10. Legacy: the two-process design (main branch fallback)

Until this rewrite ArCraft ran as the mod + a separate Spring Boot 3.3 backend sharing the H2
file via `AUTO_SERVER=TRUE`, launched either by `start.sh` or extracted-and-spawned from the
mod jar (`WebBackendLauncher`, `-PbundleWeb`). Spring MVC controllers/JPA repositories map
1:1 onto today's `web.routes`/`web.service`+`Daos` classes — the old code in `backend/` is
the reference if behavior questions come up. The email-verification bug that motivated "one
process, one datasource" was exactly a two-files-out-of-sync failure.
