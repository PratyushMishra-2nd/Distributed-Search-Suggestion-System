package com.dsss.api;

import com.dsss.cache.CacheNode;
import com.dsss.cache.CacheRouter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the cache internals so the consistent-hashing behaviour is visible
 * (spec §6 / §9 debug routing). {@code GET /cache/debug?prefix=<p>} reports
 * which shard owns the prefix, whether it is currently cached, and per-shard
 * hit/miss counters.
 */
@RestController
public class DebugController {

    private final CacheRouter cache;

    public DebugController(CacheRouter cache) {
        this.cache = cache;
    }

    @GetMapping("/cache/debug")
    public Map<String, Object> debug(@RequestParam(name = "prefix", defaultValue = "") String prefix) {
        Map<String, Object> out = new LinkedHashMap<>();

        if (!prefix.isBlank()) {
            CacheNode owner = cache.shardFor(prefix);
            Map<String, Object> routing = new LinkedHashMap<>();
            routing.put("prefix", prefix);
            routing.put("profileKey", cache.profileKey(prefix));
            routing.put("ownerShard", owner.id());
            routing.put("cached", owner.contains(prefix));
            out.put("routing", routing);
        }

        List<Map<String, Object>> shardStats = new ArrayList<>();
        for (CacheNode s : cache.shards().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shard", s.id());
            m.put("entries", s.entryCount());
            m.put("hits", s.hits());
            m.put("misses", s.misses());
            long total = s.hits() + s.misses();
            m.put("hitRate", total == 0 ? 0.0 : Math.round((double) s.hits() / total * 1000) / 1000.0);
            shardStats.add(m);
        }
        out.put("shards", shardStats);
        return out;
    }
}
