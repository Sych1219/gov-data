package com.gov.app.service;

import com.gov.app.repository.TileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TileService {

    private final TileRepository tileRepository;

    /**
     * Generates an MVT tile for the given snapshot and tile coordinates.
     *
     * @param snapshotId snapshot ID from the timeline metadata response
     * @param z          tile zoom level (0–22)
     * @param x          tile column index
     * @param y          tile row index
     * @param zone       optional zone name to filter positions
     * @return binary MVT payload (empty tile if no positions intersect)
     */
    public Mono<byte[]> fetchTile(long snapshotId, int z, int x, int y, String zone) {
        return tileRepository.fetchTile(snapshotId, z, x, y, zone);
    }
}
