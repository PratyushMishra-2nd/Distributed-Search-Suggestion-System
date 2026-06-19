import { useEffect, useRef, useState } from 'react'
import useDebounce from '../useDebounce'
import { fetchSuggestions } from '../api'

/** Split a query into [matchedPrefix, rest] so the typed prefix can be marked. */
function splitMatch(query, prefix) {
  const n = query.toLowerCase().startsWith(prefix.toLowerCase()) ? prefix.length : 0
  return [query.slice(0, n), query.slice(n)]
}

/**
 * The retrieval surface: debounced typeahead whose results render as a ranked
 * bar chart (bar width = count / max), with the typed prefix highlighted inside
 * each result. Keyboard: Up/Down to move, Enter to submit, Esc to dismiss.
 * Stale in-flight requests are aborted. Telemetry (latency, cache, shard) is
 * lifted to the parent via onTelemetry.
 */
export default function SearchBox({ onSubmitSearch, onTelemetry }) {
  const [text, setText] = useState('')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const debounced = useDebounce(text, 180)
  const abortRef = useRef(null)
  const prefix = debounced.trim()

  useEffect(() => {
    if (!prefix) {
      setResults([])
      setOpen(false)
      setError(null)
      onTelemetry(null)
      return
    }
    abortRef.current?.abort()
    const ctrl = new AbortController()
    abortRef.current = ctrl
    setLoading(true)
    setError(null)
    fetchSuggestions(prefix, ctrl.signal)
      .then(({ items, ms, cache, shard }) => {
        setResults(items)
        setOpen(true)
        setActive(-1)
        onTelemetry({ ms, cache, shard, count: items.length, prefix })
      })
      .catch((e) => {
        if (e.name !== 'AbortError') setError('Retrieval failed — is the backend running on :8080?')
      })
      .finally(() => setLoading(false))
    return () => ctrl.abort()
  }, [prefix]) // eslint-disable-line react-hooks/exhaustive-deps

  function choose(query) {
    setText(query)
    setOpen(false)
    onSubmitSearch(query)
  }

  function onKeyDown(e) {
    if (!open || results.length === 0) {
      if (e.key === 'Enter' && text.trim()) choose(text.trim())
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((i) => (i + 1) % results.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => (i - 1 + results.length) % results.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      choose(active >= 0 ? results[active].query : text.trim())
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const max = results.reduce((m, s) => Math.max(m, s.count), 1)

  return (
    <div className="searchbox">
      <div className={`field${open && results.length ? ' field--open' : ''}`}>
        <span className="caret" aria-hidden>›</span>
        <input
          className="field-input"
          type="text"
          value={text}
          placeholder="type a prefix…"
          autoFocus
          spellCheck="false"
          autoComplete="off"
          aria-label="Search prefix"
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          onFocus={() => results.length && setOpen(true)}
        />
        <span className={`pulse${loading ? ' pulse--on' : ''}`} aria-hidden />
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {open && results.length > 0 && (
        <ul className="results" role="listbox">
          {results.map((s, i) => {
            const [hit, rest] = splitMatch(s.query, prefix)
            return (
              <li
                key={s.query}
                role="option"
                aria-selected={i === active}
                className={`result${i === active ? ' result--active' : ''}`}
                style={{ '--w': `${(s.count / max) * 100}%`, '--i': i }}
                onMouseDown={(e) => {
                  e.preventDefault()
                  choose(s.query)
                }}
                onMouseEnter={() => setActive(i)}
              >
                <span className="result-rank">{String(i + 1).padStart(2, '0')}</span>
                <span className="result-q">
                  <mark>{hit}</mark>
                  {rest}
                </span>
                <span className="result-count">{s.count.toLocaleString()}</span>
                <span className="result-bar" aria-hidden />
              </li>
            )
          })}
        </ul>
      )}

      {open && !loading && results.length === 0 && prefix && (
        <div className="results results--empty">
          No query starts with <code>{prefix}</code>. Submit it to seed the index.
        </div>
      )}
    </div>
  )
}
