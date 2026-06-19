import { useState } from 'react'
import SearchBox from './components/SearchBox'
import Trending from './components/Trending'
import Telemetry from './components/Telemetry'
import { submitSearch } from './api'

export default function App() {
  const [refreshKey, setRefreshKey] = useState(0)
  const [lastSubmitted, setLastSubmitted] = useState(null)
  const [telemetry, setTelemetry] = useState(null)

  async function handleSubmit(query) {
    try {
      await submitSearch(query)
      setLastSubmitted(query)
      // counts settle after the next batch flush; nudge trending to refetch
      setTimeout(() => setRefreshKey((k) => k + 1), 1600)
    } catch {
      setLastSubmitted(null)
    }
  }

  return (
    <div className="page">
      <div className="grain" aria-hidden />

      <header className="masthead">
        <div className="wordmark">
          <span className="wordmark-mark">⌕</span>
          <span className="wordmark-text">prefix</span>
        </div>
        <p className="tagline">
          A retrieval console over a Wikipedia-pageviews index — consistent-hash
          cache, batched writes, recency-aware trending.
        </p>
      </header>

      <main className="console">
        <SearchBox onSubmitSearch={handleSubmit} onTelemetry={setTelemetry} />
        <Telemetry data={telemetry} />

        {lastSubmitted && (
          <p className="ack" role="status">
            Recorded <strong>{lastSubmitted}</strong>. It climbs the ranking after
            the next batch flush.
          </p>
        )}

        <Trending refreshKey={refreshKey} />
      </main>

      <footer className="footer">
        <span>local · single-process simulation</span>
        <nav>
          <a href="/cache/debug?prefix=ja" target="_blank" rel="noreferrer">cache ring</a>
          <a href="/metrics" target="_blank" rel="noreferrer">metrics</a>
        </nav>
      </footer>
    </div>
  )
}
