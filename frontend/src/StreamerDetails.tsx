import { useEffect, useState } from 'react'
import { OfferForm } from './OfferForm'
import { StreamerPhoto, type Streamer } from './StreamerCatalog'

export type Placement = {
  id: string
  title: string
  position: string
  widthPx: number
  heightPx: number
  durationSeconds: number
  minimumPrice: number
  currency: string
  negotiable: boolean
  unrestrictedCategories: boolean
  allowedCategories: string[]
}

const pageSize = 20

export function StreamerDetails({ id }: { id: string }) {
  const [channel, setChannel] = useState<Streamer | null>(null)
  const [placements, setPlacements] = useState<Placement[]>([])
  const [after, setAfter] = useState('')
  const [next, setNext] = useState('')
  const [profileStatus, setProfileStatus] = useState('loading')
  const [placementStatus, setPlacementStatus] = useState('loading')
  const [retry, setRetry] = useState(0)
  const [canOffer, setCanOffer] = useState(false)
  const [selectedPlacement, setSelectedPlacement] = useState<Placement | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/users/me', { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) return
        const profile: { roles: string[] } = await response.json()
        if (!controller.signal.aborted) setCanOffer(profile.roles.includes('ADVERTISER'))
      })
      .catch(() => {})
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/catalog/streamers/' + id, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (response.status === 404) { setProfileStatus('missing'); return }
        if (!response.ok) throw new Error()
        const profile: Streamer = await response.json()
        if (!controller.signal.aborted) {
          setChannel(profile)
          setProfileStatus('ready')
        }
      })
      .catch(() => { if (!controller.signal.aborted) setProfileStatus('error') })
    return () => controller.abort()
  }, [id])

  useEffect(() => {
    if (!channel) return
    const controller = new AbortController()
    const query = new URLSearchParams({ channelId: id, limit: String(pageSize + 1) })
    if (after) query.set('after', after)
    fetch('/api/catalog/placements?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (response.status === 401) { setPlacementStatus('guest'); return }
        if (!response.ok) throw new Error()
        const page: Placement[] = await response.json()
        if (controller.signal.aborted) return
        setPlacements(previous => [...(after ? previous : []), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].id : '')
        setPlacementStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setPlacementStatus('error') })
    return () => controller.abort()
  }, [after, channel, id, retry])

  return (
    <main className="mx-auto max-w-4xl space-y-6 px-4 py-8 text-slate-900">
      <a className="text-sm font-medium text-sky-800 hover:underline" href="/">← All streamers</a>
      {profileStatus === 'loading' && <p role="status">Loading channel…</p>}
      {profileStatus === 'missing' && <p>This channel is no longer listed.</p>}
      {profileStatus === 'error' && <p role="alert">Could not load this channel. Refresh to try again.</p>}
      {channel && <>
        <header className="flex flex-wrap items-start gap-5 rounded-xl border border-sky-100 bg-white p-4 sm:p-5">
          <div className="w-28 shrink-0"><StreamerPhoto channel={channel} /></div>
          <div className="min-w-0 flex-1">
            <h1 className="break-words text-2xl font-semibold">{channel.displayName}</h1>
            <p className="text-sm text-slate-600">{channel.platform}</p>
            {channel.channelDescription && <p className="mt-3 whitespace-pre-line break-words">{channel.channelDescription}</p>}
          </div>
        </header>
        <section aria-labelledby="placements-heading" className="space-y-3">
          <h2 id="placements-heading" className="text-xl font-semibold">Available ad placements</h2>
          {placementStatus === 'loading' && <p role="status">Loading placements…</p>}
          {placementStatus === 'guest' && <p><a className="text-sky-800 underline" href="/login">Log in</a> to see available placements.</p>}
          {placementStatus === 'error' && <p role="alert">Could not load placements. <button type="button" className="text-sky-800 underline"
            onClick={() => { setPlacementStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
          {placementStatus === 'ready' && placements.length === 0 && <p>No ad placements are available right now.</p>}
          <ul className="grid gap-3 sm:grid-cols-2">
            {placements.map(placement => <li key={placement.id} className="min-w-0 space-y-1 rounded-xl border border-sky-100 bg-white p-4">
              <h3 className="break-words font-semibold">{placement.title}</h3>
              <p className="break-words text-sm text-slate-600">{placement.position} · {placement.widthPx} × {placement.heightPx} px · {placement.durationSeconds} s</p>
              <p className="text-sm font-medium">{placement.negotiable ? 'From ' : ''}{placement.minimumPrice.toFixed(2)} {placement.currency}</p>
              <p className="break-words text-sm text-slate-600">{placement.unrestrictedCategories ? 'All advertising categories' : placement.allowedCategories.join(', ')}</p>
              {canOffer && <button type="button" className="mt-2 rounded-lg bg-sky-100 px-3 py-2 text-sm font-semibold text-sky-900"
                onClick={() => setSelectedPlacement(placement)}>Make an offer</button>}
            </li>)}
          </ul>
          {next && placementStatus === 'ready' && <button type="button" className="rounded-lg bg-sky-100 px-4 py-2 font-semibold text-sky-900"
            onClick={() => { setPlacementStatus('loading'); setAfter(next) }}>Show more</button>}
        </section>
        {selectedPlacement && <OfferForm key={selectedPlacement.id} channelId={id} placement={selectedPlacement}
          close={() => setSelectedPlacement(null)} />}
      </>}
    </main>
  )
}
