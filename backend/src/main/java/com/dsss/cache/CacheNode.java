package com.dsss.cache;

import com.dsss.model.Suggestion;

import java.util.List;

/**
 * One cache node in the ring. Backed by a real Redis instance
 * ({@link RedisCacheNode}); the interface keeps {@link CacheRouter} and the
 * debug endpoint independent of the backing store.
 */
public interface CacheNode {

    String id();

    /** Cached suggestions for a prefix, or {@code null} on miss / node failure. */
    List<Suggestion> get(String prefix);

    /** Cache a prefix's suggestions with the node's TTL. */
    void put(String prefix, List<Suggestion> suggestions);

    /** Drop a single cached prefix. */
    void invalidate(String prefix);

    /** Whether the prefix is currently cached (for the debug endpoint). */
    boolean contains(String prefix);

    long hits();

    long misses();

    /** Number of keys held by this node (best-effort). */
    long entryCount();
}
