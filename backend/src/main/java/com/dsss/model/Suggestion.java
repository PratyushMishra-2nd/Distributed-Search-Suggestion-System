package com.dsss.model;

/**
 * A single typeahead suggestion: the query text and its current search count.
 */
public record Suggestion(String query, long count) {
}
