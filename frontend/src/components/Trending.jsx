import { useEffect, useState } from 'react'
import { fetchTrending } from '../api'

/**
 * Trending section. Two rankings of the same submitted searches: recency-aware
 * (time-decayed score) and basic (raw recent count). The segmented control
 * swaps the lens so the difference between the approaches is visible. Bars are
 * scaled to whichever metric the active lens ranks by.
 */
export default function Trending({ refreshKey }) {
  const [mode, setMode] = useState('recency')
  const [items, setItems] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    const load = () =>
      fetchTrending(mode)
        .then((data) => alive && setItems(data))
        .catch(() => alive && setError('Could not load trending'))
    load()
    const id = setInterval(load, 3000)
    return () => {
      alive = false
      clearInterval(id)
    }
  }, [mode, refreshKey])

  const valueOf = (t) => (mode === 'recency' ? t.recencyScore : t.windowCount)
  const max = items.reduce((m, t) => Math.max(m, valueOf(t)), 0.0001)

  return (
    <section className="trending">
      <header className="trending-head">
        <h2>trending</h2>
        <div className="segmented" role="tablist" aria-label="Ranking mode">
          <button
            role="tab"
            aria-selected={mode === 'recency'}
            className={mode === 'recency' ? 'on' : ''}
            onClick={() => setMode('recency')}
          >
            recency
          </button>
          <button
            role="tab"
            aria-selected={mode === 'basic'}
            className={mode === 'basic' ? 'on' : ''}
            onClick={() => setMode('basic')}
          >
            raw count
          </button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      {items.length === 0 ? (
        <p className="trending-empty">
          Nothing trending yet. Run a few searches and watch them rise.
        </p>
      ) : (
        <ol className="trending-list">
          {items.map((t, i) => (
            <li
              key={t.query}
              className="trend"
              style={{ '--w': `${(valueOf(t) / max) * 100}%`, '--i': i }}
            >
              <span className="trend-rank">{String(i + 1).padStart(2, '0')}</span>
              <span className="trend-q">{t.query}</span>
              <span className="trend-metric">
                {mode === 'recency'
                  ? t.recencyScore.toFixed(1)
                  : t.windowCount.toLocaleString()}
              </span>
              <span className="trend-bar" aria-hidden />
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
