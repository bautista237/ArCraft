# ArCraft — Objetivos actuales del proyecto

> Índice maestro. Hay **2 objetivos pendientes**. Cada uno tiene su archivo `.md` con TODAS las
> decisiones finales de cómo hacerlo. Este archivo los nombra y vuelca el conocimiento transversal
> de la sesión para que nada se pierda. **Nada de esto está implementado todavía** — es plan.
>
> Estado del código: rama `main` = build de **dos procesos** que **funciona y está verificado**
> (es el fallback del final). Rama `inprocess-web` = donde va la reescritura (objetivo 2).

---

## Los 2 objetivos

### 🎯 Objetivo 1 — Deploy en free tier → **`DEPLOY.md`**
Poner ArCraft online, 24/7, accesible mundialmente, gratis.
- **Decisión de servicio: Oracle Cloud Always Free (VM ARM Ampere A1, 2 OCPU / 12 GB).**
  Es el único free tier que corre **los dos procesos juntos compartiendo la misma H2**, con IP
  pública y cualquier puerto (25565 + 8080). Render/Railway/Fly no sirven (duermen / sin free tier /
  no exponen el puerto MC ni comparten DB).
- **Decisión de orden: se hace ANTES que el objetivo 2**, con el build actual de dos procesos
  (funciona, bajo riesgo). El single-jar se redeploya a la misma VM después.
- Todo el detalle (comparación, por qué, pasos concretos, fallback Render solo-demo, fuentes) → `DEPLOY.md`.

### 🎯 Objetivo 2 — Mod single-jar drag-and-drop → **`INPROCESS_REWRITE_PLAN.md`**
Un `.jar` que al tirarlo en `mods/` levanta la web **in-process**, graba datos, crea sus carpetas
config/data estilo Minecraft, y aparece en la mod-list con logo + descripción + pantalla de Config.
- **Decisión de arquitectura: web in-process con Javalin (Jetty), se elimina Spring Boot.**
  Un solo proceso ⇒ un solo datasource in-JVM ⇒ desaparece para siempre la clase de bug de
  "dos procesos / dos archivos H2 desincronizados" (la del verify).
- **DB para escala (200+ jugadores, millones de filas): HikariCP + jdbi, H2 embebida por defecto,
  Postgres/MySQL/MariaDB opcional** por config. Escritura async con flush en batches.
- **Packaging: Jar-in-Jar** (un solo archivo). **Gating: ocultar features** cuando falta el token.
- **Config: `config/arcraft-common.toml`** (TOML estilo mod). **Data: `<server>/arcraft/`.**
- **Migración sin pérdida** desde el setup actual (mueve la H2 vieja, importa secrets del
  `application.properties` viejo). 8 fases, cada una testeable. Todo el detalle → `INPROCESS_REWRITE_PLAN.md`.
- **Decisión de orden: DESPUÉS del deploy.** Es un refactor grande; no se bloquea el final tras él.

---

## Conocimiento transversal de la sesión (para no perderlo)

### Arquitectura actual (lo que hay que entender antes de cualquier cosa)
Son **DOS procesos que comparten un archivo H2** vía `AUTO_SERVER`, corriendo en la misma carpeta:
1. **Servidor Minecraft** = NeoForge 1.21.1 + el mod (`ArCraft/mod`). Graba stats en `./arcraft-data`.
2. **Backend web** = Spring Boot 3.3 / Java 21 (`ArCraft/backend`). Lee/escribe el mismo `./arcraft-data`.

Esta dualidad es la que define el objetivo 1 (necesitás una VM que corra ambos) y la que el
objetivo 2 elimina (todo in-process en el mod).

### Estado de las features del final (todas hechas y verificadas en `main`)
- **Verify bug (arreglado, confirmado in-game):** root cause = backend y mod abrían H2 **distintas**
  (el backend traía un path hardcodeado de Mac), así que el código emailado (DB del backend) nunca
  matcheaba el guardado (DB del mod). Fix: datasource del backend ahora
  `${ARCRAFT_DB_URL:jdbc:h2:file:./arcraft-data;AUTO_SERVER=TRUE}` (mismo archivo relativo que el mod).
  `EmailCommands.verify` normaliza a solo-dígitos + loguea.
