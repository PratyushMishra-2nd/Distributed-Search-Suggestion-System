package com.dsss.perf;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a bounded ring buffer of recent latency samples per endpoint and
 * computes percentiles on demand. Used to report p95 (spec non-functional req).
 */
@Component
public class LatencyRecorder {

    private static final int CAPACITY = 4096;

    private static final class Window {
        final long[] samples = new long[CAPACITY];
        int size = 0;
        int next = 0;

        synchronized void add(long micros) {
            samples[next] = micros;
            next = (next + 1) % CAPACITY;
            if (size < CAPACITY) {
                size++;
            }
        }

        synchronized long[] snapshot() {
            return Arrays.copyOf(samples, size);
        }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void record(String endpoint, long micros) {
        windows.computeIfAbsent(endpoint, k -> new Window()).add(micros);
    }

    /** Per-endpoint latency stats in milliseconds. */
    public Map<String, Object> report() {
        Map<String, Object> out = new LinkedHashMap<>();
        windows.forEach((endpoint, w) -> {
            long[] s = w.snapshot();
            if (s.length == 0) {
                return;
            }
            Arrays.sort(s);
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("count", s.length);
            stat.put("p50_ms", ms(percentile(s, 50)));
            stat.put("p95_ms", ms(percentile(s, 95)));
            stat.put("p99_ms", ms(percentile(s, 99)));
            stat.put("max_ms", ms(s[s.length - 1]));
            out.put(endpoint, stat);
        });
        return out;
    }

    private static long percentile(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static double ms(long micros) {
        return Math.round(micros / 10.0) / 100.0; // 2dp ms
    }
}
