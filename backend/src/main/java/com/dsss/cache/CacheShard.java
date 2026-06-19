package com.dsss.cache;

import com.dsss.model.Suggestion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One simulated cache node. Holds {@code prefix -> (suggestions, expiresAt)}
 * with TTL expiry and an LRU size cap. Each shard tracks its own hit/miss
 * counters so {@code /cache/debug} can show per-node behaviour.
 *
 * Access-ordered {@link LinkedHashMap} gives LRU eviction; all access is
 * synchronized on the map since cache reads mutate LRU order.
 */
public class CacheShard {

    private record CacheEntry(List<Suggestion> suggestions, long expiresAt) {
        boolean expired(long now) { return now >= expiresAt; }
    }

    private final String id;
    private final long ttlMs;
    private final Map<String, CacheEntry> map;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public CacheShard(String id, long ttlMs, int maxEntries) {
        this.id = id;
        this.ttlMs = ttlMs;
        this.map = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxEntries;
            }
        });
    }

    public String id() { return id; }

    /** Returns cached suggestions, or {@code null} on miss/expiry. */
    public List<Suggestion> get(String prefix, long now) {
        CacheEntry e = map.get(prefix);
        if (e == null || e.expired(now)) {
            if (e != null) {
                map.remove(prefix); // lazy TTL eviction
            }
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return e.suggestions();
    }

    public void put(String prefix, List<Suggestion> suggestions, long now) {
        map.put(prefix, new CacheEntry(suggestions, now + ttlMs));
    }

    public void invalidate(String prefix) {
        map.remove(prefix);
    }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public int entryCount() { return map.size(); }

    /** Snapshot of the prefix currently cached (for the debug endpoint). */
    public boolean contains(String prefix, long now) {
        CacheEntry e = map.get(prefix);
        return e != null && !e.expired(now);
    }
}
