import { useEffect, useState } from 'react'

type Hours = { startsAt: string; endsAt: string }

const pageSize = 10
const timeFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

function availableStart(window: Hours, duration: number) {
  if (!Number.isInteger(duration) || duration <= 0 || duration > 604800) return null
  const start = Date.parse(window.startsAt)
  const end = Date.parse(window.endsAt)
  const earliest = (Math.floor(Date.now() / 1000) + 1) * 1000
  if (start >= earliest && start + duration * 1000 <= end) return window.startsAt
  if (start < earliest && earliest + duration * 1000 <= end) return new Date(earliest).toISOString()
  return null
}

export function PlacementHours({ placementId, duration, onChoose }: {
  placementId: string; duration: number; onChoose: (instant: string) => void
}) {
  const [windows, setWindows] = useState<Hours[]>([])
  const [after, setAfter] = useState('')
  const [next, setNext] = useState('')
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (after) query.set('after', after)
    fetch('/api/catalog/placements/' + placementId + '/hours?' + query,
      { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 404) { setStatus('unavailable'); return }
        if (!response.ok) throw new Error()
        const page: Hours[] = await response.json()
        if (controller.signal.aborted) return
        setWindows(previous => [...(after ? previous : []), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].startsAt : '')
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [placementId, after, retry])

  return <section className="space-y-2 rounded-lg border border-slate-200 p-3 text-sm">
    <h3 className="font-semibold">Placement hours</h3>
    {status === 'loading' && <p role="status">Loading hours…</p>}
    {status === 'unavailable' && <p role="alert">This placement is no longer available.</p>}
    {status === 'error' && <p role="alert">Could not load hours. <button type="button" className="underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {status === 'ready' && !windows.length && <p>No upcoming placement hours.</p>}
    <ul className="divide-y divide-slate-200">
      {windows.map(window => {
        const start = availableStart(window, duration)
        return <li key={window.startsAt} className="flex flex-wrap items-center justify-between gap-2 py-2">
          <span>{timeFormat.formatRange(new Date(window.startsAt), new Date(window.endsAt))}</span>
          <button type="button" className="rounded-md border border-sky-200 px-2 py-1 font-medium text-sky-900 disabled:opacity-50"
            disabled={!start} onClick={() => {
              const current = availableStart(window, duration)
              if (current) onChoose(current)
            }}>{start ? 'Use this window' : 'Duration does not fit'}</button>
        </li>
      })}
    </ul>
    {next && status === 'ready' && <button type="button" className="text-sky-800 underline"
      onClick={() => { setStatus('loading'); setAfter(next) }}>Show more hours</button>}
  </section>
}
