package com.dsss.cache;

import com.dsss.config.AppProperties;
import com.dsss.model.Suggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes prefixes to logical cache nodes using a {@link ConsistentHashRing}.
 *
 * There are N logical nodes (one Redis logical DB each) on a single Redis
 * server. The routing key is the prefix's "profile key" = its first N
 * characters, so related prefixes ("ja", "jav", "java") land on the same
 * logical node and keep its slice of the keyspace warm. This is client-side
 * consistent hashing — adding or removing a logical node only remaps the keys
 * on the adjacent ring arcs. One server ultimately serves every request.
 *
 * Depends on {@link EmbeddedRedisManager} so the Redis server is running before
 * the Jedis pools connect.
 */
@Component
public class CacheRouter {

    private static final Logger log = LoggerFactory.getLogger(CacheRouter.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    @SuppressWarnings("unused") // injected only to enforce startup ordering
    private final EmbeddedRedisManager embeddedRedis;

    private ConsistentHashRing ring;
    private final Map<String, CacheNode> nodes = new LinkedHashMap<>();

    public CacheRouter(AppProperties props, ObjectMapper mapper, EmbeddedRedisManager embeddedRedis) {
        this.props = props;
        this.mapper = mapper;
        this.embeddedRedis = embeddedRedis;
    }

    @PostConstruct
    void init() {
        AppProperties.Cache cfg = props.getCache();

        if (cfg.getNodes() != null && !cfg.getNodes().isEmpty()) {
            // Physical mode: each endpoint is a separate Redis server (e.g. the
            // Docker containers). One cache node per server, DB 0.
            for (String endpoint : cfg.getNodes()) {
                int idx = endpoint.lastIndexOf(':');
                String host = endpoint.substring(0, idx);
                int port = Integer.parseInt(endpoint.substring(idx + 1).trim());
                RedisCacheNode node = new RedisCacheNode(endpoint, host, port, 0, cfg.getTtlMs(), mapper);
                nodes.put(endpoint, node);
                log.info("cache node {} (physical) {}", endpoint,
                        node.ping() ? "UP" : "DOWN (will degrade to miss)");
            }
        } else {
            // Logical mode: one server, N logical nodes = Redis DBs 0..N-1.
            String server = cfg.getServer();
            int idx = server.lastIndexOf(':');
            String host = server.substring(0, idx);
            int port = Integer.parseInt(server.substring(idx + 1).trim());
            for (int db = 0; db < cfg.getLogicalNodes(); db++) {
                String id = "logical-node-" + db;
                RedisCacheNode node = new RedisCacheNode(id, host, port, db, cfg.getTtlMs(), mapper);
                nodes.put(id, node);
                log.info("{} -> {} db{} {}", id, server, db,
                        node.ping() ? "UP" : "DOWN (will degrade to miss)");
            }
        }
        this.ring = new ConsistentHashRing(nodes.keySet(), cfg.getVirtualNodes());
    }

    /** The leading-chars profile key that decides node ownership. */
    public String profileKey(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase();
        int n = Math.min(props.getCache().getProfileKeyLength(), p.length());
        return p.substring(0, n);
    }

    public CacheNode shardFor(String prefix) {
        return nodes.get(ring.getShard(profileKey(prefix)));
    }

    public List<Suggestion> get(String prefix) {
        return shardFor(prefix).get(prefix);
    }

    public void put(String prefix, List<Suggestion> suggestions) {
        shardFor(prefix).put(prefix, suggestions);
    }

    /** Drop a single cached prefix from its owning node. */
    public void invalidate(String prefix) {
        shardFor(prefix).invalidate(prefix);
    }

    public Map<String, CacheNode> shards() {
        return nodes;
    }

    @PreDestroy
    void shutdown() {
        nodes.values().forEach(n -> {
            if (n instanceof RedisCacheNode r) {
                r.close();
            }
        });
    }
}
