import { PlacementHours } from './PlacementHours'
import { useReservations, overlaps } from './useReservations'
import { useEffect, useState, type FormEvent } from 'react'
import type { Offer } from './OffersPage'
import type { Placement } from './StreamerDetails'
import { StreamAreaPicker, type Rectangle } from './StreamAreaPicker'
import { postJson } from './offerApi'

const timeFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

const field = 'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2'
const button = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold disabled:opacity-50'

export function CounterOfferForm({ offer, close, onSaved, onBusy }: {
  offer: Offer; close: () => void; onSaved: (offer: Offer) => void; onBusy: (busy: boolean) => void
}) {
  const [placement, setPlacement] = useState<Placement | null>(null)
  const [rectangle, setRectangle] = useState<Rectangle | null>(offer.terms.rectangle)
  const [start, setStart] = useState(() => {
    if (!offer.terms.startsAt) return ''
    const date = new Date(offer.terms.startsAt)
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 19)
  })
  const [duration, setDuration] = useState(String(offer.terms.durationSeconds))
  const [chosenInstant, setChosenInstant] = useState<string | null>(offer.terms.startsAt)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState(false)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/catalog/placements/' + offer.placementId, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const current: Placement = await response.json()
        if (!controller.signal.aborted) setPlacement(current)
      })
      .catch(() => { if (!controller.signal.aborted) setLoadError(true) })
    return () => controller.abort()
  }, [offer.placementId, retry])

  const startsAt = chosenInstant ?? (Number.isFinite(Date.parse(start)) ? new Date(start).toISOString() : '')
  const seconds = placement && !placement.negotiable ? placement.durationSeconds : Number(duration)
  const { reservations, error: calendarError, loading: calendarLoading, refresh } =
    useReservations(placement?.channelId, startsAt, seconds)
  const occupied = reservations?.map(reservation => reservation.rectangle) ?? null
  const blocked = !!rectangle && !!occupied?.some(area => overlaps(rectangle, area))
  const outsideZone = rectangle && placement?.zone && (
    rectangle.x < placement.zone.x || rectangle.y < placement.zone.y
    || rectangle.x + rectangle.width > placement.zone.x + placement.zone.width
    || rectangle.y + rectangle.height > placement.zone.y + placement.zone.height)
  const canSend = !!placement && !!rectangle && !!occupied && !blocked && !outsideZone

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSend || !placement || busy) return
    const values = new FormData(event.currentTarget)
    if (Date.parse(startsAt) <= Date.now()) { setError('Choose a future start time.'); return }
    setBusy(true)
    onBusy(true)
    setError('')
    try {
      onSaved(await postJson<Offer>('/api/offers/' + offer.id + '/counter', {
        expectedVersion: offer.version,
        terms: {
          ...offer.terms, rectangle, mode: 'SCHEDULED', startsAt,
          price: placement.negotiable ? values.get('price') : placement.minimumPrice,
          durationSeconds: seconds,
        },
      }))
    } catch (failure) {
      setError(failure instanceof Error && failure.message === '409'
        ? 'The offer changed or the proposed terms do not meet the placement conditions. Refresh and review the offer.'
        : 'Could not send the revised offer. Check the details and try again.')
    } finally { setBusy(false); onBusy(false) }
  }

  return <form onSubmit={submit} className="space-y-3 rounded-lg border border-sky-200 p-3 text-sm">
    <h3 className="font-semibold">Suggest changes</h3>
    {!placement && !loadError && <p role="status">Loading placement conditions…</p>}
    {loadError && <p role="alert">This placement could not be loaded. <button type="button" className="underline"
      onClick={() => { setLoadError(false); setRetry(value => value + 1) }}>Try again</button></p>}
    {placement && <fieldset disabled={busy} className="space-y-3">
      <label className="block">Price ({placement.currency})<input name="price" className={field} type="number"
        min={0} step="0.01" max={9999999999.99} required readOnly={!placement.negotiable}
        defaultValue={placement.negotiable ? offer.terms.price : placement.minimumPrice ?? ''} /></label>
      <label className="block">Duration (seconds)<input name="duration" className={field} type="number"
        min={1} max={604800} required readOnly={!placement.negotiable}
        value={placement.negotiable ? duration : String(placement.durationSeconds)} onChange={event => setDuration(event.target.value)} /></label>
      <label className="block">Start time (local)<input className={field} type="datetime-local" required step={1}
        value={start} onChange={event => { setStart(event.target.value); setChosenInstant(null) }} /></label>
      <PlacementHours placementId={offer.placementId} duration={seconds} onChoose={instant => {
        if (busy || instant === startsAt) return
        const date = new Date(instant)
        setStart(new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 19))
        setChosenInstant(instant)
        setError('')
      }} />
      <p>Draw the proposed ad area{placement.zone ? ' inside the outlined zone' : ''}. Red areas are reserved for the selected time.</p>
      <StreamAreaPicker area={rectangle} onChange={setRectangle}
        zone={placement.zone} occupied={occupied ?? []} disabled={busy || !occupied} />
      {calendarLoading && <p role="status">Checking reservations…</p>}
      {calendarError && <p role="alert">{calendarError} <button type="button" className="underline" onClick={refresh}>Try again</button></p>}
      {!!reservations?.length && <details>
        <summary className="w-fit cursor-pointer text-sky-800">Reserved intervals ({reservations.length})</summary>
        <ul className="mt-2 max-h-40 space-y-1 overflow-auto text-slate-700">
          {reservations.map((reservation, index) => <li key={index}>
            {timeFormat.formatRange(new Date(reservation.startsAt), new Date(reservation.endsAt))}
          </li>)}
        </ul>
      </details>}
      {blocked && <p role="alert">The selected area overlaps a reservation. Choose a different area or time.</p>}
      {outsideZone && <p role="alert">Redraw the ad area inside the current placement zone.</p>}
      <button className={button + ' bg-sky-700 text-white'} disabled={busy || !canSend}>{busy ? 'Sending…' : 'Send revised offer'}</button>
    </fieldset>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    <button type="button" className={button} disabled={busy} onClick={close}>Cancel changes</button>
  </form>
}
