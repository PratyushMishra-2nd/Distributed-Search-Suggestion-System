/**
 * Live retrieval telemetry for the last keystroke: round-trip latency, whether
 * the consistent-hash cache served it, and which shard owns the prefix. Exposing
 * the machinery is the point — this is a distributed-systems demo, not a black box.
 */
export default function Telemetry({ data }) {
  const idle = !data
  const hit = data?.cache === 'HIT'

  return (
    <dl className={`telemetry${idle ? ' telemetry--idle' : ''}`} aria-live="polite">
      <div className="tm">
        <dt>latency</dt>
        <dd className="tm-val tm-amber">{idle ? '—' : `${data.ms.toFixed(1)}`}<i>ms</i></dd>
      </div>
      <div className="tm">
        <dt>cache</dt>
        <dd className={`tm-chip ${idle ? '' : hit ? 'tm-chip--hit' : 'tm-chip--miss'}`}>
          {idle ? '—' : data.cache}
        </dd>
      </div>
      <div className="tm">
        <dt>shard</dt>
        <dd className="tm-val">{idle ? '—' : data.shard.replace('cache-node-', 'node ')}</dd>
      </div>
      <div className="tm">
        <dt>results</dt>
        <dd className="tm-val">{idle ? '—' : data.count}</dd>
      </div>
    </dl>
  )
}
