# Performance Report

Measured with `node scripts/loadtest.mjs http://localhost:8080 5000 32` on the
local dev machine (Java 22, Windows), running the **full real stack**:

- **Dataset**: full ORCAS, **1,303,031 distinct queries** loaded.
- **Cache**: 3 real Redis nodes (docker profile — physical containers on
  6379/6380/6381) selected by the consistent-hash ring.
- **Store**: PostgreSQL (durable).

Two latency figures are reported:
- **Client-side** — the load-test stopwatch around `fetch`: end-to-end
  (HTTP + JSON over loopback + Redis hop).
- **Server-side** — from `GET /metrics` (`LatencyFilter`), isolating service logic.

## Typeahead latency — cache hit vs. miss

`GET /suggest`, 5000 requests, concurrency 32, two consecutive passes:

| Pass (client-side)  | Throughput   | p50     | **p95**    | p99       | max       |
|---------------------|--------------|---------|------------|-----------|-----------|
| Cold cache (misses) | 2,676 req/s  | 5.29 ms | **19.68 ms** | 205 ms  | 916 ms    |
| Warm cache (hits)   | 8,154 req/s  | 3.67 ms | **6.06 ms**  | 9.25 ms | 16.62 ms  |

Server-side (`/metrics`, network excluded):

| Endpoint       | p50     | **p95**    | p99     | max      |
|----------------|---------|------------|---------|----------|
| `GET /suggest` | 0.61 ms | **1.78 ms** | 3.07 ms | 6.6 ms   |
| `POST /search` | 0.37 ms | **0.61 ms** | 2.79 ms | 444 ms\* |

\* one-off outlier during JIT warm-up / first batch flush; p99 is 2.79 ms.

**Read:** warming the cache cut client-side p95 from **19.7 → 6.1 ms (~69%)** and
tripled throughput (2.7k → 8.2k req/s). The cold pass is heavier than the old
in-memory build because a miss now also does a Redis round-trip + populate over
1.3M queries; once warm, reads are served from Redis and p95 is ~6 ms end-to-end
(~1.8 ms in-process). Comfortably meets the sub-10 ms p95 expectation (§10).

## Cache hit rate

After the warm pass, per Redis node (`GET /cache/debug`):

| Node            | hits | misses | hit rate |
|-----------------|------|--------|----------|
| localhost:6379  | 2652 | 14     | 99.5%    |
| localhost:6380  | 3979 | 28     | 99.3%    |
| localhost:6381  | 3318 | 16     | 99.5%    |

~**99.4%** hit rate once warm; misses are first-touch per prefix (then cached for
the 30 s TTL). Load is spread fairly evenly across the three nodes by the
consistent-hash ring + virtual nodes.

## Batch write reduction

`POST /search`, 5000 submissions, `batch.size=500`, `flush-interval-ms=2000`:

| Metric               | Value   |
|----------------------|---------|
| Submissions          | 5,000   |
| Store writes applied | ~270    |
| Flushes              | ~9      |
| **Write reduction**  | **~95%** (`1 − 270/5000`) |

Each flush collapses many repeated submissions into one `+N` upsert per distinct
query, so PostgreSQL absorbs ~18× fewer writes than there are searches — the core
benefit of the batch layer.

## Trade-offs observed
- **Freshness vs. write load**: larger `batch.size` / longer `flush-interval-ms`
  raises the reduction ratio but delays when a submitted count becomes visible.
  Defaults keep the visible delay ≤ 2 s.
- **Crash window**: the batch buffer is in-memory; submissions buffered but not
  yet flushed are lost on a hard crash (a clean shutdown flushes via
  `@PreDestroy`). A durable queue (e.g. Redis Streams) would close this gap.
- **Cache TTL**: longer `ttl-ms` raises hit rate (lower p95) but lets rankings go
  stale up to the TTL when counts change without an explicit invalidation.
- **Cold-miss cost**: real Redis adds a network hop vs. the old in-process cache;
  the win is shared cache state across instances + eviction/TTL handled by Redis.

## Reproduce
```bash
docker compose up -d                                       # real Redis + Postgres
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=docker
node scripts/loadtest.mjs http://localhost:8080 5000 32
curl http://localhost:8080/metrics
curl "http://localhost:8080/cache/debug?prefix=git"
```
(Omit the profile to run on embedded Redis + Postgres instead.)
