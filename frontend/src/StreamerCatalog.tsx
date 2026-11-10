import { useEffect, useState } from 'react'

type Streamer = { channelId: string; displayName: string; platform: string; channelDescription: string | null }
type Campaign = { id: string; title: string; description: string; platforms: string[] }
type Page = { streamers: Streamer[]; nextCursor: string | null }
  | { campaigns: Campaign[]; nextBefore: string | null; nextBeforeId: string | null }
type Entry = Streamer | Campaign

function StreamerPhoto({ channel }: { channel: Streamer }) {
  const [missing, setMissing] = useState(false)
  return (
    <div className="grid aspect-square w-full place-items-center overflow-hidden rounded-xl bg-sky-100 text-4xl font-semibold text-sky-800">
      {missing ? channel.displayName.slice(0, 1).toUpperCase()
        : <img className="size-full object-cover" src={'/api/catalog/streamers/' + channel.channelId + '/photo'}
            alt={channel.displayName + ' profile photo'} onError={() => setMissing(true)} loading="lazy" />}
    </div>
  )
}

export function StreamerCatalog({ campaigns = false }: { campaigns?: boolean }) {
  const [channels, setChannels] = useState<Entry[]>([])
  const [after, setAfter] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: '20' })
    const endpoint = campaigns ? 'campaigns' : 'streamers'
    fetch('/api/catalog/' + endpoint + '?' + query + (after ? '&' + after : ''), { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const page: Page = await response.json()
        if (controller.signal.aborted) return
        const entries = 'campaigns' in page ? page.campaigns : page.streamers
        setChannels(previous => after ? [...previous, ...entries] : entries)
        if ('campaigns' in page) {
          setNext(page.nextBefore && page.nextBeforeId
            ? new URLSearchParams({ before: page.nextBefore, beforeId: page.nextBeforeId }).toString() : null)
        } else {
          setNext(page.nextCursor ? new URLSearchParams({ after: page.nextCursor }).toString() : null)
        }
      })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load the catalog. Refresh to try again.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [after, campaigns])

  return (
    <main className="mx-auto max-w-7xl space-y-5 px-4 py-10 sm:px-8">
      <h1 className="text-3xl font-bold text-slate-900">{campaigns ? 'Ad Campaigns' : 'Streamers'}</h1>
      {loading && <p role="status">Loading catalog…</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !error && !channels.length && <p>{campaigns ? 'No published campaigns yet.' : 'No listed streamers yet.'}</p>}
      <div className={campaigns ? 'grid gap-4 sm:grid-cols-2 lg:grid-cols-3' : 'grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5'}>
        {channels.map(channel => 'id' in channel ? <article key={channel.id} className="min-w-0 rounded-2xl border border-sky-100 bg-white p-4">
          <h2 className="break-words font-semibold">{channel.title}</h2>
          <p className="mt-1 text-sm text-slate-600">{channel.platforms.join(' · ')}</p>
          <p className="mt-2 whitespace-pre-line break-words text-sm text-slate-700">{channel.description}</p>
        </article> : <article key={channel.channelId} className="min-w-0 rounded-2xl border border-sky-100 bg-white p-3">
          <StreamerPhoto channel={channel} />
          <h2 className="mt-3 break-words font-semibold">{channel.displayName}</h2>
          <p className="text-sm text-slate-600">{channel.platform}</p>
          {channel.channelDescription && <p className="mt-2 whitespace-pre-line break-words text-sm text-slate-700">{channel.channelDescription}</p>}
        </article>)}
      </div>
      {next && !loading && !error && <button type="button" className="rounded-lg bg-sky-100 px-4 py-2 font-semibold text-sky-900"
        onClick={() => { setLoading(true); setAfter(next) }}>Show more</button>}
    </main>
  )
}
