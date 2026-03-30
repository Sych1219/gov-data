package com.gov.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.ZoneSeedEntry;
import com.gov.app.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads zone data from:
 * - OneMap Planning Areas API  → district polygons
 * - OSM Overpass API           → road/highway LineStrings (tag-based discovery)
 *
 * All results are batch-inserted into the zones table at startup.
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

    @Value("${zone.seed.overpass.url:https://overpass-api.de/api/interpreter}")
    private String overpassUrl;

    @Value("${zone.seed.overpass.bounding-box:1.1,103.5,1.5,104.1}")
    private String overpassBbox;

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

    /**
     * Fetches districts (OneMap) and roads/highways (Overpass) in parallel,
     * merges the results, and batch-inserts into the zones table.
     */
    private Mono<Void> loadAndInsert() {
        return Mono.zip(fetchFromOnemap(), fetchFromOverpass())
                .map(t -> {
                    List<ZoneSeedEntry> all = new ArrayList<>(t.getT1());
                    all.addAll(t.getT2());
                    return all;
                })
                .flatMapMany(zoneRepository::batchInsert)
                .doOnNext(n -> log.debug("inserted {} zone row(s)", n))
                .then()
                .doOnSuccess(v -> log.info("zone seed complete"));
    }

    // -------------------------------------------------------------------------
    // OneMap — district polygons
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Overpass — road/highway LineStrings (dynamic tag-based discovery)
    // -------------------------------------------------------------------------

    /**
     * Fetches all named expressways (highway=motorway) and arterial roads
     * (highway=trunk|primary) from OSM Overpass API in parallel.
     * On error: logs warning and returns empty list so district seeding is not blocked.
     */
    private Mono<List<ZoneSeedEntry>> fetchFromOverpass() {
        Mono<List<ZoneSeedEntry>> highways = fetchOverpassByType(
                "[out:json];way[\"highway\"=\"motorway\"][\"name\"](" + overpassBbox + ");out geom;",
                "highway");
        Mono<List<ZoneSeedEntry>> roads = fetchOverpassByType(
                "[out:json];way[\"highway\"~\"trunk|primary\"][\"name\"](" + overpassBbox + ");out geom;",
                "road");
        return Mono.zip(highways, roads)
                .map(t -> {
                    List<ZoneSeedEntry> all = new ArrayList<>(t.getT1());
                    all.addAll(t.getT2());
                    return all;
                })
                .onErrorResume(ex -> {
                    log.warn("Overpass fetch failed: {}", ex.getMessage());
                    return Mono.just(List.of());
                });
    }

    /**
     * POSTs one Overpass QL query, groups the returned ways by tags.name,
     * and builds one MULTILINESTRING WKT per named road.
     */
    private Mono<List<ZoneSeedEntry>> fetchOverpassByType(String ql, String category) {
        String body = "data=" + ql;
        return webClientBuilder.build()
                .post()
                .uri(overpassUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> parseOverpassResponse(json, category))
                .doOnError(ex -> log.warn("Overpass query for category '{}' failed: {}", category, ex.getMessage()))
                .onErrorReturn(List.of());
    }

    /**
     * Parses an Overpass JSON response.
     * Ways sharing the same tags.name are grouped; their coordinate sequences
     * are combined into a single MULTILINESTRING WKT zone entry.
     *
     * Overpass returns {lat, lon}; WKT requires (lon lat) — coordinates are swapped.
     */
    private List<ZoneSeedEntry> parseOverpassResponse(String json, String category) {
        Map<String, List<List<double[]>>> waysByName = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode elements = root.path("elements");
            if (!elements.isArray()) {
                log.warn("Overpass response missing 'elements' array for category '{}'", category);
                return List.of();
            }
            for (JsonNode element : elements) {
                if (!"way".equals(element.path("type").asText())) continue;
                String name = element.path("tags").path("name").asText("").trim();
                if (name.isEmpty()) continue;
                JsonNode geometry = element.path("geometry");
                if (!geometry.isArray() || geometry.size() < 2) continue;

                List<double[]> coords = new ArrayList<>();
                for (JsonNode pt : geometry) {
                    double lat = pt.path("lat").asDouble();
                    double lon = pt.path("lon").asDouble();
                    coords.add(new double[]{lon, lat}); // WKT order: lon lat
                }
                waysByName.computeIfAbsent(name, k -> new ArrayList<>()).add(coords);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse Overpass response for category '{}': {}", category, ex.getMessage());
            return List.of();
        }

        List<ZoneSeedEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<List<double[]>>> entry : waysByName.entrySet()) {
            String name = entry.getKey();
            String wkt = buildMultiLineStringWkt(entry.getValue());
            entries.add(new ZoneSeedEntry(name, category, wkt));
            log.debug("Loaded Overpass {} '{}' ({} way segment(s))", category, name, entry.getValue().size());
        }
        log.info("Loaded {} {}(s) from Overpass", entries.size(), category);
        return entries;
    }

    private static String buildMultiLineStringWkt(List<List<double[]>> ways) {
        StringBuilder sb = new StringBuilder("MULTILINESTRING(");
        boolean firstWay = true;
        for (List<double[]> way : ways) {
            if (!firstWay) sb.append(",");
            sb.append("(");
            boolean firstPt = true;
            for (double[] pt : way) {
                if (!firstPt) sb.append(",");
                sb.append(pt[0]).append(" ").append(pt[1]); // lon lat
                firstPt = false;
            }
            sb.append(")");
            firstWay = false;
        }
        sb.append(")");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Geometry helpers (used by OneMap district parsing)
    // -------------------------------------------------------------------------

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
