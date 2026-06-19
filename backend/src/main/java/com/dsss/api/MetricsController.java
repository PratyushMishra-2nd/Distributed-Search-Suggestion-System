package com.dsss.api;

import com.dsss.batch.BatchWriter;
import com.dsss.perf.LatencyRecorder;
import com.dsss.store.SearchCountStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /metrics} — operational snapshot for the performance report:
 * batch write-reduction ratio and per-endpoint latency percentiles (incl. p95).
 */
@RestController
public class MetricsController {

    private final BatchWriter batchWriter;
    private final LatencyRecorder latency;
    private final SearchCountStore store;

    public MetricsController(BatchWriter batchWriter, LatencyRecorder latency, SearchCountStore store) {
        this.batchWriter = batchWriter;
        this.latency = latency;
        this.store = store;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("distinctQueries", store.size());

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("totalSubmissions", batchWriter.totalSubmissions());
        batch.put("totalStoreWrites", batchWriter.totalStoreWrites());
        batch.put("flushCount", batchWriter.flushCount());
        batch.put("pending", batchWriter.pending());
        batch.put("writeReductionRatio", Math.round(batchWriter.overallReductionRatio() * 1000) / 1000.0);
        out.put("batch", batch);

        out.put("latency", latency.report());
        return out;
    }
}
