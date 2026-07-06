# ArCraft — Explicación técnica del proyecto

> Documento para la presentación técnica: qué es el stack, por qué se eligió cada pieza, y
> cómo funciona todo junto de punta a punta. Pensado para explicarse en ~15 minutos frente a
> un tribunal técnico. (La guía de demo/no-técnica está en `PRESENTACION_FINAL.md`; el detalle
> de implementación para programar, en `ARCHITECTURE.md`.)

---

## 1. Qué es ArCraft (elevator pitch técnico)

Un **mod de servidor para Minecraft (NeoForge 1.21.1)** que instrumenta todo lo que pasa en el
juego y lo publica en un **dashboard web servido desde adentro del propio proceso del
servidor**. Un solo `.jar` en `mods/` levanta: captura de eventos, base de datos embebida,
sitio web completo con login, pagos reales (MercadoPago), asistente de IA (Gemini) y mails
HTML. Cero instalación adicional.

**La frase clave: un proceso, un datasource, un jar.**

## 2. El stack, pieza por pieza (y POR QUÉ cada una)

| Capa | Tecnología | Por qué esta y no otra |
|---|---|---|
| Juego | **NeoForge 1.21.1 (Java 21)** | El mod loader estándar moderno para servidores 1.21; event bus para instrumentar gameplay. |
| Captura de datos | **Event handlers + cola single-writer** | Los eventos del juego corren en el server thread: NUNCA se bloquea con I/O de DB; cada evento encola una escritura que ejecuta un único hilo dedicado. |
| Base de datos | **H2 embebida** (default) / **PostgreSQL o MariaDB** (config) | H2 es Java puro (sin binarios nativos), archivo único → sostiene el "drag & drop". Para servidores grandes (200+ jugadores, millones de filas) se apunta a un RDBMS real con UN cambio de config. Es el mismo modelo de los plugins top (LuckPerms, CoreProtect). |
| Pool de conexiones | **HikariCP** | El estándar de facto de la JVM; el mod y la web comparten un único pool. |
| Acceso a datos | **jdbi (SQL directo)** | Sin ORM: bajo los classloaders modulares de NeoForge, Hibernate es frágil y pesado. SQL explícito, portable entre los 3 dialectos, mapeo a view-models simples. |
| Servidor web | **Javalin 6 (Jetty 11 embebido) + virtual threads** | Es lo que hace la comunidad (Dynmap/BlueMap embeben Jetty): el web vive DENTRO del proceso del juego. Javalin da routing/sesiones/estáticos en ~0 config. Virtual threads (Java 21) para requests baratos. |
| Templates | **Thymeleaf 3.1** | Server-side rendering; los mismos templates del backend original se reusaron casi intactos. |
| Seguridad | **Sesiones Jetty + BCrypt + SameSite/Origin-check** | Login contra el hash BCrypt que el mod escribe cuando el jugador se registra in-game. CSRF sin framework: cookie SameSite=Lax + validación de Origin en cada POST. Guard central para `/admin/**`. |
| Pagos | **MercadoPago Checkout Pro (REST)** | Flujo: crear "preference" → redirect al checkout hosteado → return URL + webhook confirman → acreditación de coins **idempotente** (una orden se paga exactamente una vez aunque lleguen return Y webhook). |
| IA | **Gemini API** (REST, `java.net.http`) | Grounding: se arma un snapshot de texto de la DB (jugadores, stats, clanes, eventos) y se inyecta en el prompt → el modelo SOLO responde con datos reales; cadena de fallback de modelos + retry ante 429/503 (free tier). |
| Mails | **Angus Mail (SMTP) + ScheduledExecutorService** | HTML renderizado con los mismos templates Thymeleaf; un scheduler barre códigos de verificación pendientes (30 s) y recordatorios de eventos (10 min). |
| Config | **TOML de NeoForge (`ModConfigSpec`)** | La convención de mods: `config/arcraft-common.toml` auto-generado con comentarios + pantalla de config in-game auto-generada. Tokens vacíos ⇒ la feature se oculta de la web (gating). |
| Empaquetado | **Jar-in-Jar de NeoForge** | Las ~36 librerías van ANIDADAS dentro del jar del mod como módulos propios (16 MB). Si otro mod trae la misma lib, NeoForge resuelve versiones — la razón por la que es el estándar y no un fat-jar shadeado. |

## 3. Los tres problemas técnicos interesantes (para contar en la defensa)

### 3.1 El classloader modular de NeoForge
NeoForge carga cada mod y cada jar anidado como **módulos separados**. Dos consecuencias:
- `DriverManager.getConnection()` no encuentra drivers (solo ve los visibles para el caller)
  → construimos los DataSource **por referencia directa de clase** (`new org.h2.jdbcx.JdbcDataSource()`),
  que sí atraviesa la lectura de módulos.
- Jetty descubre servicios vía el *thread context classloader*, que apunta al loader del juego
  → **swap del TCCL** alrededor del arranque de Javalin y restauración inmediata (el fix
  documentado por la propia guía "Javalin and Minecraft servers").

