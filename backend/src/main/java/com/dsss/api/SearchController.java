package com.dsss.api;

import com.dsss.batch.BatchWriter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Search-submission endpoint. {@code POST /search} records the query (via the
 * batch writer) and returns the dummy acknowledgement required by spec §4.2.
 * The submitted count is reflected in suggestions/trending after the next flush.
 */
@RestController
public class SearchController {

    private final BatchWriter batchWriter;

    public SearchController(BatchWriter batchWriter) {
        this.batchWriter = batchWriter;
    }

    public record SearchRequest(@NotBlank String query) {
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, String>> search(@RequestBody SearchRequest req) {
        batchWriter.submit(req.query());
        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
