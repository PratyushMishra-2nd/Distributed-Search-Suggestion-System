package com.dsss.store;

import com.dsss.model.Suggestion;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The "primary store" of the system: the authoritative query -> search-count
 * map plus a {@link Trie} prefix index. In a real distributed system this would
 * be a database; here it is in-memory but treated as the source of truth that
 * the batch writer protects from excessive writes.
 */
@Component
public class SearchCountStore {

    /** Upper bound on candidates pulled from the trie before ranking. */
    private static final int MAX_CANDIDATES = 5000;

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final Trie trie = new Trie();

    /** Bulk-load a query with an initial count (used by the dataset loader). */
    public void load(String query, long initialCount) {
        String key = normalize(query);
        if (key.isEmpty()) {
            return;
        }
        counts.computeIfAbsent(key, k -> { trie.insert(k); return new AtomicLong(0); })
              .addAndGet(initialCount);
    }

    /**
     * Increment a query's count by {@code delta}, inserting it into the trie on
     * first sight. Returns the new total. Called by the batch writer on flush.
     */
    public long increment(String query, long delta) {
        String key = normalize(query);
        if (key.isEmpty()) {
            return 0;
        }
        return counts.computeIfAbsent(key, k -> { trie.insert(k); return new AtomicLong(0); })
                     .addAndGet(delta);
    }

    public long countOf(String query) {
        AtomicLong v = counts.get(normalize(query));
        return v == null ? 0 : v.get();
    }

    public int size() {
        return counts.size();
    }

    /**
     * Top-{@code k} suggestions for a prefix, sorted by count descending.
     * Empty/blank prefix -> empty list. No match -> empty list.
     */
    public List<Suggestion> topK(String prefix, int k) {
        String p = normalize(prefix);
        if (p.isEmpty() || k <= 0) {
            return List.of();
        }
        List<String> candidates = trie.collect(p, MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return List.of();
        }
        // min-heap of size k keyed by count -> keeps the k highest counts.
        PriorityQueue<Suggestion> heap =
                new PriorityQueue<>(Comparator.comparingLong(Suggestion::count));
        for (String q : candidates) {
            long c = countOf(q);
            if (heap.size() < k) {
                heap.offer(new Suggestion(q, c));
            } else if (c > heap.peek().count()) {
                heap.poll();
                heap.offer(new Suggestion(q, c));
            }
        }
        return heap.stream()
                   .sorted(Comparator.comparingLong(Suggestion::count).reversed()
                           .thenComparing(Suggestion::query))
                   .toList();
    }

    /** Lower-case + trim; the one place query normalization is defined. */
    public static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
