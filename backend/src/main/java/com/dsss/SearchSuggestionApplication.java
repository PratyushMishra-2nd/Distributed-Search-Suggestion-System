package com.dsss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Distributed Search Suggestion System backend.
 *
 * Single Spring Boot process that simulates a distributed typeahead service:
 *  - in-memory search-count store (Trie + count map)
 *  - cache shards placed on a consistent-hash ring
 *  - a background batch writer that buffers search-count updates
 *  - recency-aware trending ranking
 */
@SpringBootApplication
@EnableScheduling
public class SearchSuggestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchSuggestionApplication.class, args);
    }
}
