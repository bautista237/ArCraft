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
            String lastVisited
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
        // For chunks visited by multiple players, keep the most recently visited entry
        Map<String, ChunkVisit> latestByCoord = new LinkedHashMap<>();
        for (ChunkVisit cv : all) {
            String key = cv.getChunkX() + "," + cv.getChunkZ();
            ChunkVisit existing = latestByCoord.get(key);
            if (existing == null || cv.getLastVisited().isAfter(existing.getLastVisited())) {
                latestByCoord.put(key, cv);
            }
        }
        List<ChunkVisitDTO> dtos = latestByCoord.values().stream()
                .map(cv -> new ChunkVisitDTO(
                        cv.getChunkX(), cv.getChunkZ(),
                        cv.getMapColorR(), cv.getMapColorG(), cv.getMapColorB(),
                        cv.getPlayer().getUsername(),
                        cv.getLastVisited().format(FMT)
                ))
                .toList();
        return new GlobalMapData(dtos);
    }

    public List<ChunkVisitDTO> getChunksAsDTO(Player player, String dimension) {
        return chunkVisitRepository.findByPlayerAndDimension(player, dimension).stream()
                .map(cv -> new ChunkVisitDTO(
                        cv.getChunkX(), cv.getChunkZ(),
                        cv.getMapColorR(), cv.getMapColorG(), cv.getMapColorB(),
                        cv.getPlayer().getUsername(),
                        cv.getLastVisited().format(FMT)
                ))
                .toList();
    }

    public long countChunksByPlayerAndDimension(Player player, String dimension) {
        return chunkVisitRepository.countByPlayerAndDimension(player, dimension);
    }
}
