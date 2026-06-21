package com.dsss.batch;

import com.dsss.cache.CacheRouter;
import com.dsss.config.AppProperties;
import com.dsss.persistence.QueryCountRepository;
import com.dsss.store.SearchCountStore;
import com.dsss.trending.TrendingService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffers search submissions and writes aggregated counts to the primary store
 * periodically, instead of one write per search (spec §8).
 *
 * Why: under typeahead traffic the same queries are submitted repeatedly in
 * short bursts. Collapsing N submissions of a query into a single
 * {@code +N} store write sharply reduces write amplification on the primary
 * store. The trade-off — counts are eventually consistent: a just-submitted
 * query is not reflected in suggestions until the next flush.
 *
 * Flush triggers: buffer reaches {@code batch.size}, the scheduled interval
 * fires, or the app shuts down (so nothing is lost).
 */
@Component
public class BatchWriter {

    private static final Logger log = LoggerFactory.getLogger(BatchWriter.class);

    private final SearchCountStore store;
    private final CacheRouter cacheRouter;
    private final TrendingService trending;
    private final QueryCountRepository repo;
    private final int batchSize;

    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final ReentrantLock flushLock = new ReentrantLock();

    // ---- metrics for the performance report ----
    private final AtomicLong totalSubmissions = new AtomicLong();
    private final AtomicLong totalStoreWrites = new AtomicLong(); // distinct (query) writes applied
    private final AtomicLong flushCount = new AtomicLong();

    public BatchWriter(SearchCountStore store, CacheRouter cacheRouter,
                       TrendingService trending, QueryCountRepository repo,
                       AppProperties props) {
        this.store = store;
        this.cacheRouter = cacheRouter;
        this.trending = trending;
        this.repo = repo;
        this.batchSize = props.getBatch().getSize();
    }

    /** Enqueue a submitted search. Non-blocking; the caller returns immediately. */
    public void submit(String query) {
        String key = SearchCountStore.normalize(query);
        if (key.isEmpty()) {
            return;
        }
        buffer.add(key);
        totalSubmissions.incrementAndGet();
        if (pending.incrementAndGet() >= batchSize) {
            flush(); // size-triggered flush
        }
    }

    @Scheduled(fixedDelayString = "${app.batch.flush-interval-ms}")
    public void scheduledFlush() {
        flush();
    }

    /**
     * Drain the buffer, aggregate per query, and apply one write per distinct
     * query. Single-threaded via {@code flushLock}; concurrent triggers coalesce.
     */
    public void flush() {
        if (!flushLock.tryLock()) {
            return; // another flush in progress; it will drain what we added
        }
        try {
            Map<String, Long> agg = new HashMap<>();
            String q;
            int drained = 0;
            while ((q = buffer.poll()) != null) {
                agg.merge(q, 1L, Long::sum);
                drained++;
            }
            if (agg.isEmpty()) {
                return;
            }
            pending.addAndGet(-drained);
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> e : agg.entrySet()) {
                store.increment(e.getKey(), e.getValue());
                trending.record(e.getKey(), e.getValue(), now);
                // counts changed -> drop any stale cached prefixes of this query
                invalidatePrefixes(e.getKey());
            }
            // Durable write: one aggregated upsert batch to Postgres. Guarded so
            // a DB hiccup never loses the in-memory update or fails the flush.
            try {
                repo.incrementBatch(agg);
            } catch (Exception ex) {
                log.warn("postgres flush failed ({} queries): {}", agg.size(), ex.getMessage());
            }
            totalStoreWrites.addAndGet(agg.size());
            flushCount.incrementAndGet();
            log.info("flush: {} submissions -> {} store writes (reduction {}%); buffer drained={}",
                    drained, agg.size(), reductionPct(drained, agg.size()), drained);
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * Invalidate every cached prefix of the changed query so the next /suggest
     * recomputes ranking. We invalidate all prefixes of the query string; the
     * owning shard drops only the ones it holds.
     */
    private void invalidatePrefixes(String query) {
        for (int i = 1; i <= query.length(); i++) {
            cacheRouter.invalidate(query.substring(0, i));
        }
    }

    private long reductionPct(int submissions, int writes) {
        return submissions == 0 ? 0 : Math.round((1.0 - (double) writes / submissions) * 100);
    }

    @PreDestroy
    public void onShutdown() {
        log.info("shutdown: final flush of {} pending submissions", pending.get());
        flush();
    }

    // ---- exposed for /metrics-style reporting ----
    public long totalSubmissions() { return totalSubmissions.get(); }
    public long totalStoreWrites() { return totalStoreWrites.get(); }
    public long flushCount() { return flushCount.get(); }
    public int pending() { return pending.get(); }

    public double overallReductionRatio() {
        long s = totalSubmissions.get();
        long w = totalStoreWrites.get();
        return s == 0 ? 0.0 : 1.0 - (double) w / s;
    }
}
