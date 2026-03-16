package com.gov.app.controller;

import com.gov.app.service.TileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Slf4j
@Tag(name = "Vector Tiles", description = "Mapbox Vector Tile (MVT) endpoints for taxi positions")
@RestController
@RequestMapping("/api/v1/tiles")
@RequiredArgsConstructor
public class TileController {

    private final TileService tileService;

    /**
     * 4.7.2 Batched Timeline Tile Endpoint (MVT)
     * Returns taxi positions from multiple snapshots as a single binary Mapbox Vector Tile.
     * Each MVT feature carries a snapshot_id property so the frontend can filter client-side
     * — no per-frame network request is needed during playback.
     */
    @Operation(
        summary = "Batched taxi positions as MVT tile",
        description = "Returns taxi positions from all requested snapshots as a single binary Mapbox Vector Tile (MVT). " +
                      "Each feature carries a snapshot_id property, allowing the frontend to switch frames instantly " +
                      "via a Mapbox filter expression. The z/x/y parameters follow the standard slippy map tile convention. " +
                      "The MVT layer name is 'taxis'. Tiles are immutable per snapshot (Cache-Control: max-age=300)."
    )
    @GetMapping(value = "/taxis/timeline/{z}/{x}/{y}.pbf", produces = "application/x-protobuf")
    public Mono<ResponseEntity<byte[]>> getTimelineTile(
            @Parameter(description = "Tile zoom level (0–22)", example = "12")
            @PathVariable int z,

            @Parameter(description = "Tile column index", example = "3254")
            @PathVariable int x,

            @Parameter(description = "Tile row index", example = "2031")
            @PathVariable int y,

            @Parameter(description = "Comma-separated snapshot IDs from the /history/snapshots metadata response", example = "7184,7194,7204", required = true)
            @RequestParam String snapshots,

            @Parameter(description = "Optional zone name to filter positions to a named area", example = "cbd")
            @RequestParam(required = false) String zone) {

        Long[] snapshotIds = Arrays.stream(snapshots.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .toArray(Long[]::new);

        log.info("GET /tiles/taxis/timeline/{}/{}/{}.pbf snapshots={} zone={}", z, x, y, snapshots, zone);
        return tileService.fetchTimelineTile(snapshotIds, z, x, y, zone)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/x-protobuf")
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=300")
                        .body(bytes));
    }
}
