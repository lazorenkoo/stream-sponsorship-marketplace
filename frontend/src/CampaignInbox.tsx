import { useEffect, useState } from 'react'
import { postJson } from './offerApi'

type Application = {
  id: string; channelId: string; version: number; createdAt: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'
}
type Entry = Application & { channelName?: string }
const labels = { PENDING: 'Pending', ACCEPTED: 'Accepted', REJECTED: 'Declined', WITHDRAWN: 'Withdrawn' }
const button = 'rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium disabled:opacity-50'

export function CampaignInbox({ campaignId, published, onClose }: {
  campaignId: string; published: boolean; onClose: () => void
}) {
  const [entries, setEntries] = useState<Entry[]>([])
  const [after, setAfter] = useState('')
  const [next, setNext] = useState('')
  const [reload, setReload] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal, cache: 'no-store' as const }
    async function load() {
      const query = new URLSearchParams({ campaignId, direction: 'APPLICATION', limit: '10' })
      if (after) query.set('after', after)
      const response = await fetch('/api/participations?' + query, options)
      if (!response.ok) throw new Error()
      const page: Application[] = await response.json()
      const named = await Promise.all(page.map(async (application): Promise<Entry> => {
        try {
          const channel = await fetch('/api/catalog/streamers/' + application.channelId, options)
          if (channel.ok) {
            const { displayName } = await channel.json() as { displayName: string }
            return { ...application, channelName: displayName }
          }
        } catch {
          // An unavailable public profile should not hide a private application.
        }
        return application
      }))
      if (controller.signal.aborted) return
      setEntries(previous => after ? [...previous, ...named] : named)
      setNext(response.headers.get('X-Next-Cursor') ?? '')
    }
    load().catch(() => { if (!controller.signal.aborted) setLoadError(true) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [campaignId, after, reload])

  async function decide(entry: Entry, action: 'ACCEPT' | 'REJECT') {
    if (busy || loading) return
    setBusy(entry.id)
    setActionError('')
    try {
      const saved = await postJson<Application>('/api/participations/' + entry.id + '/decision',
        { action, expectedVersion: entry.version })
      setEntries(previous => previous.map(item => item.id === saved.id ? { ...item, ...saved } : item))
    } catch (failure) {
      setActionError(failure instanceof Error && failure.message === '409'
        ? 'The application changed or is no longer eligible. Refresh to see its current status.'
        : 'Could not update the application. Try again.')
    } finally { setBusy('') }
  }

  return <section className="space-y-3 border-t border-slate-200 pt-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-semibold">Applications</h3>
      <button type="button" className={button} disabled={!!busy} onClick={onClose}>Close applications</button>
    </div>
    {loading && <p role="status">Loading applications…</p>}
    {loadError && <p role="alert">Could not load applications. <button type="button" className="underline"
      onClick={() => { setLoadError(false); setLoading(true); setReload(value => value + 1) }}>Try again</button></p>}
    {!loading && !loadError && !entries.length && <p>No applications yet.</p>}
    <ul className="divide-y divide-slate-200">
      {entries.map(entry => <li key={entry.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
        <div className="min-w-0">
          {entry.channelName
            ? <a className="break-words font-medium text-sky-800 underline" href={'/streamers/' + entry.channelId}>{entry.channelName}</a>
            : <p className="break-words text-sm">Channel {entry.channelId}</p>}
          <p className="text-sm text-slate-600">{labels[entry.status]} · {new Date(entry.createdAt).toLocaleDateString('en')}</p>
        </div>
        {published && entry.status === 'PENDING' && <div className="flex flex-wrap gap-2">
          <button type="button" className={button + ' bg-sky-700 text-white'} disabled={!!busy || loading}
            onClick={() => void decide(entry, 'ACCEPT')}>Accept</button>
          <button type="button" className={button} disabled={!!busy || loading}
            onClick={() => void decide(entry, 'REJECT')}>Decline</button>
        </div>}
      </li>)}
    </ul>
    {actionError && <p role="alert" className="text-sm text-red-700">{actionError}{' '}
      <button type="button" className="underline" onClick={() => {
        setActionError(''); setLoadError(false); setLoading(true); setAfter(''); setReload(value => value + 1)
      }}>Refresh applications</button>
    </p>}
    {next && !loading && !loadError && <button type="button" className={button} disabled={!!busy}
      onClick={() => { setLoading(true); setAfter(next) }}>Show more</button>}
  </section>
}
