package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chunk-exploration data for the web map. Global view aggregates heat counters across every
 * player while keeping the most recent terrain colour per coordinate.
 * NOTE: chunk_visit stores ids as VARCHAR(36) (written by the mod as UUID strings), so the
 * player join casts the UUID — portable across H2/PostgreSQL/MariaDB.
 */
public final class MapService {

    public static final MapService INSTANCE = new MapService();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record ChunkVisitDTO(
            int chunkX, int chunkZ,
            int r, int g, int b,
            String playerUsername,
            String lastVisited,
            long mined, long placed, long stay,
            int surfaceY) {}

    private record Row(int x, int z, int r, int g, int b, String username,
                       LocalDateTime lastVisited, long mined, long placed, long stay, int surfaceY) {}

    private static final String SELECT = """
            SELECT cv.chunk_x, cv.chunk_z, cv.map_color_r, cv.map_color_g, cv.map_color_b,
                   cv.blocks_mined, cv.blocks_placed, cv.stay_ticks, cv.surface_y, cv.last_visited,
                   p.username
            FROM chunk_visit cv
            JOIN player p ON cv.player_id = CAST(p.id AS VARCHAR(36))
            WHERE cv.dimension = :dim
            """;

    private MapService() {
    }

    private static Row map(ResultSet rs) throws SQLException {
        var ts = rs.getTimestamp("last_visited");
        return new Row(rs.getInt("chunk_x"), rs.getInt("chunk_z"),
                rs.getInt("map_color_r"), rs.getInt("map_color_g"), rs.getInt("map_color_b"),
                rs.getString("username"),
                ts == null ? LocalDateTime.MIN : ts.toLocalDateTime(),
                rs.getLong("blocks_mined"), rs.getLong("blocks_placed"), rs.getLong("stay_ticks"),
                rs.getInt("surface_y"));
    }

    private static ChunkVisitDTO dto(Row r, long mined, long placed, long stay) {
        return new ChunkVisitDTO(r.x(), r.z(), r.r(), r.g(), r.b(), r.username(),
                r.lastVisited().format(FMT), mined, placed, stay, r.surfaceY());
    }

    public List<ChunkVisitDTO> getChunksAsDTO(UUID playerId, String dimension) {
        return Database.jdbi().withHandle(h -> h
                .createQuery(SELECT + " AND cv.player_id = :pid")
                .bind("dim", dimension)
                .bind("pid", playerId.toString())
                .map((rs, c) -> {
                    Row r = map(rs);
                    return dto(r, r.mined(), r.placed(), r.stay());
                })
                .list());
    }

    /** Server-wide map: latest colour per coordinate + heat counters summed across players. */
    public List<ChunkVisitDTO> getGlobalChunks(String dimension) {
        List<Row> all = Database.jdbi().withHandle(h -> h
                .createQuery(SELECT)
                .bind("dim", dimension)
                .map((rs, c) -> map(rs))
                .list());
        Map<String, Row> latestByCoord = new LinkedHashMap<>();
        Map<String, long[]> heatByCoord = new HashMap<>(); // [mined, placed, stay]
        for (Row cv : all) {
            String key = cv.x() + "," + cv.z();
            Row existing = latestByCoord.get(key);
            if (existing == null || cv.lastVisited().isAfter(existing.lastVisited())) {
                latestByCoord.put(key, cv);
            }
            long[] heat = heatByCoord.computeIfAbsent(key, k -> new long[3]);
            heat[0] += cv.mined();
            heat[1] += cv.placed();
            heat[2] += cv.stay();
        }
        List<ChunkVisitDTO> dtos = new ArrayList<>();
        for (var entry : latestByCoord.entrySet()) {
            long[] heat = heatByCoord.get(entry.getKey());
            dtos.add(dto(entry.getValue(), heat[0], heat[1], heat[2]));
        }
        return dtos;
    }

    public long countChunks(UUID playerId, String dimension) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM chunk_visit WHERE player_id = :pid AND dimension = :dim")
                .bind("pid", playerId.toString())
                .bind("dim", dimension)
                .mapTo(Long.class).one());
    }
}
