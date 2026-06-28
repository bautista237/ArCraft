# ArCraft — Guía para el Final

> Material de apoyo para la presentación. Dos bloques: **no técnico** (qué es, con qué se compara,
> demo) y **técnico** (arquitectura, tecnologías, decisiones). Al final, **scripts de demo** tipo
> historia y un **checklist de casos de uso**.

---

## 1. Presentación NO técnica

### ¿Qué es ArCraft?
ArCraft es una **plataforma de estadísticas y gamificación para un servidor privado de Minecraft**.
Un mod instala "sensores" dentro del juego que registran todo lo que hace cada jugador (combates,
minería, construcción, viajes, muertes de jefes, etc.) y lo guardan en una base de datos. Una
**aplicación web** convierte esos datos en un panel tipo "dashboard deportivo": rankings, perfiles
de jugador, historial de peleas PvP, clanes, mapa de actividad y una tienda con economía de monedas.

En una frase: **"Es como tener las estadísticas de un eSport, pero para tu servidor de Minecraft
con amigos."**

### ¿Qué problema resuelve?
En un servidor privado no hay forma de saber quién es el mejor, qué pasó mientras no estabas, ni de
darle sentido competitivo al juego. ArCraft agrega **memoria, ranking y recompensas**: cada acción
cuenta, queda registrada y se traduce en reputación (rankings/títulos) y economía (monedas/tienda).

### Comparación con apps similares
| App | Qué hace | En qué se diferencia ArCraft |
|---|---|---|
| **Plan / Minecraft "stats" plugins** | Métricas de servidor (uptime, jugadores online) orientadas al **admin** | ArCraft está orientado al **jugador y a la competencia**: perfiles, rivalidades PvP, títulos, economía |
| **Statbus / sitios de stats de Hypixel** | Stats de servidores **públicos grandes**, sin control propio | ArCraft es **self-hosted** para tu servidor privado; vos sos dueño de los datos |
| **Plan de logros de Steam / tracker.gg** | Logros y stats por juego | ArCraft suma **gamificación con economía real** (compra de monedas con MercadoPago) y **chat con IA** sobre tus datos |
| **Discord bots de stats** | Comandos sueltos en Discord | ArCraft es una **web completa** con visualizaciones, mapa de calor y tienda |

**Diferenciales clave para destacar:** datos propios (privacidad), enfoque competitivo por jugador,
economía con pasarela de pago real (modo prueba), mapa de actividad del mundo, y un **asistente de
IA** que responde preguntas en lenguaje natural sobre la base de datos.

---

## 2. Presentación TÉCNICA

### Arquitectura (modelo de dos procesos)
```
   ┌─────────────────────┐         ┌──────────────────────────┐
   │  Mod NeoForge 1.21.1 │ escribe │   Base de datos (H2 /     │
   │  (dentro del juego)  │────────▶│   PostgreSQL en la nube)  │
   │  eventos + comandos  │         └──────────────────────────┘
   └─────────────────────┘                    ▲ lee/escribe
                                               │
   ┌──────────────────────────────────────────┴───────────────┐
   │  Backend Spring Boot (web)                                 │
   │  Thymeleaf + Spring Security + JPA/Hibernate              │
   │  REST/MVC, tienda, IA, mails, MercadoPago                  │
   └───────────────────────────────────────────────────────────┘
```
- **Dos procesos independientes** que comparten estado por la base de datos.
- En **local** comparten un archivo **H2** (`AUTO_SERVER=TRUE` permite múltiples conexiones).
- En **producción** la base es **PostgreSQL** (perfil `prod`); la web se despliega en free tier.

**Justificación:** el classloader modular de NeoForge es incompatible con el protocolo de
"nested jars" de Spring Boot, así que embeber el backend dentro del mod no es viable. El modelo de
dos procesos desacopla el ciclo de vida del juego del de la web y permite desplegar la web sola.

### Stack y por qué
| Tecnología | Rol | Justificación |
|---|---|---|
| **Java 21** | Lenguaje | Mismo runtime que Minecraft/NeoForge; records, text blocks |
| **Spring Boot 3.3** | Framework web | MVC + seguridad + JPA + scheduling integrados; productivo |
| **Thymeleaf** | Vistas server-side | SSR simple, sin build de frontend; integra con Spring Security |
| **Spring Security + BCrypt** | Auth | Sesiones, roles (ADMIN/USER); BCrypt verificable también desde el mod. Funciona con servidores *cracked* (sin OAuth obligatorio) |
| **Hibernate / Spring Data JPA** | ORM | Mapeo de entidades, repositorios declarativos |
| **H2 (local) / PostgreSQL (prod)** | Base de datos | H2 archivo para dev sin instalar nada; Postgres para deploy |
| **NeoForge 1.21.1** | Mod | Captura de eventos in-game vía event bus + comandos Brigadier |
| **MercadoPago Checkout Pro** | Pagos | Pasarela líder en Argentina; ambiente de prueba sin dinero real |
| **Google Gemini** | IA | Free tier; chat en lenguaje natural sobre los datos |
| **JavaMail + Thymeleaf** | Emails HTML | Verificación y recordatorios de eventos con links a la app |
| **Docker + Render** | Deploy | Build reproducible; free tier con Postgres administrado |

### Decisiones de diseño a mencionar
- **Valores derivados no se almacenan** (K/D, precisión de arco, distancia total): se calculan en
  query para no desincronizar datos.
