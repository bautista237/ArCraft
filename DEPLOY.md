# Deploy de ArCraft en free tier — investigación y decisión

> Doc de decisión para el final. Resume qué servicio conviene, por qué, y en qué
> orden hacerlo respecto de la reescritura a **single-jar** (`INPROCESS_REWRITE_PLAN.md`).
> Investigación hecha en julio 2026 (los free tiers cambian seguido — ver fuentes al final).

---

## 0. El dato que manda todo: nuestra arquitectura son DOS procesos

ArCraft no es "una web". Son **dos procesos que comparten un archivo H2**:

1. **Servidor Minecraft (NeoForge 1.21.1 + el mod)** — escribe stats en `./arcraft-data`.
2. **Backend web (Spring Boot)** — lee/escribe el **mismo** `./arcraft-data` vía H2 `AUTO_SERVER`.

Para que las escrituras en vivo del juego se vean en la web, **los dos procesos tienen que
compartir la misma base**. Hoy lo hacen porque corren en la misma carpeta de la misma máquina.

Esto descarta de una casi todos los "web hosts" gratis: no corren un servidor de Minecraft y no
exponen el puerto 25565, y un "Minecraft host" gratis no corre nuestro backend web ni deja
compartir la base. **Necesitamos una VM completa (un Linux con IP pública) que corra los dos
procesos juntos**, igual que hoy en la laptop.

---

## 1. Comparación de opciones (julio 2026)

| Opción | ¿Corre MC + web juntos? | RAM | 24/7 real | Costo | Veredicto |
|---|---|---|---|---|---|
| **Oracle Cloud Always Free** (ARM Ampere A1) | ✅ VM completa, cualquier puerto | **2 OCPU / 12 GB** | ✅ sin dormir | $0 para siempre | ✅ **RECOMENDADO** |
| Google Cloud e2-micro (Always Free) | ✅ VM completa | 1 GB (poco) | ✅ | $0 | ⚠️ RAM insuficiente para MC + Spring |
| Render (free web service) | ❌ solo web, no MC | 512 MB | ❌ duerme a los 15 min (cold start ~30 s) | $0 | ⚠️ solo demo del web, DB partida |
| Railway | ❌ solo web | — | — | ya **no** hay free tier (solo $5 crédito único) | ❌ |
| Fly.io | ❌ solo web | — | — | free tier **eliminado** (trial 2 h/7 días) | ❌ |
| Falix / AxentHost / FalixNodes (MC host gratis) | ❌ corre MC pero **no** nuestro web ni deja compartir la DB | 4 GB | ✅ | $0 | ❌ no expone el backend |

### Por qué gana Oracle Cloud Always Free
- Es una **VM Linux real con IP pública** → corremos **los dos procesos en la misma carpeta**,
  con la **misma H2 compartida**, exactamente como en la laptop. **Cero cambios de arquitectura.**
- Aún después del recorte de junio 2026 (bajó de 4→2 OCPU / 24→12 GB), **12 GB de RAM** alcanzan
  de sobra para un server NeoForge chico + Spring Boot + H2.
- Abrís **cualquier puerto** (25565 para MC, 8080 para el web) — no como Render, que solo da HTTP.
- **10 TB de tráfico saliente/mes** y 200 GB de disco: no lo vamos a rozar.
- **Always Free de verdad**: no duerme, no expira, sin tarjeta obligatoria para el uso free.

### Advertencias de Oracle (para no comernos una sorpresa en el final)
- **Java 21 en ARM64 anda perfecto** (el mod no usa librerías nativas, es JVM pura). NeoForge
  corre bien en ARM. Si algún plugin trajera código nativo x86 habría que revisar — no es el caso.
- La capacidad de **Ampere A1 en tu región** a veces está agotada al crear la instancia:
  se reintenta en otra Availability Domain o con un script de reintento (típico, documentado).
- Instancias **idle** pueden ser reclamadas: con MC + web corriendo 24/7 no hay riesgo.
- Alternativa si A1 no aparece: instancia **AMD micro** (1/8 OCPU, 1 GB) — muy justa, o GCP e2-micro.

---

## 2. Decisión de ORDEN: ¿deploy antes o después del single-jar?

**Decisión: deployamos AHORA con el build actual de dos procesos. El single-jar queda para DESPUÉS.**

### Por qué deploy primero (con lo que ya funciona)
1. **El build actual funciona y está verificado** (verify, MercadoPago, Gemini, PvP, mails HTML).
   El final necesita el sistema **vivo y accesible**; no lo bloqueamos detrás de un refactor grande.
2. **En una VM, el modelo de dos procesos anda igual que en local** — misma carpeta, misma H2
   compartida. El deploy **no requiere** la reescritura para nada.
3. La reescritura in-process (Javalin embebido, cambio de capa de datos, TCCL, etc.) es **grande y
   riesgosa**. Hacerla antes del deploy sería jugarse el final a un refactor sin red.
4. El beneficio del single-jar es de **distribución/publicación** ("tirás un `.jar` y listo"), que
   es **ortogonal** a hostear *nuestra propia* instancia. Para el deploy no aporta nada todavía.
5. Cuando terminemos el single-jar, **redeployar a la misma VM es trivial**: reemplazás el `.jar`.

