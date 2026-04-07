# ArCraft — Project Reference File
> Read this at the start of every Claude Code session before writing any code.

---

## What is ArCraft?
A NeoForge 1.21.1 Minecraft mod that instruments a private server, records all player
activity, stores it in a PostgreSQL database, and exposes it through a web analytics
dashboard built with Spring Boot. Stats platform + gamification layer for a private server.

## Developer
- Student: Matias Perez Menendez — tuteperezmenendez@gmail.com
- University: Universidad Austral, Pilar, Buenos Aires, Argentina
- Subject: Laboratorio I — Tutor: Fabrizio Di Santo

---

## Tech Stack
- Language: Java
- Web framework: Spring Boot
- ORM: Hibernate (JPA) via Spring Data JPA
- Database: PostgreSQL running in Docker on localhost:5432
- Frontend: Thymeleaf templates + HTML/CSS/JS, served by Spring Boot on port 8080
- Mod platform: NeoForge 1.21.1 (Phase 2 only — not needed for web work)

Spring Boot handles everything: REST endpoints, Thymeleaf page rendering, DB connection.
The Minecraft server runs on port 25565. The web dashboard runs on port 8080. Same machine, same IP.

---

## Professor's Roadmap (current priority)
- Class 4 (24-mar): User registration + Login/Logout         [BEHIND - build first]
- Class 5 (31-mar): Admin manually loads data + Dashboard    [BEHIND - build second]
- Class 6 (07-apr): Rankings page + Player profile page      [DUE TOMORROW]
- Class 7 (14-apr): Clans: create/view/manage + group stats

Start with manual/fake data and a working frontend. Do NOT work on the NeoForge mod
until the web is complete and the professor approves going further.

---

## Authentication
- No OAuth. Must work for cracked (offline-mode) Minecraft servers.
- Phase 1 (web only): Admin creates players manually via admin panel. Players log in
  with username + password set by the admin.
- Phase 2 (with mod): Player runs /arcraft set password <password> in-game.
  Player runs /arcraft get password to receive their password in private chat.
- Passwords stored with BCrypt hashing. Use Spring Security for session management.
- isAdmin flag on Player table controls access to the admin panel.

---

## Database Schema

