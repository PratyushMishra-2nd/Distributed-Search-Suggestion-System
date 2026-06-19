// Simple load generator for the suggestion API. Measures p50/p95/p99 latency
// for /suggest (cold vs warm cache) and drives /search to exercise batching.
//
// Usage: node scripts/loadtest.mjs [baseUrl] [requests] [concurrency]
//   node scripts/loadtest.mjs http://localhost:8080 5000 32

const BASE = process.argv[2] || 'http://localhost:8080'
const TOTAL = parseInt(process.argv[3] || '5000', 10)
const CONC = parseInt(process.argv[4] || '32', 10)

// Prefixes biased toward the seed dataset so most hit real suggestions.
const PREFIXES = ['ja', 'jav', 'java', 'py', 'pyt', 'react', 'redis', 'node',
  'c', 'cl', 'data', 'dist', 'doc', 'micro', 'mysql', 'mongo', 'kube', 'goo',
  'git', 'sql', 'sea', 'http', 'ht', 'has', 'el', 'fa', 'web', 'ru', 'ng', 'net']

function pct(sorted, p) {
  const i = Math.max(0, Math.ceil((p / 100) * sorted.length) - 1)
  return sorted[i]
}

async function timed(url, opts) {
  const t0 = performance.now()
  const res = await fetch(url, opts)
  await res.text()
  return { ms: performance.now() - t0, ok: res.ok }
}

async function runSuggest(label) {
  const lat = []
  let errors = 0
  let next = 0
  async function worker() {
    while (next < TOTAL) {
      const i = next++
      const p = PREFIXES[i % PREFIXES.length]
      try {
        const r = await timed(`${BASE}/suggest?q=${encodeURIComponent(p)}`)
        lat.push(r.ms)
        if (!r.ok) errors++
      } catch {
        errors++
      }
    }
  }
  const t0 = performance.now()
  await Promise.all(Array.from({ length: CONC }, worker))
  const wall = performance.now() - t0
  lat.sort((a, b) => a - b)
  const rps = Math.round((TOTAL / wall) * 1000)
  console.log(`\n[${label}] ${TOTAL} GET /suggest, concurrency ${CONC}`)
  console.log(`  throughput : ${rps} req/s   (wall ${wall.toFixed(0)} ms, errors ${errors})`)
  console.log(`  p50 ${pct(lat, 50).toFixed(2)} ms | p95 ${pct(lat, 95).toFixed(2)} ms | ` +
    `p99 ${pct(lat, 99).toFixed(2)} ms | max ${lat[lat.length - 1].toFixed(2)} ms`)
}

async function runSearch(n) {
  let next = 0
  async function worker() {
    while (next < n) {
      const i = next++
      const q = PREFIXES[i % PREFIXES.length] + ' query'
      try {
        await timed(`${BASE}/search`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query: q }),
        })
      } catch {
        /* ignore */
      }
    }
  }
  await Promise.all(Array.from({ length: CONC }, worker))
  console.log(`\n[search] submitted ${n} POST /search`)
}

;(async () => {
  console.log(`Target ${BASE}`)
  await runSuggest('cold cache')      // first pass: mostly cache misses
  await runSuggest('warm cache')      // second pass: mostly cache hits
  await runSearch(5000)               // drive batching
  const m = await (await fetch(`${BASE}/metrics`)).json()
  console.log('\n[metrics] batch:', JSON.stringify(m.batch))
  console.log('[metrics] latency:', JSON.stringify(m.latency, null, 2))
})()
