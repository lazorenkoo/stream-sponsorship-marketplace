import { useEffect, useState } from 'react'

type Streamer = { channelId: string; displayName: string; platform: string; channelDescription: string | null }
type Page = { streamers: Streamer[]; nextCursor: string | null }

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

export function StreamerCatalog() {
  const [channels, setChannels] = useState<Streamer[]>([])
  const [after, setAfter] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [signedOut, setSignedOut] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: '20' })
    if (after) query.set('after', after)
    fetch('/api/catalog/streamers?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (response.status === 401) { setSignedOut(true); return }
        if (!response.ok) throw new Error()
        const page: Page = await response.json()
        if (controller.signal.aborted) return
        setChannels(previous => after ? [...previous, ...page.streamers] : page.streamers)
        setNext(page.nextCursor)
      })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load streamers. Refresh to try again.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [after])

  return (
    <main className="mx-auto max-w-7xl space-y-5 px-4 py-10 sm:px-8">
      <h1 className="text-3xl font-bold text-slate-900">Streamers</h1>
      {signedOut && <p><a className="text-sky-800 underline" href="/login">Log in</a> to browse streamers.</p>}
      {loading && <p role="status">Loading streamers…</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !error && !signedOut && !channels.length && <p>No listed streamers yet.</p>}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {channels.map(channel => <article key={channel.channelId} className="min-w-0 rounded-2xl border border-sky-100 bg-white p-3">
          <StreamerPhoto channel={channel} />
          <h2 className="mt-3 break-words font-semibold">{channel.displayName}</h2>
          <p className="text-sm text-slate-600">{channel.platform}</p>
          {channel.channelDescription && <p className="mt-2 whitespace-pre-line break-words text-sm text-slate-700">{channel.channelDescription}</p>}
        </article>)}
      </div>
      {next && !loading && !error && !signedOut && <button type="button" className="rounded-lg bg-sky-100 px-4 py-2 font-semibold text-sky-900"
        onClick={() => { setLoading(true); setAfter(next) }}>Show more</button>}
    </main>
  )
}
