# Distributed Search Suggestion System

A search typeahead service (like the autocomplete on a search engine or
e-commerce site). You type a prefix, the backend returns the top-10 matching
queries ranked by search count. Submitting a search updates the counts. The
backend is designed as a **distributed data system** — even though it runs in a
single process for the assignment, it simulates the pieces that matter:

- **Typeahead API** — `GET /suggest?q=<prefix>` → top-K by count (Trie + heap).
- **Caching with consistent hashing** — prefixes are routed to one of N cache
  shards via a consistent-hash ring; entries have TTL + LRU eviction.
- **Batch writes** — search submissions are buffered and flushed in aggregate so
  the primary store sees far fewer writes than there are searches.
- **Trending** — a recency-aware (time-decayed) ranking alongside a basic
  raw-count ranking, so the two approaches can be compared.

| Layer    | Tech                          |
|----------|-------------------------------|
| Backend  | Java 17 · Spring Boot 3.3     |
| Frontend | React 18 · Vite 5             |
| Dataset  | Wikipedia pageviews (public)  |

![Suggestions dropdown](screenshots/ui-suggestions.png)

## Architecture

```
React (Vite) UI ──HTTP──> Spring Boot backend
  SearchBox (debounce, keyboard nav)        ├─ api/        controllers
  Suggestions dropdown                      ├─ cache/      consistent-hash ring + N shards (TTL/LRU)
  Trending section (recency | basic)        ├─ store/      SearchCountStore (Trie + count map)
                                            ├─ batch/      BatchWriter (buffer + background flush)
                                            ├─ trending/   recency-aware scoring
                                            ├─ perf/       per-endpoint latency (p95)
                                            └─ loader/     Wikipedia pageviews loader
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

On startup it loads the dataset. With no downloaded dump it falls back to the
bundled sample (`backend/src/main/resources/data/pageviews-sample.txt`, ~120
queries) so the app always boots.

**2. Frontend** (port 5173, proxies the API to 8080):

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>.

## Use the full dataset (>100k queries)

Download one hourly Wikipedia pageviews dump and restart the backend:

```bash
# bash
scripts/fetch-dataset.sh                 # default date
scripts/fetch-dataset.sh 2024 11 01 12   # YYYY MM DD HH (UTC)

# windows
powershell -File scripts/fetch-dataset.ps1
```

It saves `backend/data/pageviews.gz`; the loader reads gzip directly and keeps
English-Wikipedia rows whose view count becomes the initial search count.

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
