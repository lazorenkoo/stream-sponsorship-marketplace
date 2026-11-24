import { useReservations, overlaps } from './useReservations'
import { postJson } from './offerApi'
import { PlacementHours } from './PlacementHours'
import { useEffect, useState, type FormEvent } from 'react'
import { StreamAreaPicker, type Rectangle } from './StreamAreaPicker'
import type { Placement } from './StreamerDetails'

type Creative = { id: string; title: string }
type Draft = { id: string; version: number; status: string }
const timeFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

const input = 'block w-full rounded-lg border border-slate-300 px-3 py-2'
const primary = 'rounded-lg bg-sky-700 px-4 py-2 font-semibold text-white disabled:opacity-50'

export function OfferForm({ channelId, campaignId, placement, close }: { channelId: string; campaignId: string | null; placement: Placement; close: () => void }) {
  const [creatives, setCreatives] = useState<Creative[] | null>(null)
  const [start, setStart] = useState('')
  const [chosenInstant, setChosenInstant] = useState<string | null>(null)
  const [duration, setDuration] = useState(String(placement.durationSeconds))
  const [rectangle, setRectangle] = useState<Rectangle | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const startsAt = Date.parse(chosenInstant ?? start)
  const startInstant = chosenInstant ?? (Number.isFinite(startsAt) ? new Date(startsAt).toISOString() : '')
  const seconds = Number(duration)
  const { reservations, error: calendarError, loading: calendarLoading, refresh: refreshReservations } =
    useReservations(channelId, startInstant, seconds)
  const occupied = reservations?.map(reservation => reservation.rectangle) ?? null
  const blocked = rectangle && occupied?.some(area => overlaps(rectangle, area))

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      const available: Creative[] = []
      let after = ''
      while (true) {
        const query = new URLSearchParams({ limit: '100' })
        if (after) query.set('after', after)
        const response = await fetch('/api/creatives?' + query, { signal: controller.signal, cache: 'no-store' })
        if (!response.ok) throw new Error()
        const page: Creative[] = await response.json()
        available.push(...page)
        if (page.length < 100) break
        after = page[page.length - 1].id
      }
      if (!controller.signal.aborted) setCreatives(available)
    }
    load().catch(() => { if (!controller.signal.aborted) setError('Could not load your creatives. Reopen the form to retry.') })
    return () => controller.abort()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || sent || !rectangle?.width || !rectangle.height || !occupied || blocked) return
    if (startsAt <= Date.now()) { setError('Choose a start time in the future.'); return }
    const data = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    let saved = draft
    try {
      saved ??= await postJson<Draft>('/api/offers', { placementId: placement.id, campaignId, terms: {
        creativeId: data.get('creativeId'), price: placement.negotiable ? data.get('price') : placement.minimumPrice,
        currency: placement.currency, durationSeconds: seconds, mode: 'SCHEDULED',
        startsAt: startInstant, advertisingCategory: data.get('category'), rectangle,
      } })
      setDraft(saved)
      await postJson('/api/offers/' + saved.id + '/send', { expectedVersion: saved.version })
      setSent(true)
    } catch (failure) {
      setError(saved
        ? 'The offer was saved as a draft. Check the placement availability and retry sending.'
        : failure instanceof Error && failure.message === '409'
          ? 'This placement, campaign or creative is no longer eligible for the offer.'
          : 'Could not create the offer. Check the terms and try again.')
    } finally { setBusy(false) }
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-sky-200 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold">{campaignId ? 'Make a campaign offer' : 'Make an offer'} · {placement.title}</h2>
        <button type="button" disabled={busy} onClick={close} className="text-sm text-sky-800 underline">Close</button>
      </div>
      {sent ? <p role="status">Offer sent. The streamer can now review it.{' '}
        <a href="/offers" className="text-sky-800 underline">View offers</a>
      </p> : <>
        <fieldset disabled={busy || !!draft} className="grid gap-3 sm:grid-cols-2">
          <label>Creative<select name="creativeId" required className={input}>
            <option value="">{creatives === null ? 'Loading creatives…' : 'Choose a creative'}</option>
            {creatives?.map(creative => <option key={creative.id} value={creative.id}>{creative.title}</option>)}
          </select></label>
          <label>Advertising category
            {placement.unrestrictedCategories
              ? <input name="category" className={input} required maxLength={40} pattern="[a-z0-9]+(-[a-z0-9]+)*" placeholder="gaming" />
              : <select name="category" className={input} required>{placement.allowedCategories.map(category => <option key={category}>{category}</option>)}</select>}
          </label>
          <label>Start time (local)<input type="datetime-local" step={1} required className={input} value={start}
            onChange={event => { setStart(event.target.value); setChosenInstant(null) }} /></label>
          <label>Duration (seconds)<input type="number" required min={1} max={604800} className={input}
            readOnly={!placement.negotiable} value={duration}
            onChange={event => { setDuration(event.target.value) }} /></label>
          <label>Price ({placement.currency})<input type="number" name="price" required min={0} max={9999999999.99}
            step="0.01" className={input} readOnly={!placement.negotiable} defaultValue={placement.minimumPrice?.toFixed(2) ?? ''} /></label>
        </fieldset>
        {!draft && <PlacementHours placementId={placement.id} duration={seconds} onChoose={instant => {
          if (busy || instant === startInstant) return
          const date = new Date(instant)
          setStart(new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 19))
          setChosenInstant(instant)
        }} />}
        {creatives?.length === 0 && <p>Upload a creative to send with your offer. <a className="text-sky-800 underline" href="/creatives">Manage creatives</a></p>}
        <p className="text-sm">{placement.zone
          ? 'Draw your ad area inside the outlined zone.'
          : 'Draw your ad area anywhere on the layout.'} Red areas are reserved for the selected time.</p>
        <StreamAreaPicker area={rectangle} onChange={setRectangle} zone={placement.zone} occupied={occupied ?? []}
          disabled={!!draft || busy || !occupied} />
        {calendarLoading && <p role="status">Checking reservations…</p>}
        {calendarError && <p role="alert">{calendarError} <button type="button" className="underline"
          onClick={refreshReservations}>Retry</button></p>}
        {!!reservations?.length && <details className="text-sm">
          <summary className="w-fit cursor-pointer text-sky-800">Reserved intervals ({reservations.length})</summary>
          <ul className="mt-2 max-h-40 space-y-1 overflow-auto text-slate-700">
            {reservations.map((reservation, index) => <li key={index}>
              {timeFormat.formatRange(new Date(reservation.startsAt), new Date(reservation.endsAt))}
            </li>)}
          </ul>
        </details>}
        {blocked && <p role="alert">The selected area overlaps a reservation.</p>}
        {error && <p role="alert" className="text-red-700">{error}{draft && <> <a href="/offers"
          className="underline">Open saved drafts</a></>}</p>}
        <button className={primary} disabled={busy || !occupied || !rectangle?.width || !rectangle.height || !!blocked || !creatives?.length}>
          {busy ? 'Sending…' : draft ? 'Retry sending' : 'Send offer'}
        </button>
      </>}
    </form>
  )
}
