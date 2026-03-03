package com.gov.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.ZoneSeedEntry;
import com.gov.app.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates zone data loading from OneMap (online, authoritative) or
 * from the bundled zones-seed.json (offline fallback).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ZoneSeedService {

    private final ZoneRepository zoneRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${zone.seed.onemap.enabled:true}")
    private boolean onemapEnabled;

    @Value("${zone.seed.onemap.planning-areas-url:https://www.onemap.gov.sg/api/public/geodata/PlanningAreasGeo}")
    private String onemapUrl;

    @Value("${zone.seed.onemap.token:}")
    private String onemapToken;

    @Value("${zone.seed.fallback-file:classpath:zones-seed.json}")
    private Resource seedFile;

    /**
     * Maps OneMap planning area name (uppercase) → our canonical zone name.
     * Only district zones appear here; roads/highways always come from the seed file.
     */
    private static final Map<String, String> ONEMAP_TO_CANONICAL = Map.ofEntries(
            Map.entry("DOWNTOWN CORE", "CBD"),
            Map.entry("ORCHARD", "Orchard"),
            Map.entry("MARINA SOUTH", "Marina Bay"),
            Map.entry("CHANGI", "Changi"),
            Map.entry("TAMPINES", "Tampines"),
            Map.entry("JURONG EAST", "Jurong East"),
            Map.entry("WOODLANDS", "Woodlands"),
            Map.entry("BISHAN", "Bishan"),
            Map.entry("PUNGGOL", "Punggol"),
            Map.entry("SENTOSA", "Sentosa"),
            Map.entry("BUKIT MERAH", "Harbourfront"),
            Map.entry("NOVENA", "Novena"),
            Map.entry("TOA PAYOH", "Toa Payoh"),
            Map.entry("QUEENSTOWN", "Buona Vista")
    );

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
        Mono<List<ZoneSeedEntry>> source = onemapEnabled
                ? fetchFromOnemap().onErrorResume(ex -> {
                    log.warn("OneMap fetch failed ({}), falling back to bundled seed", ex.getMessage());
                    return loadFromFile();
                })
                : loadFromFile();

        return source
                .flatMapMany(zoneRepository::batchInsert)
                .doOnNext(n -> log.debug("inserted {} zone row(s)", n))
                .then()
                .doOnSuccess(v -> log.info("zone seed complete"));
    }

    /**
     * Fetches district polygons from OneMap and supplements with roads/highways
     * (and any unmatched districts) from the bundled seed file.
     */
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
                .doOnError(ex -> log.error("OneMap response parsing failed: {}", ex.getMessage()))
                .flatMap(onemapDistricts -> loadFromFile().map(seedEntries -> {
                    // Start with seed entries (roads, highways, and fallback districts)
                    Map<String, ZoneSeedEntry> result = new LinkedHashMap<>();
                    seedEntries.forEach(e -> result.put(e.name(), e));
                    // Override districts with OneMap data (authoritative boundaries)
                    onemapDistricts.forEach(e -> result.put(e.name(), e));
                    log.info("OneMap provided {} district zone(s); total zones: {}",
                            onemapDistricts.size(), result.size());
                    return new ArrayList<>(result.values());
                }));
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
                // String canonicalName = ONEMAP_TO_CANONICAL.get(plnAreaN);
                // if (canonicalName == null) continue;
                try {
                    JsonNode geometry = objectMapper.readTree(item.path("geojson").asText());
                    String wkt = geometryToWkt(geometry);
                    entries.add(new ZoneSeedEntry(plnAreaN, "district", wkt));
                    log.debug("Mapped OneMap '{}' → canonical zone '{}'", plnAreaN, plnAreaN);
                } catch (Exception ex) {
                    log.warn("Skipping OneMap zone '{}' due to geometry error: {}", plnAreaN, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse OneMap response: {}", ex.getMessage());
        }
        return entries;
    }

    /** Parses the bundled zones-seed.json from the classpath. */
    Mono<List<ZoneSeedEntry>> loadFromFile() {
        return Mono.fromCallable(() -> {
            String json = new String(seedFile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            List<ZoneSeedEntry> entries = new ArrayList<>();
            for (JsonNode feature : root.path("features")) {
                String name = feature.path("properties").path("name").asText();
                String category = feature.path("properties").path("category").asText();
                String wkt = geometryToWkt(feature.get("geometry"));
                entries.add(new ZoneSeedEntry(name, category, wkt));
            }
            log.debug("Loaded {} zones from seed file", entries.size());
            return entries;
        }).subscribeOn(Schedulers.boundedElastic());
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