### 3.2 Migración sin pérdida (upgrade de la versión de 2 procesos)
Al primer arranque del jar nuevo sobre un servidor viejo:
1. Se **mueve** la H2 de la raíz (`./arcraft-data.mv.db`) a la carpeta de datos `arcraft/`.
2. Se **importan los secrets** del viejo `application.properties` (SMTP, MercadoPago, Gemini)
   al TOML — con `SPEC.save()` explícito porque `ConfigValue.set()` solo toca memoria.
3. El schema es idéntico (el DDL nuevo es la unión del DDL del mod + las tablas que creaba JPA,
   todo `IF NOT EXISTS`) ⇒ jugadores, stats, clanes y compras quedan intactos.
Verificado en vivo: el server de prueba migró DB + 3 grupos de credenciales en un arranque.

### 3.3 Por qué murió Spring Boot (la decisión de arquitectura)
La versión anterior eran DOS procesos (mod + backend Spring) compartiendo un archivo H2 con
`AUTO_SERVER`. Eso causó el bug real de la verificación de email: cada proceso abría un
archivo H2 DISTINTO (path hardcodeado vs relativo) y el código enviado nunca coincidía con el
guardado. Además, el launcher de fat-jar de Spring es incompatible con el classloader de
NeoForge (por eso existía el hack de lanzar un JVM hijo). La reescritura in-process elimina
la clase entera de bugs: **un proceso ⇒ un pool ⇒ imposible desincronizarse**, y de paso el
jar bajó de 64 MB a 16 MB y no hay JVM extra consumiendo RAM.

## 4. Flujo de datos end-to-end (para dibujar en el pizarrón)

```
Jugador rompe un bloque
  → ArcraftEventHandler (server thread) encola la escritura
  → ArCraft-DB-Writer (1 hilo) ejecuta: UPDATE player_stats / block_stat_entry (SQL portable)
  → HikariCP → H2 (arcraft/arcraft-data.mv.db)

Browser pide /rankings
  → Jetty (virtual thread) → Auth gate (¿sesión? ¿admin? ¿origin?)
  → RankingsService: SELECT stats JOIN player JOIN clan (jdbi → mismo pool)
  → view-models → Thymeleaf (WebContext) → HTML

Jugador compra coins
  → POST /store/coins/checkout → MercadoPagoService crea preference (REST) + fila coin_order PENDING
  → redirect al checkout de MP → paga → MP redirige a /return Y postea /webhook
  → confirmByPayment consulta el pago → creditOrder (transacción, idempotente) → coins += N

Usuario pregunta al Assistant
  → POST /assistant/ask → AiContextService serializa la DB a texto
  → GeminiService: prompt con el snapshot como única fuente de verdad
  → fallback 2.5-flash → 2.5-flash-lite → flash-latest con retry en 429/503 → respuesta
```

## 5. Seguridad (resumen para preguntas)

- **AuthN**: BCrypt (hash escrito por el mod in-game); sesión con id regenerado al loguear.
- **AuthZ**: gate central — todo requiere login salvo lista pública; `/admin/**` exige flag admin.
- **CSRF**: cookie `SameSite=Lax` + `HttpOnly` y verificación de que el host de Origin/Referer
  coincida con el Host en todo request mutante (webhook de MP exento: es server-to-server y la
  acreditación es idempotente).
- **Secrets**: nunca en el repo — viven en `config/arcraft-common.toml` del servidor (gitignored
  del lado del server) y el seeder avisa que se cambie `admin/admin`.
- **SQL injection**: imposible por diseño — todo va con parámetros bindeados (jdbi/PreparedStatement).
- **Prompt injection → DB**: la IA no ejecuta queries; recibe un snapshot de solo lectura.

## 6. Calidad y verificación (qué se testeó)

- **Upgrade real**: server de prueba con datos reales (jugadores Vontk/Bautista237/pepe, clan
  Testers) → un arranque migró DB + secrets; las 17 rutas devolvieron HTTP 200 con los datos viejos.
- **Seguridad**: anónimo → redirect a login; POST cross-origin → 403.
- **Integraciones en vivo**: Gemini respondió con datos reales de la DB; MercadoPago creó una
  preference real (test) y redirigió al checkout.
- **Instalación limpia**: jar solo en un server fresco → crea `config/` + `arcraft/`, siembra
  admin + catálogo, y las features sin token quedan ocultas.

## 7. Números útiles para la presentación

- 1 jar de **16 MB** (36 libs anidadas) — antes: 64 MB + segundo proceso Spring.
- ~6.700 líneas de la versión Spring portadas a ~5.000 en el mod (sin contar templates reusados).
- 18 tablas; índices en las queries calientes (PvP, feed, chat de clan, mapa).
- 3 dialectos de DB soportados con el mismo codebase (H2/PostgreSQL/MariaDB).
- Escala objetivo: 200+ jugadores concurrentes (escrituras async en batch por un solo writer,
  reads pooled) y millones de filas históricas (RDBMS externo por config).
