package com.dsss.model;

/**
 * A trending query with both ranking signals exposed so the two approaches
 * required by the spec (basic count vs. recency-aware score) can be compared.
 */
public record TrendingItem(String query, long windowCount, double recencyScore) {
}
