package com.dsss.api;

import com.dsss.cache.CacheRouter;
import com.dsss.config.AppProperties;
import com.dsss.model.Suggestion;
import com.dsss.store.SearchCountStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Typeahead endpoint. {@code GET /suggest?q=<prefix>} returns the top-K
 * suggestions for the prefix, served from the cache when warm.
 */
@RestController
public class SuggestController {

    private final SearchCountStore store;
    private final CacheRouter cache;
    private final int topK;

    public SuggestController(SearchCountStore store, CacheRouter cache, AppProperties props) {
        this.store = store;
        this.cache = cache;
        this.topK = props.getSuggest().getTopK();
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<Suggestion>> suggest(@RequestParam(name = "q", required = false) String q) {
        String prefix = SearchCountStore.normalize(q == null ? "" : q);
        if (prefix.isEmpty()) {
            return ResponseEntity.ok(List.of()); // empty input -> no suggestions
        }
        // Report which shard served the prefix and whether it was a cache hit so
        // the UI can show honest retrieval telemetry (this is a data-system demo).
        String shard = cache.shardFor(prefix).id();
        List<Suggestion> cached = cache.get(prefix);
        if (cached != null) {
            return ResponseEntity.ok()
                    .header("X-Cache", "HIT").header("X-Shard", shard)
                    .body(cached);
        }
        List<Suggestion> result = store.topK(prefix, topK);
        cache.put(prefix, result); // populate even empty results to absorb repeats
        return ResponseEntity.ok()
                .header("X-Cache", "MISS").header("X-Shard", shard)
                .body(result);
    }
}
