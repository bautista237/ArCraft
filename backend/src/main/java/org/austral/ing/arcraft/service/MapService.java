package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.ChunkVisit;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.ChunkVisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChunkVisitRepository chunkVisitRepository;

    public record MapData(
            List<ChunkVisit> chunks,
            int minX, int maxX,
            int minZ, int maxZ,
            long totalChunksExplored
    ) {}

    public record GlobalMapData(List<ChunkVisitDTO> chunks) {}

    public record ChunkVisitDTO(
            int chunkX, int chunkZ,
            int r, int g, int b,
            String playerUsername,
            String lastVisited,
            long mined, long placed, long stay,
            int surfaceY
    ) {}

    public MapData getPlayerMapData(Player player, String dimension) {
        List<ChunkVisit> chunks = chunkVisitRepository.findByPlayerAndDimension(player, dimension);
        if (chunks.isEmpty()) {
            return new MapData(chunks, 0, 0, 0, 0, 0);
        }
        int minX = chunks.stream().mapToInt(ChunkVisit::getChunkX).min().orElse(0) - 2;
        int maxX = chunks.stream().mapToInt(ChunkVisit::getChunkX).max().orElse(0) + 2;
        int minZ = chunks.stream().mapToInt(ChunkVisit::getChunkZ).min().orElse(0) - 2;
        int maxZ = chunks.stream().mapToInt(ChunkVisit::getChunkZ).max().orElse(0) + 2;
        return new MapData(chunks, minX, maxX, minZ, maxZ, chunks.size());
    }

    public GlobalMapData getGlobalMapData(String dimension) {
        List<ChunkVisit> all = chunkVisitRepository.findAllByDimension(dimension);
        // Aggregate per coordinate: keep the most-recent terrain colour but SUM the heat
        // counters across every player so the global heatmaps reflect server-wide activity.
        Map<String, ChunkVisit> latestByCoord = new LinkedHashMap<>();
        Map<String, long[]> heatByCoord = new HashMap<>(); // [mined, placed, stay]
        for (ChunkVisit cv : all) {
            String key = cv.getChunkX() + "," + cv.getChunkZ();
            ChunkVisit existing = latestByCoord.get(key);
            if (existing == null || cv.getLastVisited().isAfter(existing.getLastVisited())) {
                latestByCoord.put(key, cv);
            }
            long[] heat = heatByCoord.computeIfAbsent(key, k -> new long[3]);
            heat[0] += cv.getBlocksMined();
            heat[1] += cv.getBlocksPlaced();
            heat[2] += cv.getStayTicks();
        }
        List<ChunkVisitDTO> dtos = new ArrayList<>();
        for (var entry : latestByCoord.entrySet()) {
            ChunkVisit cv = entry.getValue();
            long[] heat = heatByCoord.get(entry.getKey());
            dtos.add(new ChunkVisitDTO(
                    cv.getChunkX(), cv.getChunkZ(),
                    cv.getMapColorR(), cv.getMapColorG(), cv.getMapColorB(),
                    cv.getPlayer().getUsername(),
                    cv.getLastVisited().format(FMT),
                    heat[0], heat[1], heat[2], cv.getSurfaceY()));
        }
        return new GlobalMapData(dtos);
    }

    public List<ChunkVisitDTO> getChunksAsDTO(Player player, String dimension) {
        return chunkVisitRepository.findByPlayerAndDimension(player, dimension).stream()
                .map(cv -> new ChunkVisitDTO(
                        cv.getChunkX(), cv.getChunkZ(),
                        cv.getMapColorR(), cv.getMapColorG(), cv.getMapColorB(),
                        cv.getPlayer().getUsername(),
                        cv.getLastVisited().format(FMT),
                        cv.getBlocksMined(), cv.getBlocksPlaced(), cv.getStayTicks(), cv.getSurfaceY()))
                .toList();
    }

    public long countChunksByPlayerAndDimension(Player player, String dimension) {
        return chunkVisitRepository.countByPlayerAndDimension(player, dimension);
    }
}
