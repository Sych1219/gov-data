package com.gov.app.repository;

import com.gov.app.domain.Zone;
import com.gov.app.domain.ZoneSeedEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ZoneRepository {

    private final DatabaseClient db;

    /** Returns true if any zone rows exist. */
    public Mono<Boolean> hasAnyZone() {
        return db.sql("SELECT COUNT(*) AS c FROM zones")
                .map(row -> row.get("c", Long.class) > 0)
                .one();
    }

    /** Returns true if a zone with the given name exists. */
    public Mono<Boolean> existsByName(String name) {
        return db.sql("SELECT 1 FROM zones WHERE name = :name LIMIT 1")
                .bind("name", name)
                .fetch().one()
                .map(r -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Inserts zones in bulk. geogWkt is WKT, e.g. "POLYGON((...))".
     * Uses ON CONFLICT DO NOTHING so restarts are safe.
     */
    public Flux<Long> batchInsert(List<ZoneSeedEntry> entries) {
        return Flux.fromIterable(entries)
                .flatMap(e -> db.sql("""
                        INSERT INTO zones (name, category, geog)
                        VALUES (:name, :category, ST_GeogFromText(:wkt))
                        ON CONFLICT (name) DO NOTHING
                        """)
                        .bind("name", e.name())
                        .bind("category", e.category())
                        .bind("wkt", e.wkt())
                        .fetch().rowsUpdated());
    }

    /** Returns all zones ordered by category then name. */
    public Flux<Zone> findAll() {
        return db.sql("SELECT id, name, category FROM zones ORDER BY category, name")
                .map(row -> Zone.builder()
                        .id(row.get("id", Integer.class))
                        .name(row.get("name", String.class))
                        .category(row.get("category", String.class))
                        .build())
                .all();
    }

    /**
     * Returns the best-matching district name whose trigram similarity to {@code input}
     * exceeds {@code threshold} (0.0–1.0). Returns empty if nothing qualifies.
     */
    public Mono<String> findBestDistrictMatch(String input, double threshold) {
        return db.sql("""
                SELECT name FROM zones
                WHERE category = 'district'
                  AND similarity(name, :q) > :threshold
                ORDER BY similarity(name, :q) DESC
                LIMIT 1
                """)
                .bind("q", input)
                .bind("threshold", threshold)
                .map(row -> row.get("name", String.class))
                .one();
    }

    /** Returns the top {@code limit} district names closest to {@code input} by trigram similarity. */
    public Flux<String> findDistrictSuggestions(String input, int limit) {
        return db.sql("""
                SELECT name FROM zones
                WHERE category = 'district'
                ORDER BY similarity(name, :q) DESC
                LIMIT :limit
                """)
                .bind("q", input)
                .bind("limit", limit)
                .map(row -> row.get("name", String.class))
                .all();
    }

    /**
     * Returns the best-matching road/highway name whose trigram similarity exceeds {@code threshold}.
     * Only searches zones with category 'road' or 'highway'.
     */
    public Mono<String> findBestRoadMatch(String input, double threshold) {
        return db.sql("""
                SELECT name FROM zones
                WHERE category IN ('road', 'highway')
                  AND similarity(name, :q) > :threshold
                ORDER BY similarity(name, :q) DESC
                LIMIT 1
                """)
                .bind("q", input)
                .bind("threshold", threshold)
                .map(row -> row.get("name", String.class))
                .one();
    }

    /** Returns the top {@code limit} road/highway names closest to {@code input} by trigram similarity. */
    public Flux<String> findRoadSuggestions(String input, int limit) {
        return db.sql("""
                SELECT name FROM zones
                WHERE category IN ('road', 'highway')
                ORDER BY similarity(name, :q) DESC
                LIMIT :limit
                """)
                .bind("q", input)
                .bind("limit", limit)
                .map(row -> row.get("name", String.class))
                .all();
    }

    /** Returns zones filtered by category. */
    public Flux<Zone> findByCategory(String category) {
        return db.sql("SELECT id, name, category FROM zones WHERE category = :category ORDER BY name")
                .bind("category", category)
                .map(row -> Zone.builder()
                        .id(row.get("id", Integer.class))
                        .name(row.get("name", String.class))
                        .category(row.get("category", String.class))
                        .build())
                .all();
    }
}
