package com.gov.app.repository;

import com.gov.app.domain.Zone;
import com.gov.app.domain.ZoneSeedEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ZoneRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Returns true if any zone rows exist. */
    public boolean hasAnyZone() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) AS c FROM zones", new MapSqlParameterSource(), Long.class);
        return count != null && count > 0;
    }

    /** Returns true if a zone with the given name exists. */
    public boolean existsByName(String name) {
        List<Integer> rows = jdbc.query(
                "SELECT 1 FROM zones WHERE name = :name LIMIT 1",
                new MapSqlParameterSource("name", name),
                (rs, i) -> 1);
        return !rows.isEmpty();
    }

    /**
     * Inserts zones in bulk. geogWkt is WKT, e.g. "POLYGON((...))".
     * Uses ON CONFLICT DO NOTHING so restarts are safe.
     */
    public void batchInsert(List<ZoneSeedEntry> entries) {
        String sql = """
                INSERT INTO zones (name, category, geog)
                VALUES (:name, :category, ST_GeogFromText(:wkt))
                ON CONFLICT (name) DO NOTHING
                """;
        MapSqlParameterSource[] batchParams = entries.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("name", e.name())
                        .addValue("category", e.category())
                        .addValue("wkt", e.wkt()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batchParams);
    }

    /** Returns all zones ordered by category then name. */
    public List<Zone> findAll() {
        return jdbc.query(
                "SELECT id, name, category FROM zones ORDER BY category, name",
                new MapSqlParameterSource(),
                (rs, i) -> Zone.builder()
                        .id(rs.getInt("id"))
                        .name(rs.getString("name"))
                        .category(rs.getString("category"))
                        .build());
    }

    /**
     * Returns the best-matching district name whose trigram similarity to {@code input}
     * exceeds {@code threshold} (0.0–1.0). Returns empty if nothing qualifies.
     */
    public Optional<String> findBestDistrictMatch(String input, double threshold) {
        String sql = """
                SELECT name FROM zones
                WHERE category = 'district'
                  AND similarity(name, :q) > :threshold
                ORDER BY similarity(name, :q) DESC
                LIMIT 1
                """;
        List<String> results = jdbc.query(sql,
                new MapSqlParameterSource().addValue("q", input).addValue("threshold", threshold),
                (rs, i) -> rs.getString("name"));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Returns the top {@code limit} district names closest to {@code input} by trigram similarity. */
    public List<String> findDistrictSuggestions(String input, int limit) {
        String sql = """
                SELECT name FROM zones
                WHERE category = 'district'
                ORDER BY similarity(name, :q) DESC
                LIMIT :limit
                """;
        return jdbc.query(sql,
                new MapSqlParameterSource().addValue("q", input).addValue("limit", limit),
                (rs, i) -> rs.getString("name"));
    }

    /**
     * Returns the best-matching road/highway name whose trigram similarity exceeds {@code threshold}.
     * Only searches zones with category 'road' or 'highway'.
     */
    public Optional<String> findBestRoadMatch(String input, double threshold) {
        String sql = """
                SELECT name FROM zones
                WHERE category IN ('road', 'highway')
                  AND similarity(name, :q) > :threshold
                ORDER BY similarity(name, :q) DESC
                LIMIT 1
                """;
        List<String> results = jdbc.query(sql,
                new MapSqlParameterSource().addValue("q", input).addValue("threshold", threshold),
                (rs, i) -> rs.getString("name"));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns the best-matching road/highway Zone (with name and category) whose
     * trigram similarity exceeds {@code threshold}. Used to populate RoadContext.
     */
    public Optional<Zone> findBestRoadZone(String input, double threshold) {
        String sql = """
                SELECT name, category FROM zones
                WHERE category IN ('road', 'highway')
                  AND similarity(name, :q) > :threshold
                ORDER BY similarity(name, :q) DESC
                LIMIT 1
                """;
        List<Zone> results = jdbc.query(sql,
                new MapSqlParameterSource().addValue("q", input).addValue("threshold", threshold),
                (rs, i) -> Zone.builder()
                        .name(rs.getString("name"))
                        .category(rs.getString("category"))
                        .build());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Returns the top {@code limit} road/highway names closest to {@code input} by trigram similarity. */
    public List<String> findRoadSuggestions(String input, int limit) {
        String sql = """
                SELECT name FROM zones
                WHERE category IN ('road', 'highway')
                ORDER BY similarity(name, :q) DESC
                LIMIT :limit
                """;
        return jdbc.query(sql,
                new MapSqlParameterSource().addValue("q", input).addValue("limit", limit),
                (rs, i) -> rs.getString("name"));
    }

    /**
     * Returns the best-matching zone (any category) whose trigram similarity exceeds {@code threshold}.
     * Useful for resolving an unknown name before deciding which endpoint to call.
     */
    public Optional<Zone> findBestAnyMatch(String input, double threshold) {
        String sql = """
                SELECT name, category FROM zones
                WHERE similarity(name, :q) > :threshold
                ORDER BY similarity(name, :q) DESC
                LIMIT 1
                """;
        List<Zone> results = jdbc.query(sql,
                new MapSqlParameterSource().addValue("q", input).addValue("threshold", threshold),
                (rs, i) -> Zone.builder()
                        .name(rs.getString("name"))
                        .category(rs.getString("category"))
                        .build());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns the geometry of a zone as a GeoJSON string (via ST_AsGeoJSON) along with its category.
     * Works for any category: district (Polygon), road/highway (LineString).
     */
    public Optional<ZoneGeometryJson> findGeometryByName(String name) {
        String sql = """
                SELECT name, category, ST_AsGeoJSON(geog)::text AS geom_json
                FROM zones
                WHERE name = :name
                """;
        List<ZoneGeometryJson> results = jdbc.query(sql,
                new MapSqlParameterSource("name", name),
                (rs, i) -> new ZoneGeometryJson(
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("geom_json")));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Raw geometry result from PostGIS, before JSON parsing. */
    public record ZoneGeometryJson(String name, String category, String geometryJson) {}

    /** Returns zones filtered by category. */
    public List<Zone> findByCategory(String category) {
        return jdbc.query(
                "SELECT id, name, category FROM zones WHERE category = :category ORDER BY name",
                new MapSqlParameterSource("category", category),
                (rs, i) -> Zone.builder()
                        .id(rs.getInt("id"))
                        .name(rs.getString("name"))
                        .category(rs.getString("category"))
                        .build());
    }
}
