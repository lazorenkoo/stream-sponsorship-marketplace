import { useEffect, useState } from 'react'

type OfferEvent = {
  version: number
  revision: number
  status: string
  actorRole: string
  occurredAt: string
}

const pageSize = 10
const dates = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })
const actions: Record<string, string> = {
  DRAFT: 'Created draft',
  PENDING: 'Sent offer',
  COUNTERED: 'Suggested changes',
  ACCEPTED: 'Accepted and reserved',
  REJECTED: 'Declined offer',
  WITHDRAWN: 'Withdrew offer',
  EXPIRED: 'Offer expired',
}

export function OfferActivity({ offerId }: { offerId: string }) {
  const [open, setOpen] = useState(false)
  return <details onToggle={event => setOpen(event.currentTarget.open)}>
    <summary className="w-fit cursor-pointer text-sm font-medium text-sky-800">Activity history</summary>
    {open && <Events offerId={offerId} />}
  </details>
}

function Events({ offerId }: { offerId: string }) {
  const [events, setEvents] = useState<OfferEvent[]>([])
  const [before, setBefore] = useState<number | null>(null)
  const [next, setNext] = useState<number | null>(null)
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (before !== null) query.set('beforeVersion', String(before))
    fetch('/api/offers/' + offerId + '/events?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const page: OfferEvent[] = await response.json()
        if (controller.signal.aborted) return
        setEvents(previous => [...(before === null ? [] : previous), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].version : null)
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [offerId, before, retry])

  return <div className="mt-2 space-y-2 text-sm">
    <ol className="divide-y divide-slate-200 rounded-lg bg-slate-50">
      {events.map(event => <li key={event.version} className="space-y-1 p-2">
        <p className="font-medium">{event.status === 'PENDING' && event.revision > 0 ? 'Sent revised offer' : actions[event.status] ?? event.status}</p>
        <p className="text-slate-600">
          {event.actorRole === 'ADVERTISER' ? 'Advertiser' : 'Streamer'} · Revision {event.revision + 1}
        </p>
        <time className="block text-slate-600" dateTime={event.occurredAt}>{dates.format(new Date(event.occurredAt))}</time>
      </li>)}
    </ol>
    {status === 'loading' && <p role="status">Loading activity…</p>}
    {status === 'error' && <p role="alert">Could not load activity. <button type="button" className="text-sky-800 underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {status === 'ready' && !events.length && <p>No recorded activity.</p>}
    {status === 'ready' && next !== null && <button type="button" className="text-sky-800 underline"
      onClick={() => { setStatus('loading'); setBefore(next) }}>Show earlier activity</button>}
  </div>
}
