# Deploying ArCraft (free tier)

The **web dashboard** (Spring Boot backend) is what gets deployed. The NeoForge mod keeps
running on the Minecraft server machine. In production the shared database becomes **PostgreSQL**
instead of the local H2 file; locally nothing changes (H2 stays the default).

## What's included
- `backend/Dockerfile` — multi-stage build (Maven → JRE), reads `$PORT`, runs the `prod` profile.
- `backend/src/main/resources/application-prod.properties` — Postgres config from env vars.
- `render.yaml` — one-click Render blueprint: web service + free Postgres + env wiring.

## Option A — Render (recommended, has free Postgres)
1. Push this repo to GitHub.
2. On https://render.com → **New + → Blueprint** → pick the repo. It reads `render.yaml`,
   creating the `arcraft` web service and the `arcraft-db` Postgres database.
3. In the service's **Environment** tab, fill the `sync:false` secrets:
   - `ARCRAFT_BASE_URL` = your URL, e.g. `https://arcraft.onrender.com`
   - `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` = your MercadoPago test credentials
   - `GEMINI_API_KEY` = your Gemini key (optional — enables the AI chat)
4. Deploy. First boot creates the schema (`ddl-auto=update`). Seed data runs if the DB is empty.

> Note: Render's free web service **sleeps** after ~15 min idle and cold-starts on the next
> request (~30 s). Fine for a demo — just open the page a minute before presenting.

## Option B — Railway / Fly.io
Both build the `backend/Dockerfile` directly. Provision a Postgres add-on and set the same env
vars (`ARCRAFT_DB_URL` or `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD`, `ARCRAFT_BASE_URL`,
`SPRING_PROFILES_ACTIVE=prod`, plus the secrets above).

## Environment variables reference
| Var | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Activates Postgres profile |
| `ARCRAFT_DB_URL` *(or `PGHOST`+`PGPORT`+`PGDATABASE`)* | Postgres JDBC URL / parts |
| `ARCRAFT_DB_USER` / `ARCRAFT_DB_PASS` *(or `PGUSER`/`PGPASSWORD`)* | DB credentials |
| `ARCRAFT_BASE_URL` | Public https URL (email links + MercadoPago return) |
| `MP_ACCESS_TOKEN` / `MP_PUBLIC_KEY` | MercadoPago (test) |
| `MP_CURRENCY` | Defaults to `ARS` |
| `GEMINI_API_KEY` | Enables AI chat |
| `PORT` | Injected by the host |

## Connecting the mod to the deployed database (optional, full integration)
The mod (`mod/.../DatabaseManager.java`) currently opens H2. To share the cloud Postgres,
point it at the same database and swap the H2 driver for the Postgres JDBC driver. The schema
DDL in `createSchema()` is largely Postgres-compatible (`UUID`, `BOOLEAN`, `REAL`,
`ADD COLUMN IF NOT EXISTS`). This is only needed if you want live in-game writes to reach the
deployed site; for the demo you can run the mod against local H2 and deploy the web with seeded
Postgres data.
