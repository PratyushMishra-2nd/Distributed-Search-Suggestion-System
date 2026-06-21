# Distributed Search Suggestion System

A search typeahead service (like the autocomplete on a search engine or
e-commerce site). You type a prefix, the backend returns the top-10 matching
queries ranked by search count. Submitting a search updates the counts. The
backend is designed as a **distributed data system** — even though it runs in a
single process for the assignment, it simulates the pieces that matter:

- **Durable store** — counts persist in **PostgreSQL** (embedded, no Docker); the
  in-memory Trie is rebuilt from it on restart. First run seeds from ORCAS.
- **Typeahead API** — `GET /suggest?q=<prefix>` → top-K by count (Trie + heap).
- **Caching with consistent hashing** — prefixes are routed to one of N **logical
  cache nodes** (Redis logical DBs on a single Redis server) via our own
  consistent-hash ring; entries have a TTL and degrade to a cache-miss if Redis
  is unreachable.
- **Batch writes** — search submissions are buffered and flushed in aggregate so
  the primary store sees far fewer writes than there are searches.
- **Trending** — a recency-aware (time-decayed) ranking alongside a basic
  raw-count ranking, so the two approaches can be compared.

| Layer    | Tech                          |
|----------|-------------------------------|
| Backend  | Java 17 · Spring Boot 3.3            |
| Frontend | React 18 · Vite 5                   |
| Store    | PostgreSQL (embedded) — durable     |
| Cache    | Redis (embedded) · logical nodes    |
| Dataset  | ORCAS — real Bing queries (public)  |

![Suggestions dropdown](screenshots/ui-suggestions.png)

## Architecture

```
React (Vite) UI ──HTTP──> Spring Boot backend
  SearchBox (debounce, keyboard nav)        ├─ api/        controllers
  Suggestions dropdown                      ├─ cache/      consistent-hash ring + Redis logical nodes
  Trending section (recency | basic)        ├─ store/      SearchCountStore (Trie + count map)
                                            ├─ persistence/ PostgreSQL durable store (embedded)
                                            ├─ batch/      BatchWriter (buffer → store + Postgres)
                                            ├─ trending/   recency-aware scoring
                                            ├─ perf/       per-endpoint latency (p95)
                                            └─ loader/     ORCAS seed + PG-first init
```

Full design + trade-offs: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
API reference: [docs/API.md](docs/API.md).
Performance report: [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Requirements

- JDK 17+ (tested on 22), Maven 3.9+
- Node 18+ (tested on 22), npm

## Run it

**1. Backend** (port 8080):

```bash
cd backend
mvn spring-boot:run
```

On startup it boots two embedded servers (no Docker), shutting them down on exit:
- **PostgreSQL** (bundled binary, port 5433) — durable store, persists to
  `backend/data/pgdata`. First run seeds it from ORCAS (~50s); later runs load
  straight from the DB.
- **Redis** (bundled binary, port 6379) — **3 logical cache nodes** (logical
  DBs 0/1/2).

With no downloaded ORCAS dump it seeds from the bundled sample
(`backend/src/main/resources/data/orcas-sample.tsv`, ~60 real queries) so the
app always boots.

> **Cache nodes.** A single Redis server holds N *logical* nodes
> (`app.cache.logical-nodes`, one logical DB each). `app.cache.embedded: true`
> (default) runs that server in-process; set it to `false` and point
> `app.cache.server` at external Redis. Either way our consistent-hash ring
> (`CacheRouter`) decides which logical node owns each prefix.

**2. Frontend** (port 5173, proxies the API to 8080):

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>.

## Use the full dataset (~10M real queries)

Download the full ORCAS dump and restart the backend:

```bash
# bash
scripts/fetch-dataset.sh

# windows
powershell -File scripts/fetch-dataset.ps1
```

It saves `backend/data/orcas.tsv.gz` (~330 MB compressed, ~18M rows). The loader
reads gzip directly, scans the whole file, and keeps **1 in `app.dataset.sample-mod`**
queries by a stable hash (default 8) — so the kept set spans the entire file
while memory stays bounded; every click row of a kept query is still counted, so
its popularity is exact. Default yields **~1.3M distinct queries**. Set
`sample-mod: 1` to keep everything (needs a large heap, ~10M queries). To use
Wikipedia pageviews instead, set `app.dataset.format: pageviews`.

## Quick API check

```bash
curl "http://localhost:8080/suggest?q=jav"
curl -X POST localhost:8080/search -H 'Content-Type: application/json' -d '{"query":"java tutorial"}'
curl "http://localhost:8080/trending"
curl "http://localhost:8080/cache/debug?prefix=ja"
curl "http://localhost:8080/metrics"
```

## Load test / performance

```bash
node scripts/loadtest.mjs http://localhost:8080 5000 32
```

Reports cold vs warm cache p50/p95/p99 and the batch write-reduction ratio.
Recorded results in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Project layout

```
backend/    Spring Boot service (Maven)
frontend/   React + Vite UI
scripts/    dataset fetch + load test
docs/       architecture, API, performance
screenshots/
```
