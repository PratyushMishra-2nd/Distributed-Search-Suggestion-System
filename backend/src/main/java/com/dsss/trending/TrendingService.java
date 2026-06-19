package com.dsss.trending;

import com.dsss.config.AppProperties;
import com.dsss.model.TrendingItem;
import com.dsss.store.SearchCountStore;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recency-aware trending ranking.
 *
 * Each submitted search contributes 1.0 to its query's score, but older
 * contributions decay exponentially with a configurable half-life:
 *
 *     score(now) = score(last) * 2^(-(now - last) / halfLife) + 1
 *
 * Decaying lazily (only when a query is touched or read) keeps it O(1) per
 * submission. We expose two signals so the UI/viva can contrast the approaches:
 *   - windowCount  : raw number of recent submissions (the "basic" ranking)
 *   - recencyScore : decayed score (the "enhanced" ranking)
 *
 * Why recency matters (spec §7): a query searched heavily for years would
 * permanently dominate a raw-count ranking; decay lets a query that is hot
 * *right now* surface above a historically-popular but currently-quiet one.
 */
@Component
public class TrendingService {

    private static final class Stat {
        double score;
        long windowCount;
        long lastUpdateMs;
    }

    private final Map<String, Stat> stats = new ConcurrentHashMap<>();
    private final double halfLifeMs;

    public TrendingService(AppProperties props) {
        this.halfLifeMs = props.getTrending().getHalfLifeSec() * 1000.0;
    }

    /** Record {@code delta} fresh submissions of a query (called on batch flush). */
    public void record(String query, long delta, long nowMs) {
        String key = SearchCountStore.normalize(query);
        if (key.isEmpty() || delta <= 0) {
            return;
        }
        stats.compute(key, (k, s) -> {
            if (s == null) {
                s = new Stat();
                s.lastUpdateMs = nowMs;
            }
            s.score = decay(s.score, s.lastUpdateMs, nowMs) + delta;
            s.windowCount += delta;
            s.lastUpdateMs = nowMs;
            return s;
        });
    }

    /** Top {@code n} trending queries by decayed recency score. */
    public List<TrendingItem> top(int n, long nowMs) {
        return stats.entrySet().stream()
                .map(e -> {
                    Stat s = e.getValue();
                    double live = decay(s.score, s.lastUpdateMs, nowMs);
                    return new TrendingItem(e.getKey(), s.windowCount, live);
                })
                .sorted(Comparator.comparingDouble(TrendingItem::recencyScore).reversed())
                .limit(n)
                .toList();
    }

    /** Top {@code n} by raw recent count — the "basic" ranking for comparison. */
    public List<TrendingItem> topBasic(int n, long nowMs) {
        return stats.entrySet().stream()
                .map(e -> {
                    Stat s = e.getValue();
                    double live = decay(s.score, s.lastUpdateMs, nowMs);
                    return new TrendingItem(e.getKey(), s.windowCount, live);
                })
                .sorted(Comparator.comparingLong(TrendingItem::windowCount).reversed())
                .limit(n)
                .toList();
    }

    private double decay(double score, long lastMs, long nowMs) {
        long dt = Math.max(0, nowMs - lastMs);
        return score * Math.pow(2.0, -dt / halfLifeMs);
    }
}