### Qué queda para después (ya planificado)
El plan completo del single-jar está en **`INPROCESS_REWRITE_PLAN.md`** (rama `inprocess-web`):
web in-process con Javalin embebido, config TOML estilo mod, HikariCP + multi-DB, migración sin
pérdida, mod-list con logo, etc. Se hace **sobre la VM ya deployada**, sin tocar `main`.

> Resumen del orden: **(A) Deploy en Oracle Cloud con el build de dos procesos → (B) single-jar
> in-process como iteración posterior, redeployando el `.jar` a la misma VM.**

---

## 3. Cómo deployar en Oracle Cloud Always Free (build actual, dos procesos)

Igual que la laptop, pero en una VM con IP pública. La H2 se comparte porque ambos corren en la
misma carpeta.

1. **Crear la VM**: Oracle Cloud → Compute → Instance → shape **VM.Standard.A1.Flex**
   (2 OCPU / 12 GB) o AMD micro si A1 no hay. SO: Ubuntu 22.04/24.04 LTS (ARM).
2. **Abrir puertos** en la Security List / NSG de la VCN: `25565` (Minecraft), `8080` (web).
   Además en la VM: `sudo iptables`/`ufw` para 25565 y 8080 (Oracle trae iptables restrictivo).
3. **Instalar Java 21** (Temurin/OpenJDK ARM64) y `screen`/`tmux`.
4. **Subir el server**: la carpeta `arcraft-test-server/` (server MC + `arcraft-web/arcraft-backend.jar`)
   vía `scp`/`rsync`. Copiar también `application.properties` (secrets — **NO** va al repo).
5. **`arcraft.app.base-url`**: ponerlo en `http://<IP-pública>:8080` (o un dominio si tenés).
   Esto arregla los links de los mails y las `back_urls` de MercadoPago.
6. **Arrancar**: `SRV=/ruta/al/server ./start.sh` (levanta backend, espera "Started
   ArcraftApplication", luego el server MC). Idealmente dentro de `tmux` o como servicio `systemd`.
7. **Verificar**: `http://<IP-pública>:8080` desde otra red, y conectarse al MC en `<IP>:25565`.

> **Seguridad al exponer a internet**: `spring.h2.console.enabled=false` (ya está en el
> `application.properties` del server). Los secrets viven solo en ese archivo, fuera del repo.

---

## 4. Alternativa SOLO-DEMO (si no querés dejar tu VM/PC prendida): Render

Sirve **únicamente** para mostrar el dashboard web público con **datos semilla** (Postgres), pero
las escrituras en vivo del juego **no** llegan salvo que cablees el mod a ese Postgres. No es el
"deploy mundial" real; es una demo del front. Archivos ya presentes: `backend/Dockerfile`,
`application-prod.properties` (perfil Postgres), `render.yaml`.

1. Push del repo a GitHub → Render → **New + → Blueprint** → repo (lee `render.yaml`: web + Postgres).
2. En **Environment** completar los `sync:false`: `ARCRAFT_BASE_URL`, `MP_ACCESS_TOKEN`,
   `MP_PUBLIC_KEY`, `GEMINI_API_KEY`.
3. Deploy. Primer boot crea el schema (`ddl-auto=update`) y corre el seed si la DB está vacía.

> Render **duerme** a los ~15 min y cold-startea ~30 s: abrí la página un minuto antes de presentar.

### Variables de entorno (perfil prod / Render)
| Var | Para qué |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Activa el perfil Postgres |
| `ARCRAFT_DB_URL` *(o `PGHOST`+`PGPORT`+`PGDATABASE`)* | JDBC Postgres |
| `ARCRAFT_DB_USER` / `ARCRAFT_DB_PASS` *(o `PGUSER`/`PGPASSWORD`)* | Credenciales DB |
| `ARCRAFT_BASE_URL` | URL pública https (links de mail + return de MercadoPago) |
| `MP_ACCESS_TOKEN` / `MP_PUBLIC_KEY` | MercadoPago (test) |
| `MP_CURRENCY` | Default `ARS` |
| `GEMINI_API_KEY` | Habilita el chat AI |
| `PORT` | Lo inyecta el host |

---

## 5. Fuentes (julio 2026)
- Oracle Cloud Always Free (recorte jun-2026 a 2 OCPU/12 GB, uso 24/7 para MC):
  <https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm>,
  <https://fullmetalbrackets.com/blog/oci-free-tier-breakdown>,
  <https://medium.com/@imvinojanv/setup-always-free-vps-with-4-ocpu-24gb-ram-and-200gb-storage-the-ultimate-oracle-cloud-guide-bed5cbf73d34>
- Oracle vs Google Cloud free tier (e2-micro 1 GB vs Ampere): 
  <https://freevps.edu.pl/blog/oracle-vs-google-cloud-free-tier-2026/>
- Render/Railway/Fly free tiers 2026 (Railway/Fly sin free tier, Render duerme):
  <https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026>,
  <https://techsy.io/en/blog/railway-vs-render-vs-fly-io>
- MC hosts gratis 24/7 (Falix/AxentHost — no exponen backend propio):
  <https://www.hostingadvice.com/how-to/best-free-minecraft-server-hosting/>
