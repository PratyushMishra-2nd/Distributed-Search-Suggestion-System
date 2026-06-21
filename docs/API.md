# API Reference

Base URL: `http://localhost:8080`. All responses are JSON.

---

## `GET /suggest`
Top-K typeahead suggestions for a prefix, sorted by search count descending.

| Param | Type   | Notes                                  |
|-------|--------|----------------------------------------|
| `q`   | string | Prefix. Empty/blank → `[]`. Case-insensitive. |

**200**
```json
[
  { "query": "java (programming language)", "count": 98500 },
  { "query": "javascript", "count": 95200 },
  { "query": "java", "count": 74000 }
]
```
Behaviour: empty `q` → `[]`; no prefix match → `[]`; `JaV` == `jav`. Results are
served from a Redis cache node when warm (see `/cache/debug`).

Response headers expose retrieval telemetry:

| Header    | Example           | Meaning                                       |
|-----------|-------------------|-----------------------------------------------|
| `X-Cache` | `HIT` / `MISS`    | Whether the cache served the prefix           |
| `X-Shard` | `logical-node-0`  | The logical node (Redis DB) that owns it       |

The endpoint itself accepts any prefix length; the UI gates lookups at **3+
characters** to avoid very broad, low-value queries.

---

## `POST /search`
Submit a search. The query is buffered and applied to counts on the next batch
flush; the response is the dummy acknowledgement (assignment §4.2).

**Request**
```json
{ "query": "java tutorial" }
```

**200**
```json
{ "message": "Searched" }
```
The submitted count appears in `/suggest` and `/trending` after the next flush
(≤ `app.batch.flush-interval-ms`).

---

## `GET /trending`
Trending queries among submitted searches.

| Param   | Type   | Default   | Notes                                       |
|---------|--------|-----------|---------------------------------------------|
| `mode`  | string | `recency` | `recency` = time-decayed; `basic` = raw count |
| `limit` | int    | top-K     | Max items                                   |

**200**
```json
[
  { "query": "java tutorial", "windowCount": 12, "recencyScore": 9.71 }
]
```
`windowCount` is the raw recent count (basic ranking); `recencyScore` is the
decayed score (recency ranking). Both are returned regardless of `mode`; only
the sort order differs.

---

## `GET /cache/debug`
Inspect consistent-hash routing and per-shard cache stats.

| Param    | Type   | Notes                              |
|----------|--------|------------------------------------|
| `prefix` | string | Optional. If given, shows its routing. |

**200**
```json
{
  "routing": {
    "prefix": "java",
    "profileKey": "ja",
    "ownerShard": "logical-node-0",
    "cached": true
  },
  "shards": [
    { "shard": "logical-node-0", "entries": 5, "hits": 120, "misses": 18, "hitRate": 0.87 },
    { "shard": "logical-node-1", "entries": 0, "hits": 0, "misses": 0, "hitRate": 0.0 }
  ]
}
```

---

## `GET /metrics`
Operational snapshot for the performance report.

**200**
```json
{
  "distinctQueries": 119,
  "batch": {
    "totalSubmissions": 10001,
    "totalStoreWrites": 571,
    "flushCount": 20,
    "pending": 0,
    "writeReductionRatio": 0.943
  },
  "latency": {
    "GET /suggest": { "count": 4096, "p50_ms": 0.10, "p95_ms": 0.14, "p99_ms": 0.20, "max_ms": 1.66 }
  }
}
```
`writeReductionRatio` = 1 − storeWrites / submissions. Latency percentiles are in
milliseconds, bucketed by `METHOD /path-prefix`.