- **Idempotencia en pagos:** cada compra de monedas crea una `CoinOrder`; las monedas se acreditan
  **una sola vez** aunque lleguen el redirect del navegador *y* el webhook de MercadoPago.
- **IA "grounded":** no se le deja a la IA ejecutar SQL; se le pasa un **snapshot textual** de la
  base como contexto. Evita inyección y mantiene respuestas ancladas a datos reales.
- **Secretos fuera de git:** credenciales en `application-secret.properties` (gitignored) o en
  variables de entorno; nunca hardcodeadas.

### Modelo de datos (entidades principales)
`Player`, `PlayerStats`, `Clan`, `ClanMessage`, `PvPEvent` + `PvPHit`, `BlockStatEntry`,
`ItemStatEntry`, `MobStatEntry`, `EventLog`, `Achievement` + `PlayerAchievement`, `ChunkVisit`
(mapa de calor), `StoreItem` + `PlayerPurchase`, `CoinOrder` (compras MercadoPago), `ServerConfig`.

---

## 3. Casos de uso — Scripts de DEMO (formato historia)

> Consejo de los profes: narrar historias. Para mostrar interacción entre dos usuarios, usar modo
> incógnito o un segundo navegador. **Tener la base con datos reales/seed antes de empezar.**

### Historia 1 — "Juan quiere mejorar y compite"
1. **Juan no tiene cuenta** → muestra el **registro** y el **login**. *(CU: registro/login)*
2. Entra al **Dashboard**: ve stats generales, ítems más crafteados, feed de eventos. *(CU: dashboard)*
3. Va a **Rankings** y ordena por kills / bloques minados / K/D para ver dónde está parado.
   Usa los filtros/orden de columnas. *(CU: rankings)*
4. Abre su **perfil**: stats completas, mobs, bloques, y la nueva sección **PvP Analysis**
   (némesis, víctima favorita, head-to-head, win rate). *(CU: perfil + PvP detalle)*

### Historia 2 — "La rivalidad PvP"
1. En el perfil de Juan, sección PvP, hace clic en **"View hits →"** de una pelea.
2. Muestra el **detalle del encuentro**: peleadores, **Combat Breakdown** (daño por jugador, golpe
   más fuerte, DPS), **armas usadas** y el **historial golpe a golpe** con corazones de daño.
   *(CU: más detalle de data para PvP — pedido explícito del mail)*

### Historia 3 — "Economía y tienda con MercadoPago"
1. Juan quiere un título pero le faltan monedas. Va a **Store**.
2. En **"Buy coins"** elige un paquete y paga con **MercadoPago (modo prueba)** usando una
   tarjeta de test → vuelve a la app y se **acreditan las monedas**. *(CU: integración MercadoPago)*
3. Con las monedas, **compra un título** y lo **equipa**; aparece junto a su nombre en rankings.

### Historia 4 — "El asistente con IA"
1. Juan abre **Assistant** y pregunta en lenguaje natural: *"¿Quién tiene más kills?"*,
   *"Listá los clanes y sus líderes"*, *"¿Qué pasó últimamente en el servidor?"*.
2. La IA responde con datos **reales** de la base. *(CU: integración chat con IA sobre la BD)*

### Historia 5 — "Clanes y comunidad"
1. Juan crea/entra a un **clan**, ve **stats agregadas** del clan y usa el **chat de clan**. *(CU: clanes)*
2. Muestra el **Mapa** de actividad (chunks visitados / minería / construcción). *(CU: mapa)*

### Historia 6 — "Eventos y mails"
1. Un admin crea un **evento** (torneo). *(CU: admin/eventos)*
2. In-game, Juan registra su email con `/email <address>`, recibe un **mail HTML** con el código y
   links a la app, y verifica con `/email verify <código>`. *(CU: verificación + templates HTML)*
3. Cuando se acerca el evento, los jugadores verificados reciben un **recordatorio por mail**.

---

## 4. Checklist de Casos de Uso (que TODO funcione antes del final)
- [ ] Registro de usuario + Login/Logout
- [ ] Dashboard con overview, gráfico de ítems, feed de eventos
- [ ] Rankings ordenables por cada stat
- [ ] Perfil de jugador completo + **PvP Analysis**
- [ ] Detalle de encuentro PvP (**Combat Breakdown** + armas + golpe a golpe)
- [ ] Clanes: crear/ver/gestionar + stats agregadas + chat
- [ ] Mapa de actividad (heatmap de chunks)
- [ ] Tienda: títulos/cosméticos + **compra de monedas con MercadoPago (test)**
- [ ] **Asistente IA** respondiendo sobre la base (requiere `GEMINI_API_KEY`)
- [ ] Mails **HTML** con links (verificación + recordatorios de eventos)
- [ ] App **deployada** en free tier con base con datos reales
- [ ] Panel de admin (jugadores, clanes, eventos, tienda, monedas)

> Recordatorio del mail de los profes: si algo está incompleto, conviene presentarse en la próxima
> fecha. Ante dudas, escribirles por mail.

---

## 5. Antes de presentar (mini-runbook)
- Ver `DEPLOY.md` para levantar la web (o usar el deploy). Si Render duerme, abrir la página
  ~1 min antes.
- Tener `application-secret.properties` (local) o las env vars (deploy) con MercadoPago y Gemini.
- Sembrar/confirmar datos reales en la base (jugadores, peleas, clanes, eventos).
- Tener a mano una **tarjeta de prueba** de MercadoPago y el **usuario de prueba**.
- Segundo navegador / incógnito listo para mostrar interacción entre usuarios.