- **MercadoPago Checkout Pro (test):** `MercadoPagoService`, `CoinOrder`, `/store/coins/{checkout,return,webhook}`,
  paquetes en `CoinPackage`. `auto_return` solo si base-url NO es localhost (MP rechaza localhost).
  Verificado creando preferencia real en la cuenta test del usuario.
- **Gemini AI chat sobre la DB:** `GeminiService` + `AiContextService` (snapshot de texto de la DB) +
  `/assistant`. Cadena de fallback de modelos (`gemini-2.5-flash,gemini-2.5-flash-lite,gemini-flash-latest`)
  con retry en 429/503. La key del usuario NO tiene cuota para `gemini-2.0-flash`.
- **Mails HTML** (Thymeleaf `templates/email/*`) para verificación + recordatorios, con links a la app.
- **Más data PvP:** `PlayerProfileService.getPvpAnalytics` (némesis / víctima favorita / head-to-head /
  win rate) en el perfil; Combat Breakdown (daño/DPS por peleador, uso de armas) en `/pvp/{id}`.
- **DataSeeder** siembra un mundo demo (6 jugadores, 2 clanes, peleas+hits, breakdowns, event log,
  ServerConfig), gateado por `arcraft.seed.demo-data` (default true).
- **Guía de presentación:** `PRESENTACION_FINAL.md`.

### 🔐 Secrets — NUNCA al repo (constraint que persiste)
Viven fuera de git, en:
- `ArCraft/backend/application-secret.properties` (gitignored) — para desarrollo local.
- `/home/overseer/arcraft-test-server/application.properties` (fuera del repo) — para el server de prueba.

`.gitignore` ya excluye: `*-secret.properties`, `*.mv.db`, `*.trace.db`, `mc-server/`, etc.
**Siempre verificar que no haya secrets ni `*.mv.db` en el stage antes de commitear/pushear.**
Valores actuales (en esos archivos, no acá): token MercadoPago, key Gemini, Gmail app password.
En el objetivo 2 estos secrets pasan al `config/arcraft-common.toml` (y la migración los importa solos).

### Server de prueba local
`/home/overseer/arcraft-test-server/` — backend en `arcraft-web/arcraft-backend.jar`, mod en
`mods/arcraft-1.0.0-all.jar`, `arcraft-data.mv.db` compartida, config con mail+MP+Gemini.
Arrancar con `SRV=/ruta ./start.sh` (levanta backend, espera "Started ArcraftApplication", luego
`./run.sh nogui`). `stop.sh` mata ambos. Admin seed = `admin`/`admin`.

### Gotchas de build/run en este entorno (sandbox)
- `mvn package`/`clean` y `gradlew` mueren con **exit 144** salvo `dangerouslyDisableSandbox: true`.
  `mvn compile` suele andar sandboxeado. Mod offline: `./gradlew compileJava --offline` (+ sandbox off).
- `pkill` devuelve exit 144 pero funciona — chequear el puerto después.
- App en background: usar `run_in_background: true` (un `cmd &` común se reapea). Esperar readiness
  con `until grep -q "Started ArcraftApplication"`.
- Login por form necesita el hidden `_csrf`; el fetch del assistant necesita header `X-CSRF-TOKEN`
  (token en `<meta name="_csrf">`).
- El launcher subprocess existente (`mod/.../WebBackendLauncher.java`, flag gradle `-PbundleWeb`,
  Jar-in-Jar de H2/jBCrypt) es la base del drag-and-drop actual — el objetivo 2 lo reemplaza por
  in-process. NeoForge tiene classloader modular incompatible con el launcher fat-jar de Spring Boot
  (documentado en `ARCRAFT_INFO.md`); Javalin/Jetty esquiva eso con el switch de TCCL (ver objetivo 2).

---

## Orden recomendado
1. **Objetivo 1** (deploy en Oracle Cloud, build de dos procesos actual). → `DEPLOY.md`
2. **Objetivo 2** (reescritura single-jar in-process, sobre `inprocess-web`), redeploy a la misma VM.
   → `INPROCESS_REWRITE_PLAN.md`
