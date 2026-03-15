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

@Slf4j
@Tag(name = "Vector Tiles", description = "Mapbox Vector Tile (MVT) endpoints for taxi positions")
@RestController
@RequestMapping("/api/v1/tiles")
@RequiredArgsConstructor
public class TileController {

    private final TileService tileService;

    /**
     * 4.7.2 Snapshot Tile Endpoint (MVT)
     * Returns taxi positions for a snapshot as a binary Mapbox Vector Tile.
     * Tile coordinates follow the standard slippy map convention.
     */
    @Operation(
        summary = "Taxi positions as MVT tile",
        description = "Returns taxi positions for a given snapshot as a binary Mapbox Vector Tile (MVT). " +
                      "The z/x/y parameters follow the standard slippy map tile convention — " +
                      "Mapbox GL JS computes these automatically from the user's viewport. " +
                      "The MVT layer name is 'taxis'. Tiles are immutable per snapshot (Cache-Control: max-age=300)."
    )
    @GetMapping(value = "/taxis/{snapshotId}/{z}/{x}/{y}.pbf", produces = "application/x-protobuf")
    public Mono<ResponseEntity<byte[]>> getTile(
            @Parameter(description = "Snapshot ID from the /history/snapshots metadata response", example = "1001")
            @PathVariable long snapshotId,

            @Parameter(description = "Tile zoom level (0–22)", example = "12")
            @PathVariable int z,

            @Parameter(description = "Tile column index", example = "3254")
            @PathVariable int x,

            @Parameter(description = "Tile row index", example = "2031")
            @PathVariable int y,

            @Parameter(description = "Optional zone name to filter positions to a named area", example = "cbd")
            @RequestParam(required = false) String zone) {
        log.info("GET /tiles/taxis/{}/{}/{}/{}.pbf zone={}", snapshotId, z, x, y, zone);
        return tileService.fetchTile(snapshotId, z, x, y, zone)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/x-protobuf")
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=300")
                        .body(bytes));
    }
}
