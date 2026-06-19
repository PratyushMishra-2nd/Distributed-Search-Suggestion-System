package com.dsss.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Classic consistent-hash ring with virtual nodes.
 *
 * Each physical shard is placed at {@code virtualNodes} positions on a 64-bit
 * ring (hashed from "shardId#i"). A key is routed to the first shard clockwise
 * from the key's hash. Virtual nodes keep the key distribution even and mean
 * that adding/removing a shard only remaps keys near the affected arcs rather
 * than reshuffling everything (the property we want to demonstrate).
 */
public class ConsistentHashRing {

    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodes;

    public ConsistentHashRing(Collection<String> shardIds, int virtualNodes) {
        this.virtualNodes = virtualNodes;
        for (String shardId : shardIds) {
            addShard(shardId);
        }
    }

    public void addShard(String shardId) {
        for (int i = 0; i < virtualNodes; i++) {
            ring.put(hash(shardId + "#" + i), shardId);
        }
    }

    public void removeShard(String shardId) {
        for (int i = 0; i < virtualNodes; i++) {
            ring.remove(hash(shardId + "#" + i));
        }
    }

    /** Shard that owns {@code key} (first virtual node clockwise). */
    public String getShard(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("ring has no shards");
        }
        long h = hash(key);
        SortedMap<Long, String> tail = ring.tailMap(h);
        Long pos = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(pos);
    }

    /** MD5-derived 64-bit hash; deterministic across runs. */
    static long hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (d[i] & 0xff);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
