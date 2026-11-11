import { useEffect, useState } from 'react'
import type { Rectangle } from './StreamAreaPicker'

type Revision = {
  revision: number
  proposedByRole: string
  createdAt: string
  terms: {
    price: number
    currency: string
    startsAt: string | null
    durationSeconds: number
    advertisingCategory: string
    rectangle: Rectangle
  }
}

const pageSize = 10
const dateTime = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

export function OfferHistory({ offerId }: { offerId: string }) {
  const [open, setOpen] = useState(false)

  return <details onToggle={event => setOpen(event.currentTarget.open)}>
    <summary className="w-fit cursor-pointer text-sm font-medium text-sky-800">Revision history</summary>
    {open && <Revisions offerId={offerId} />}
  </details>
}

function Revisions({ offerId }: { offerId: string }) {
  const [revisions, setRevisions] = useState<Revision[]>([])
  const [before, setBefore] = useState<number | null>(null)
  const [next, setNext] = useState<number | null>(null)
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (before !== null) query.set('beforeRevision', String(before))
    fetch('/api/offers/' + offerId + '/revisions?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const page: Revision[] = await response.json()
        if (controller.signal.aborted) return
        setRevisions(previous => [...(before === null ? [] : previous), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].revision : null)
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [before, offerId, retry])

  return <div className="mt-2 space-y-2 text-sm">
    <ol className="divide-y divide-slate-200 rounded-lg bg-slate-50">
      {revisions.map(({ revision, proposedByRole, createdAt, terms }) => <li key={revision} className="space-y-1 p-2">
        <p className="font-medium">Revision {revision + 1} · {proposedByRole === 'ADVERTISER' ? 'Advertiser' : 'Streamer'}</p>
        <p className="text-slate-600">{dateTime.format(new Date(createdAt))}</p>
        <p>{terms.price.toFixed(2)} {terms.currency} · {terms.durationSeconds} s · {terms.startsAt
          ? dateTime.format(new Date(terms.startsAt)) : 'Flexible start'}</p>
        <p className="break-words text-slate-600">{terms.advertisingCategory}</p>
        <svg viewBox="0 0 10000 10000" preserveAspectRatio="none" role="img" aria-label="Ad area in this revision"
          className="aspect-video w-32 rounded bg-slate-200">
          <rect {...terms.rectangle} fill="#7dd3fc" stroke="#0369a1" strokeWidth="55" />
        </svg>
        <a className="text-sky-800 underline" target="_blank" rel="noreferrer"
          href={'/api/offers/' + offerId + '/revisions/' + revision + '/creative'}>Open revision creative</a>
      </li>)}
    </ol>
    {status === 'loading' && <p role="status">Loading revisions…</p>}
    {status === 'error' && <p role="alert">Could not load revisions. <button type="button" className="underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {status === 'ready' && next !== null && <button type="button" className="text-sky-800 underline"
      onClick={() => { setStatus('loading'); setBefore(next) }}>Show earlier revisions</button>}
  </div>
}
