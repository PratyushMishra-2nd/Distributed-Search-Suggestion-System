package com.dsss.store;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe prefix index over query strings.
 *
 * Each terminal node marks a complete query. Lookups walk to the prefix node
 * then collect every query in its subtree; ranking by count is done by the
 * caller (see {@link SearchCountStore#topK}). Keys are stored lower-cased so
 * matching is case-insensitive.
 */
public class Trie {

    private static final class Node {
        final ConcurrentHashMap<Character, Node> children = new ConcurrentHashMap<>();
        final AtomicBoolean terminal = new AtomicBoolean(false);
        volatile String word; // original (lower-cased) word stored at terminal
    }

    private final Node root = new Node();

    /** Insert a query. Idempotent. */
    public void insert(String query) {
        Node node = root;
        for (int i = 0; i < query.length(); i++) {
            node = node.children.computeIfAbsent(query.charAt(i), c -> new Node());
        }
        node.terminal.set(true);
        node.word = query;
    }

    /**
     * Collect up to {@code limit} complete queries that start with {@code prefix}.
     * A limit guards against pathological prefixes (e.g. "e") fanning out to the
     * entire dataset; ranking still happens on this bounded candidate set.
     */
    public List<String> collect(String prefix, int limit) {
        List<String> out = new ArrayList<>();
        Node node = root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.get(prefix.charAt(i));
            if (node == null) {
                return out; // no query has this prefix
            }
        }
        dfs(node, out, limit);
        return out;
    }

    private void dfs(Node node, List<String> out, int limit) {
        if (out.size() >= limit) {
            return;
        }
        if (node.terminal.get() && node.word != null) {
            out.add(node.word);
        }
        for (Node child : node.children.values()) {
            if (out.size() >= limit) {
                return;
            }
            dfs(child, out, limit);
        }
    }
}
