package com.dsss.cache;

import com.dsss.model.Suggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A logical cache node = one Redis logical DB on the single Redis server, via a
 * {@link JedisPool} pinned to that DB index. The consistent-hash ring routes
 * each prefix to one logical node; all logical nodes live on the same server.
 *
 * Suggestion lists are stored as JSON under {@code sugg:<prefix>} with a TTL
 * (Redis {@code SET ... EX}). Hit/miss counters are kept app-side because they
 * count our routing logic, not Redis-internal stats.
 *
 * Resilience (degrade-to-miss): every Redis call is guarded. If Redis is
 * unreachable, {@code get} returns {@code null} (so the caller recomputes from
 * the primary store) and writes are dropped — the request still succeeds.
 */
public class RedisCacheNode implements CacheNode {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheNode.class);
    private static final String PREFIX = "sugg:";

    private final String id;       // logical node id, also the ring key
    private final int db;          // Redis logical DB index this node owns
    private final int ttlSeconds;
    private final JedisPool pool;
    private final ObjectMapper mapper;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private volatile boolean degraded = false;

    public RedisCacheNode(String id, String host, int port, int db, long ttlMs, ObjectMapper mapper) {
        this.id = id;
        this.db = db;
        this.ttlSeconds = (int) Math.max(1, ttlMs / 1000);
        this.mapper = mapper;
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(32);
        cfg.setMaxIdle(8);
        // Every pooled connection is pinned to this logical node's DB index.
        this.pool = new JedisPool(cfg, new HostAndPort(host, port),
                DefaultJedisClientConfig.builder()
                        .database(db)
                        .connectionTimeoutMillis(500)
                        .socketTimeoutMillis(500)
                        .build());
    }

    /** PING the node; logs and returns reachability. Called once at startup. */
    public boolean ping() {
        try (Jedis j = pool.getResource()) {
            return "PONG".equalsIgnoreCase(j.ping());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String id() { return id; }

    @Override
    public List<Suggestion> get(String prefix) {
        try (Jedis j = pool.getResource()) {
            String json = j.get(PREFIX + prefix);
            recovered();
            if (json == null) {
                misses.incrementAndGet();
                return null;
            }
            hits.incrementAndGet();
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, Suggestion.class));
        } catch (Exception e) {
            onFailure("get", e);
            misses.incrementAndGet();
            return null; // degrade to miss
        }
    }

    @Override
    public void put(String prefix, List<Suggestion> suggestions) {
        try (Jedis j = pool.getResource()) {
            j.setex(PREFIX + prefix, ttlSeconds, mapper.writeValueAsString(suggestions));
            recovered();
        } catch (Exception e) {
            onFailure("put", e); // drop the write; next read recomputes
        }
    }

    @Override
    public void invalidate(String prefix) {
        try (Jedis j = pool.getResource()) {
            j.del(PREFIX + prefix);
            recovered();
        } catch (Exception e) {
            onFailure("invalidate", e);
        }
    }

    @Override
    public boolean contains(String prefix) {
        try (Jedis j = pool.getResource()) {
            return j.exists(PREFIX + prefix);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long entryCount() {
        try (Jedis j = pool.getResource()) {
            return j.dbSize();
        } catch (Exception e) {
            return -1; // unknown (node down)
        }
    }

    @Override public long hits() { return hits.get(); }
    @Override public long misses() { return misses.get(); }

    public void close() {
        pool.close();
    }

    /** Log a degradation once per outage rather than per request. */
    private void onFailure(String op, Exception e) {
        if (!degraded) {
            degraded = true;
            log.warn("Redis node {} unreachable on {} ({}); degrading to cache-miss",
                    id, op, e.getMessage());
        }
    }

    private void recovered() {
        if (degraded) {
            degraded = false;
            log.info("Redis node {} recovered", id);
        }
    }
}
