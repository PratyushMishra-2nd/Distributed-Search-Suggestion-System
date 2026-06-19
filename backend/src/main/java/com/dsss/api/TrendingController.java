package com.dsss.api;

import com.dsss.config.AppProperties;
import com.dsss.model.TrendingItem;
import com.dsss.trending.TrendingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Trending endpoint. {@code GET /trending} returns recency-ranked queries by
 * default; {@code ?mode=basic} returns the raw-count ranking so the two
 * approaches can be compared side by side (spec §7).
 */
@RestController
public class TrendingController {

    private final TrendingService trending;
    private final int defaultLimit;

    public TrendingController(TrendingService trending, AppProperties props) {
        this.trending = trending;
        this.defaultLimit = props.getSuggest().getTopK();
    }

    @GetMapping("/trending")
    public List<TrendingItem> trending(
            @RequestParam(name = "mode", defaultValue = "recency") String mode,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int n = limit == null ? defaultLimit : Math.max(1, limit);
        long now = System.currentTimeMillis();
        return "basic".equalsIgnoreCase(mode)
                ? trending.topBasic(n, now)
                : trending.top(n, now);
    }
}
