package com.gov.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.ZoneSeedEntry;
import com.gov.app.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads zone data from the OneMap Planning Areas API and inserts it into the zones table.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ZoneSeedService {

    private final ZoneRepository zoneRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${zone.seed.onemap.planning-areas-url:https://www.onemap.gov.sg/api/public/popapi/getAllPlanningarea}")
    private String onemapUrl;

    @Value("${zone.seed.onemap.token:}")
    private String onemapToken;

    /** Entry point called by ZoneDataInitializer. Seeds zones if table is empty. */
    public Mono<Void> seedIfEmpty() {
        return zoneRepository.hasAnyZone()
                .flatMap(hasData -> {
                    if (hasData) {
                        log.info("zones table already populated, skipping seed");
                        return Mono.empty();
                    }
                    return loadAndInsert();
                });
    }

    private Mono<Void> loadAndInsert() {
        return fetchFromOnemap()
                .flatMapMany(zoneRepository::batchInsert)
                .doOnNext(n -> log.debug("inserted {} zone row(s)", n))
                .then()
                .doOnSuccess(v -> log.info("zone seed complete"));
    }

    private Mono<List<ZoneSeedEntry>> fetchFromOnemap() {
        WebClient client = webClientBuilder.build();
        var requestSpec = client.get().uri(onemapUrl);
        if (onemapToken != null && !onemapToken.isBlank()) {
            requestSpec = requestSpec.header("Authorization", onemapToken);
        }
        return requestSpec
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(ex -> log.error("OneMap HTTP request failed: {}", ex.getMessage()))
                .map(this::parseOneMapDistricts)
                .doOnError(ex -> log.error("OneMap response parsing failed: {}", ex.getMessage()));
    }

    private List<ZoneSeedEntry> parseOneMapDistricts(String json) {
        List<ZoneSeedEntry> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            // popapi/getAllPlanningarea returns:
            // {"SearchResults": [{"pln_area_n": "BEDOK", "geojson": "<MultiPolygon geometry string>"}, ...]}
            JsonNode searchResults = root.path("SearchResults");
            if (!searchResults.isArray()) {
                log.warn("Unexpected OneMap response structure — no SearchResults array found");
                return entries;
            }
            for (JsonNode item : searchResults) {
                String plnAreaN = item.path("pln_area_n").asText("").trim().toUpperCase();
                if (plnAreaN.isEmpty()) continue;
                try {
                    JsonNode geometry = objectMapper.readTree(item.path("geojson").asText());
                    String wkt = geometryToWkt(geometry);
                    entries.add(new ZoneSeedEntry(plnAreaN, "district", wkt));
                    log.debug("Loaded OneMap zone '{}'", plnAreaN);
                } catch (Exception ex) {
                    log.warn("Skipping OneMap zone '{}' due to geometry error: {}", plnAreaN, ex.getMessage());
                }
            }
            log.info("Loaded {} district zone(s) from OneMap", entries.size());
        } catch (Exception ex) {
            log.warn("Failed to parse OneMap response: {}", ex.getMessage());
        }
        return entries;
    }

    private static String geometryToWkt(JsonNode geometry) {
        if (geometry == null || geometry.isNull()) {
            throw new IllegalArgumentException("null geometry");
        }
        String type = geometry.path("type").asText();
        JsonNode coords = geometry.get("coordinates");
        return switch (type) {
            case "Polygon" -> "POLYGON((" + coordsToWkt(coords.get(0)) + "))";
            case "MultiPolygon" -> {
                // Use the exterior ring of the first polygon
                JsonNode ring = coords.get(0).get(0);
                yield "POLYGON((" + coordsToWkt(ring) + "))";
            }
            case "LineString" -> "LINESTRING(" + coordsToWkt(coords) + ")";
            case "MultiLineString" -> {
                StringBuilder sb = new StringBuilder("LINESTRING(");
                boolean first = true;
                for (JsonNode line : coords) {
                    if (!first) sb.append(", ");
                    sb.append(coordsToWkt(line));
                    first = false;
                }
                yield sb.append(")").toString();
            }
            default -> throw new IllegalArgumentException("Unsupported geometry type: " + type);
        };
    }

    private static String coordsToWkt(JsonNode coordsArray) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode pt : coordsArray) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(pt.get(0).asDouble()).append(" ").append(pt.get(1).asDouble());
        }
        return sb.toString();
    }
}
