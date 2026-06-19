# Performance Report

Measured with `node scripts/loadtest.mjs http://localhost:8080 5000 32` on the
local dev machine (Java 22, Windows). Two figures are reported:

- **Client-side** latency (the load-test stopwatch around `fetch`) — includes
  HTTP + JSON over loopback, i.e. end-to-end.
- **Server-side** latency from `GET /metrics` — in-process request timing via the
  `LatencyFilter`, isolating the service logic.

> Dataset: bundled sample (~120 queries). The full Wikipedia dump has more
> distinct prefixes but the same access pattern; cache + batch behaviour scales
> the same way. Re-run after `scripts/fetch-dataset.sh` to reproduce at size.

## Typeahead latency — cache hit vs. miss

`GET /suggest`, 5000 requests, concurrency 32, two consecutive passes:

| Pass (client-side)     | Throughput  | p50     | **p95**   | p99      | max      |
|------------------------|-------------|---------|-----------|----------|----------|
| Cold cache (misses)    | 7,083 req/s | 3.69 ms | **7.83 ms** | 23.54 ms | 44.61 ms |
| Warm cache (hits)      | 12,224 req/s| 2.16 ms | **4.43 ms** | 8.23 ms  | 15.28 ms |

Server-side (`/metrics`, network excluded):

| Endpoint       | p50     | **p95**   | p99     | max     |
|----------------|---------|-----------|---------|---------|
| `GET /suggest` | 0.10 ms | **0.14 ms** | 0.20 ms | 1.66 ms |
| `POST /search` | 0.12 ms | **0.15 ms** | 0.24 ms | 4.41 ms |

**Read:** warming the consistent-hash cache cut p95 from 7.83 → 4.43 ms (~43%)
and raised throughput ~1.7×. The service itself answers a suggestion in ~0.1 ms;
the rest of the client-side number is loopback HTTP/JSON. p95 is comfortably
sub-10 ms, meeting the non-functional latency expectation (§10).

## Batch write reduction

`POST /search`, 10,001 submissions over the run, `batch.size=500`,
`flush-interval-ms=2000`:

| Metric                | Value   |
|-----------------------|---------|
| Submissions           | 10,001  |
| Store writes applied  | 571     |
| Flushes               | 20      |
| **Write reduction**   | **94.3%** (`1 − 571/10001`) |

Because the load test submits a small set of repeated queries (typeahead-like
bursts), each flush collapses many submissions into one `+N` write per distinct
query. The primary store therefore absorbs ~18× fewer writes than searches —
the core benefit the batch layer exists to provide.

## Trade-offs observed
- **Freshness vs. write load**: a larger `batch.size` / longer
  `flush-interval-ms` increases the reduction ratio further but delays when a
  submitted count becomes visible. Defaults keep the visible delay ≤ 2 s.
- **Cache TTL**: longer `ttl-ms` raises hit rate (lower p95) but lets rankings go
  stale up to the TTL when counts change without an explicit invalidation.

## Reproduce
```bash
cd backend && mvn spring-boot:run     # terminal 1
node scripts/loadtest.mjs http://localhost:8080 5000 32   # terminal 2
curl http://localhost:8080/metrics
```
