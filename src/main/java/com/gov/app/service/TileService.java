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
     * Generates a batched MVT tile containing taxi positions from multiple snapshots.
     * Each MVT feature carries a snapshot_id property so the frontend can filter client-side.
     *
     * @param snapshotIds array of snapshot IDs from the timeline metadata response
     * @param z           tile zoom level (0–22)
     * @param x           tile column index
     * @param y           tile row index
     * @param zone        optional zone name to filter positions
     * @return binary MVT payload (empty tile if no positions intersect)
     */
    public Mono<byte[]> fetchTimelineTile(Long[] snapshotIds, int z, int x, int y, String zone) {
        return tileRepository.fetchTimelineTile(snapshotIds, z, x, y, zone);
    }
}