### Connection config (application.properties)
```
spring.datasource.url=jdbc:postgresql://localhost:5432/arcraft
spring.datasource.username=arcraft
spring.datasource.password=arcraft
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### Tables and fields

**Player**
- id: UUID, PK, generated
- username: String, unique, not null
- passwordHash: String, not null
- isAdmin: boolean, default false
- clanId: UUID, FK -> Clan.id, nullable
- createdAt: Timestamp, not null

**Clan**
- id: UUID, PK, generated
- name: String, unique, not null
- tag: String (short identifier, e.g. "ARC"), not null
- leaderId: UUID, FK -> Player.id, not null
- friendlyFireEnabled: boolean, default false
- createdAt: Timestamp, not null

**ServerConfig**
- id: UUID, PK, generated
- serverStartDate: Timestamp (when the Minecraft world was first created)
- serverName: String
Used for the server timeline and "days elapsed" widget. Populated once by admin.

**PlayerStats** (one-to-one with Player, always exists when Player exists)
- id: UUID, PK, generated
- playerId: UUID, FK -> Player.id, unique, not null
- kills: long, default 0
- deaths: long, default 0
- damageDealt: float, default 0
- damageReceived: float, default 0
- mobsKilled: long, default 0
- blocksPlaced: long, default 0
- blocksMined: long, default 0
- itemsCrafted: long, default 0
- distanceWalked: long, default 0  (in blocks)
- distanceSwum: long, default 0
- distanceFlown: long, default 0
- distanceSailed: long, default 0  (by boat)
- shotsFired: long, default 0
- shotsHit: long, default 0
- longestShotBlocks: long, default 0
Note: K/D ratio, bow accuracy %, and total distance are computed, not stored.
Note: Titles (e.g. "most kills") are computed dynamically via ranking queries, not stored.

**BlockStatEntry** (per-player, per-block-type breakdown)
- id: UUID, PK, generated
- playerId: UUID, FK -> Player.id, not null
- blockType: String, not null  (e.g. "minecraft:diamond_ore")
- mined: long, default 0
- placed: long, default 0
Unique constraint on (playerId, blockType).
Used for: player profile breakdowns, crafted-items pie chart equivalent for blocks.

**ItemStatEntry** (per-player, per-item-type crafting breakdown)
- id: UUID, PK, generated
- playerId: UUID, FK -> Player.id, not null
- itemType: String, not null  (e.g. "minecraft:torch")
- count: long, default 0
Unique constraint on (playerId, itemType).
Used for: most crafted items pie chart on dashboard.

**MobStatEntry** (per-player, per-mob-type kill breakdown)
- id: UUID, PK, generated
- playerId: UUID, FK -> Player.id, not null
- mobType: String, not null  (e.g. "minecraft:zombie", "minecraft:ender_dragon")
- count: long, default 0
Unique constraint on (playerId, mobType).
Used for: mob kills breakdown on profile, boss kill counts (filter by boss mob types).

**PvPEvent** (one record per fight — from first hit to kill)
- id: UUID, PK, generated
- killerId: UUID, FK -> Player.id, not null  (who landed the killing blow)
- victimId: UUID, FK -> Player.id, not null
- startedAt: Timestamp, not null
- endedAt: Timestamp, not null
Used for: combat history, fight duration, per-matchup kill counts (COUNT GROUP BY killer+victim).

**PvPHit** (one record per individual hit within a PvPEvent)
- id: UUID, PK, generated
- pvpEventId: UUID, FK -> PvPEvent.id, not null
- attackerId: UUID, FK -> Player.id, not null
- damage: float, not null
- weapon: String, not null  (e.g. "minecraft:diamond_sword")
- hitAt: Timestamp, not null
Used for: full combat reconstruction/replay on player profile page.

**EventLog** (server timeline + live feed)
- id: UUID, PK, generated
- type: String, not null
  Values: PLAYER_DEATH, BOSS_KILL, ACHIEVEMENT, PVP_KILL, SERVER_MILESTONE, CUSTOM
- description: String, not null  (human-readable, e.g. "Steve killed Alex with a diamond sword")
- playerId: UUID, FK -> Player.id, nullable  (null for server-wide events)
- occurredAt: Timestamp, not null
Used for: live event feed on dashboard, server timeline page.
Notable events only — do NOT log every block break or mob kill here.

**Achievement** (definition of an achievement/milestone)
- id: UUID, PK, generated
- name: String, not null  (e.g. "Dragon Slayer")
- description: String, not null
- metricKey: String, not null  (e.g. "kills", "blocksPlaced", links to a PlayerStats field)

**PlayerAchievement** (who earned which achievement and when — first-come-first-served)
- id: UUID, PK, generated
- playerId: UUID, FK -> Player.id, not null
- achievementId: UUID, FK -> Achievement.id, not null
- earnedAt: Timestamp, not null
Unique constraint on (playerId, achievementId).
Used for: server timeline ("first dragon kill: Steve, Day 14"), player profile badges.

**ChunkData** (Phase 2 only — heatmap spatial data)
- id: UUID, PK, generated
- chunkX: int, not null
- chunkZ: int, not null
- activityScore: int, default 0  (accumulated player-ticks in this chunk)
- miningDiff: int, default 0    (blocks removed vs original seed)
- buildingDiff: int, default 0  (blocks added vs original seed)
- pvpCount: int, default 0      (number of PvP kills that occurred here)
Unique constraint on (chunkX, chunkZ).
DO NOT implement this table in Phase 1. It is Phase 2 only.

**ClanMessage** (clan chat, accessible via web)
- id: UUID, PK, generated
- clanId: UUID, FK -> Clan.id, not null
- senderId: UUID, FK -> Player.id, not null
- content: String, not null
- sentAt: Timestamp, not null

---

## Key Derived/Computed Values
These are never stored — always computed at query time:

- K/D ratio: PlayerStats.kills / PlayerStats.deaths (handle deaths=0 case)
- Bow accuracy %: (PlayerStats.shotsHit / PlayerStats.shotsFired) * 100
- Total distance: sum of all distance fields
- Clan aggregate stats: SUM of each PlayerStats field for all members of a clan
- Who you killed most: SELECT killerId, victimId, COUNT(*) FROM PvPEvent GROUP BY killerId, victimId ORDER BY COUNT DESC
- Who killed you most: same query filtered by victimId = current player
- Current title holders (e.g. "Top Killer"): SELECT playerId FROM PlayerStats ORDER BY kills DESC LIMIT 1
- Boss kill counts: SELECT mobType, SUM(count) FROM MobStatEntry WHERE mobType IN (...boss types...) GROUP BY mobType
- Days elapsed: DATEDIFF(NOW(), ServerConfig.serverStartDate)

---

## Features to Build

### Phase 1 — Professor requirements (build now)
1. User registration and login/logout (Spring Security, BCrypt)
2. Admin panel: create/edit players, manually set stats, log events, manage clans
3. Dashboard: top stats overview, most crafted items pie chart, PvP rankings bars, recent event feed
4. Rankings page: sortable leaderboard for every major stat
5. Player profile page: full stats, PvP history, mob kills, block breakdown, achievements
6. Clans: create, view members, show aggregate clan stats, clan chat (web)

### Phase 2 — Full vision (after professor approves)
7. NeoForge mod capturing real events and writing to DB
8. Heatmaps using ChunkData table
9. Live event feed via WebSockets
10. In-game commands (/arcraft set password, /arcraft get password)
11. Title/achievement cosmetic rewards in-game

---

## Web UI Design
- Dark theme: background #0f0f0f or #111111
- Accent colors: emerald green #10b981, aqua blue #06b6d4, orange #f97316
- Minecraft-inspired: subtle pixel/block texture details, monospaced font for numbers
- Professional dashboard aesthetic
- Pages: Login, Dashboard, Rankings, Player Profile, Clans, Clan Chat, Timeline (Phase 2), Maps (Phase 2)

---

## Seed Data for Development
On first startup (if DB is empty), seed the DB with:
- 1 admin player (username: "admin", password: "admin")
- 5 fake players with randomized stats across all PlayerStats fields
- Some fake BlockStatEntry, ItemStatEntry, MobStatEntry rows per player
- 10 fake EventLog entries of mixed types
- 1 clan containing 2-3 of the fake players
- At least 3 fake PvPEvent records with PvPHit children
