import { useEffect, useState } from 'react'

export type Streamer = { channelId: string; displayName: string; platform: string; channelDescription: string | null }
export type PublishedCampaign = { id: string; title: string; description: string; platforms: string[] }
type Audience = {
  measuredAt: string
  state: 'LIVE' | 'OFFLINE' | 'UNKNOWN'
  viewers: number | null
  averageViewers: number | null
  peakViewers: number | null
  viewerSamples: number
}
type Page = { streamers: Streamer[]; nextCursor: string | null; audience: Record<string, Audience> }
  | { campaigns: PublishedCampaign[]; nextBefore: string | null; nextBeforeId: string | null }
type Entry = Streamer | PublishedCampaign
export const platforms: Record<string, string> = { TWITCH: 'Twitch', YOUTUBE: 'YouTube', KICK: 'Kick' }

export function StreamerPhoto({ channel }: { channel: Streamer }) {
  const [missing, setMissing] = useState(false)
  return (
    <div className="grid aspect-square w-full place-items-center overflow-hidden rounded-xl bg-sky-100 text-4xl font-semibold text-sky-800">
      {missing ? channel.displayName.slice(0, 1).toUpperCase()
        : <img className="size-full object-cover" src={'/api/catalog/streamers/' + channel.channelId + '/photo'}
            alt={channel.displayName + ' profile photo'} onError={() => setMissing(true)} loading="lazy" />}
    </div>
  )
}

const viewerNumber = new Intl.NumberFormat('en', { maximumFractionDigits: 1 })

function AudienceStats({ audience }: { audience?: Audience }) {
  const recent = audience && Date.now() - Date.parse(audience.measuredAt) < 5 * 60_000
  const live = recent && audience.state === 'LIVE'
  const status = live ? 'Live' : recent && audience.state === 'OFFLINE' ? 'Offline' : 'Status unavailable'
  return (
    <div className="space-y-1 text-xs text-slate-600">
      <p className="font-semibold text-slate-900">Statistics</p>
      <p className={live ? 'font-semibold text-red-700' : 'text-slate-700'}>{status}</p>
      <dl className="grid grid-cols-[1fr_auto] gap-x-2 gap-y-1 tabular-nums">
        <dt>Watching</dt><dd className="font-semibold">{viewerNumber.format(live ? audience.viewers ?? 0 : 0)}</dd>
        <dt>Est. avg. (30d)</dt><dd className="font-semibold">{viewerNumber.format(audience?.averageViewers ?? 0)}</dd>
        <dt>Peak (30d)</dt><dd className="font-semibold">{viewerNumber.format(audience?.peakViewers ?? 0)}</dd>
        <dt>Samples (30d)</dt><dd className="font-semibold">{viewerNumber.format(audience?.viewerSamples ?? 0)}</dd>
      </dl>
    </div>
  )
}

export function StreamerCatalog({ campaigns = false }: { campaigns?: boolean }) {
  const [channels, setChannels] = useState<Entry[]>([])
  const [audience, setAudience] = useState<Record<string, Audience>>({})
  const [after, setAfter] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [platform, setPlatform] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: '20' })
    if (platform) query.set('platform', platform)
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
          setAudience(previous => ({ ...(after ? previous : {}), ...page.audience }))
          setNext(page.nextCursor ? new URLSearchParams({ after: page.nextCursor }).toString() : null)
        }
      })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load the catalog. Refresh to try again.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [after, campaigns, platform])

  return (
    <main className={'mx-auto space-y-5 px-4 py-10 sm:px-8 ' + (campaigns ? 'max-w-7xl' : 'max-w-5xl')}>
      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-sky-100 pb-4">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">{campaigns ? 'Ad Campaigns' : 'Streamers'}</h1>
        {!campaigns && <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
          Platform
          <select value={platform} className="rounded-lg border border-slate-300 bg-white px-3 py-2 focus-visible:outline-2 focus-visible:outline-sky-700"
            onChange={event => {
              setPlatform(event.target.value)
              setAfter(null)
              setNext(null)
              setChannels([])
              setError('')
              setLoading(true)
            }}>
            <option value="">All platforms</option>
            {Object.entries(platforms).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>}
      </div>
      {loading && <p role="status">Loading catalog…</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !error && !channels.length && <p>{campaigns ? 'No published campaigns yet.' : (platform ? 'No listed streamers on ' + platforms[platform] + '.' : 'No listed streamers yet.')}</p>}
      <div className={campaigns ? 'grid gap-4 sm:grid-cols-2 lg:grid-cols-3' : 'grid max-w-md justify-items-start gap-3'}>
        {channels.map(channel => 'id' in channel ? <article key={channel.id} className="min-w-0 rounded-2xl border border-sky-100 bg-white p-4">
          <h2 className="break-words font-semibold">{channel.title}</h2>
          <p className="mt-1 text-sm text-slate-600">{channel.platforms.map(platform => platforms[platform] ?? platform).join(' · ')}</p>
          <p className="mt-2 whitespace-pre-line break-words text-sm text-slate-700">{channel.description}</p>
          <a href={'/campaigns/' + channel.id} className="mt-3 inline-block text-sm font-medium text-sky-800 hover:underline">View campaign</a>
        </article> : <article key={channel.channelId} className="min-w-0 max-w-full rounded-xl border border-slate-200 bg-white hover:border-sky-300">
          <a href={'/streamers/' + channel.channelId}
            className="grid h-full min-w-0 grid-cols-[auto_minmax(0,1fr)] items-start gap-3 rounded-xl p-3 focus-visible:outline-2 focus-visible:outline-sky-700 sm:grid-cols-[auto_minmax(0,max-content)_7.5rem] sm:gap-2">
            <div className="w-14 shrink-0"><StreamerPhoto channel={channel} /></div>
            <div className="min-w-0 flex-1">
              <h2 className="break-words font-semibold text-slate-900">{channel.displayName}</h2>
              <p className="mt-0.5 text-sm text-slate-600">{platforms[channel.platform] ?? channel.platform}</p>
              {channel.channelDescription && <p className="mt-2 line-clamp-2 break-words text-sm text-slate-700">{channel.channelDescription}</p>}
              <span className="mt-3 inline-block text-sm font-medium text-sky-800">View channel →</span>
            </div>
            <aside aria-label="Audience statistics" className="col-span-2 border-t border-slate-200 pt-3 sm:col-span-1 sm:border-l sm:border-t-0 sm:pl-2 sm:pt-0">
              <AudienceStats audience={audience[channel.channelId]} />
            </aside>
          </a>
        </article>)}
      </div>
      {next && !loading && !error && <button type="button" className="rounded-lg bg-sky-100 px-4 py-2 font-semibold text-sky-900"
        onClick={() => { setLoading(true); setAfter(next) }}>Show more</button>}
    </main>
  )
}
