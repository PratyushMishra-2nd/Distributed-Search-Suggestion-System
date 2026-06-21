package com.dsss.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * Durable store for query -> search-count in PostgreSQL. This is the source of
 * truth that survives restarts; the in-memory Trie is rebuilt from it on boot.
 */
@Repository
public class QueryCountRepository {

    private final JdbcTemplate jdbc;
    private final int batchSize;

    public QueryCountRepository(JdbcTemplate jdbc,
                                com.dsss.config.AppProperties props) {
        this.jdbc = jdbc;
        this.batchSize = props.getDb().getSeedBatchSize();
    }

    public void ensureSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS query_counts (
                query VARCHAR(160) PRIMARY KEY,
                cnt   BIGINT NOT NULL
            )
            """);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM query_counts", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Apply count deltas durably. Upsert: insert new queries, add to existing.
     * Used both for first-run seeding and for every batch flush.
     */
    public void incrementBatch(Map<String, Long> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        final String sql = """
            INSERT INTO query_counts (query, cnt) VALUES (?, ?)
            ON CONFLICT (query) DO UPDATE SET cnt = query_counts.cnt + EXCLUDED.cnt
            """;
        List<Object[]> batch = new ArrayList<>(batchSize);
        for (Map.Entry<String, Long> e : deltas.entrySet()) {
            batch.add(new Object[]{e.getKey(), e.getValue()});
            if (batch.size() >= batchSize) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }
    }

    /**
     * Feed every row to the consumer without materializing the whole table,
     * using keyset pagination on the primary key (memory-safe, no streaming
     * cursor required). Used at boot to rebuild the in-memory Trie from Postgres.
     */
    public void forEach(ObjLongConsumer<String> consumer) {
        final String sql = """
            SELECT query, cnt FROM query_counts
            WHERE query > ? ORDER BY query LIMIT ?
            """;
        String last = "";
        while (true) {
            final String cursor = last;
            List<String[]> page = jdbc.query(sql,
                    (rs, i) -> new String[]{rs.getString(1), Long.toString(rs.getLong(2))},
                    cursor, batchSize);
            if (page.isEmpty()) {
                break;
            }
            for (String[] row : page) {
                consumer.accept(row[0], Long.parseLong(row[1]));
            }
            last = page.get(page.size() - 1)[0];
            if (page.size() < batchSize) {
                break;
            }
        }
    }
}
