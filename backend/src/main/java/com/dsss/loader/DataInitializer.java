package com.dsss.loader;

import com.dsss.persistence.QueryCountRepository;
import com.dsss.store.SearchCountStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Boot-time data wiring around the durable Postgres store:
 *
 *  - ensure the schema exists.
 *  - if Postgres already has rows, it is the source of truth → rebuild the
 *    in-memory Trie from it (fast restart, no re-parsing the dataset).
 *  - if Postgres is empty (first run), parse the ORCAS dataset into the Trie
 *    and persist it to Postgres so subsequent restarts load from the DB.
 *
 * Runs after the context (incl. the Postgres DataSource) is ready.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SearchCountStore store;
    private final DatasetLoader datasetLoader;
    private final QueryCountRepository repo;

    public DataInitializer(SearchCountStore store, DatasetLoader datasetLoader,
                           QueryCountRepository repo) {
        this.store = store;
        this.datasetLoader = datasetLoader;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long start = System.currentTimeMillis();
        repo.ensureSchema();
        long existing = repo.count();

        if (existing > 0) {
            log.info("Postgres has {} queries; rebuilding in-memory index from DB", existing);
            long[] n = {0};
            repo.forEach((q, c) -> {
                store.load(q, c);
                n[0]++;
            });
            log.info("loaded {} queries from Postgres into store, {} ms",
                    n[0], System.currentTimeMillis() - start);
        } else {
            log.info("Postgres empty; seeding from dataset");
            datasetLoader.loadIntoStore();
            persistStoreToPostgres();
            log.info("seed complete: {} distinct queries persisted to Postgres, {} ms",
                    store.size(), System.currentTimeMillis() - start);
        }
    }

    /** Bulk-write the freshly-loaded in-memory store into Postgres in batches. */
    private void persistStoreToPostgres() {
        Map<String, Long> buf = new HashMap<>(20_000);
        long[] total = {0};
        store.forEach((q, c) -> {
            buf.put(q, c);
            if (buf.size() >= 20_000) {
                repo.incrementBatch(buf);
                total[0] += buf.size();
                buf.clear();
                if (total[0] % 200_000 == 0) {
                    log.info("seeded {} rows to Postgres…", total[0]);
                }
            }
        });
        if (!buf.isEmpty()) {
            repo.incrementBatch(buf);
        }
    }
}
