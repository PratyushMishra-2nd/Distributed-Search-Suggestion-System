// Thin API client. Paths are relative so the Vite proxy / same-origin applies.

/**
 * Fetch suggestions and the retrieval telemetry that comes with them:
 * round-trip latency (timed here), cache HIT/MISS and the owning shard
 * (read from response headers the backend sets).
 */
export async function fetchSuggestions(prefix, signal) {
  const t0 = performance.now()
  const res = await fetch(`/suggest?q=${encodeURIComponent(prefix)}`, { signal })
  if (!res.ok) throw new Error(`suggest failed: ${res.status}`)
  const items = await res.json()
  return {
    items,
    ms: performance.now() - t0,
    cache: res.headers.get('X-Cache') || '—',
    shard: res.headers.get('X-Shard') || '—',
  }
}

export async function submitSearch(query) {
  const res = await fetch('/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query }),
  })
  if (!res.ok) throw new Error(`search failed: ${res.status}`)
  return res.json()
}

export async function fetchTrending(mode = 'recency') {
  const res = await fetch(`/trending?mode=${mode}`)
  if (!res.ok) throw new Error(`trending failed: ${res.status}`)
  return res.json()
}
