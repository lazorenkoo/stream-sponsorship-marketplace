import { useEffect, useState } from 'react'
import { statusLabels, type Participation } from './CampaignDetails'
import type { PublishedCampaign } from './StreamerCatalog'

type Activity = Participation & { campaignId: string; initiatedBy: string }
type Entry = Activity & { title: string | null; channelName?: string }
type LoadStatus = 'loading' | 'ready' | 'guest' | 'forbidden' | 'error'
const button = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium hover:bg-sky-50'

export function CampaignActivity() {
  const [entries, setEntries] = useState<Entry[]>([])
  const [after, setAfter] = useState('')
  const [next, setNext] = useState('')
  const [retry, setRetry] = useState(0)
  const [status, setStatus] = useState<LoadStatus>('loading')

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal, cache: 'no-store' as const }
    async function load() {
      const session = await fetch('/api/users/me', options)
      if (controller.signal.aborted) return
      if (session.status === 401) { setStatus('guest'); return }
      if (!session.ok) throw new Error()
      const profile: { id: string; roles: string[] } = await session.json()
      if (controller.signal.aborted) return
      if (!profile.roles.includes('STREAMER')) { setStatus('forbidden'); return }

      let cursor = after
      let page: Activity[]
      do {
        const query = new URLSearchParams({ limit: '20' })
        if (cursor) query.set('after', cursor)
        const response = await fetch('/api/participations?' + query, options)
        if (!response.ok) throw new Error()
        const requests: Activity[] = await response.json()
        page = requests.filter(request => request.direction === 'APPLICATION'
          ? request.initiatedBy === profile.id
          : request.initiatedBy !== profile.id)
        cursor = response.headers.get('X-Next-Cursor') ?? ''
      } while (cursor && page.length === 0)

      const ids = [...new Set(page.map(request => request.campaignId))]
      const [channels, titles] = await Promise.all([
        fetch('/api/channels', options).then(async response => {
          if (!response.ok) throw new Error()
          const channels: { id: string; displayName: string }[] = await response.json()
          return new Map(channels.map(channel => [channel.id, channel.displayName]))
        }),
        Promise.all(ids.map(async id => {
          const response = await fetch('/api/catalog/campaigns/' + id, options)
          if (response.status === 404) return [id, null] as const
          if (!response.ok) throw new Error()
          const campaign: PublishedCampaign = await response.json()
          return [id, campaign.title] as const
        })).then(titles => new Map(titles)),
      ])
      if (controller.signal.aborted) return

      const loaded = page.map(request => ({
        ...request,
        title: titles.get(request.campaignId) ?? null,
        channelName: channels.get(request.channelId),
      }))
      setEntries(previous => after ? [...previous, ...loaded] : loaded)
      setNext(cursor)
      setStatus('ready')
    }
    load().catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [after, retry])

  return <main className="mx-auto max-w-3xl space-y-4 px-4 py-8 text-slate-900">
    <a href="/campaigns" className="text-sm font-medium text-sky-800 hover:underline">Browse ad campaigns</a>
    <h1 className="text-2xl font-semibold">Campaign activity</h1>
    {status === 'guest' && <p><a href="/login" className="text-sky-800 underline">Log in</a> to view your campaign activity.</p>}
    {status === 'forbidden' && <p>A streamer account is required to view campaign activity.</p>}
    {status === 'loading' && <p role="status">Loading campaign activity…</p>}
    {status === 'error' && <p role="alert">Could not load campaign activity. <button type="button" className="underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {status === 'ready' && !entries.length && <p>You have no campaign invitations or applications yet.</p>}
    <ul className="space-y-3">
      {entries.map(entry => <li key={entry.id} className="space-y-1 rounded-xl border border-sky-100 bg-white p-4">
        <p className="text-sm text-slate-600">
          {entry.direction === 'INVITATION' ? 'Invitation' : 'Application'} · {statusLabels[entry.status]}
        </p>
        <h2 className="break-words font-semibold">{entry.title ?? 'Campaign unavailable'}</h2>
        {entry.channelName && <p className="break-words text-sm text-slate-600">{entry.channelName}</p>}
        {entry.title && <a href={'/campaigns/' + entry.campaignId} className="inline-block text-sm font-medium text-sky-800 underline">
          {entry.direction === 'INVITATION' && entry.status === 'PENDING' ? 'Review invitation' : 'View campaign'}
        </a>}
      </li>)}
    </ul>
    {status === 'ready' && next && <button type="button" className={button}
      onClick={() => { setStatus('loading'); setAfter(next) }}>Show more</button>}
  </main>
}
