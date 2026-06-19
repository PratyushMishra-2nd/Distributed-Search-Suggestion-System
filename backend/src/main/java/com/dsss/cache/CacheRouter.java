package com.dsss.cache;

import com.dsss.config.AppProperties;
import com.dsss.model.Suggestion;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes prefixes to cache shards using a {@link ConsistentHashRing}.
 *
 * The routing key is the prefix's "profile key" = its first N characters
 * (configurable). Using a short profile key means related prefixes such as
 * "ja", "jav", "java" land on the same shard, so a node owns a contiguous slice
 * of the keyspace and its cache stays warm for that slice.
 */
@Component
public class CacheRouter {

    private final AppProperties props;
    private ConsistentHashRing ring;
    private final Map<String, CacheShard> shards = new LinkedHashMap<>();

    public CacheRouter(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        AppProperties.Cache cfg = props.getCache();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < cfg.getShards(); i++) {
            String id = "cache-node-" + i;
            ids.add(id);
            shards.put(id, new CacheShard(id, cfg.getTtlMs(), cfg.getMaxEntriesPerShard()));
        }
        this.ring = new ConsistentHashRing(ids, cfg.getVirtualNodes());
    }

    /** The leading-chars profile key that decides shard ownership. */
    public String profileKey(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase();
        int n = Math.min(props.getCache().getProfileKeyLength(), p.length());
        return p.substring(0, n);
    }

    public CacheShard shardFor(String prefix) {
        return shards.get(ring.getShard(profileKey(prefix)));
    }

    public List<Suggestion> get(String prefix) {
        return shardFor(prefix).get(prefix, now());
    }

    public void put(String prefix, List<Suggestion> suggestions) {
        shardFor(prefix).put(prefix, suggestions, now());
    }

    /** Drop a single cached prefix from its owning shard. */
    public void invalidate(String prefix) {
        shardFor(prefix).invalidate(prefix);
    }

    public Map<String, CacheShard> shards() {
        return shards;
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
