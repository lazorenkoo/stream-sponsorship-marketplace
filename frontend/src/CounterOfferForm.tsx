import { useEffect, useState, type FormEvent } from 'react'
import type { Offer } from './OffersPage'
import type { Placement } from './StreamerDetails'
import { StreamAreaPicker } from './StreamAreaPicker'
import { postJson } from './offerApi'

const field = 'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2'
const button = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold disabled:opacity-50'

export function CounterOfferForm({ offer, close, onSaved, onBusy }: {
  offer: Offer; close: () => void; onSaved: (offer: Offer) => void; onBusy: (busy: boolean) => void
}) {
  const [placement, setPlacement] = useState<Placement | null>(null)
  const [rectangle, setRectangle] = useState(offer.terms.rectangle)
  const [start, setStart] = useState(() => {
    if (!offer.terms.startsAt) return ''
    const date = new Date(offer.terms.startsAt)
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 19)
  })
  const [timeEdited, setTimeEdited] = useState(false)
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!placement || busy) return
    const values = new FormData(event.currentTarget)
    const startsAt = timeEdited || !offer.terms.startsAt ? new Date(start).toISOString() : offer.terms.startsAt
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
          durationSeconds: placement.negotiable ? Number(values.get('duration')) : placement.durationSeconds,
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
        defaultValue={placement.negotiable ? offer.terms.durationSeconds : placement.durationSeconds} /></label>
      <label className="block">Start time (local)<input className={field} type="datetime-local" required step={1}
        value={start} onChange={event => { setStart(event.target.value); setTimeEdited(true) }} /></label>
      <p>Draw the proposed ad area{placement.zone ? ' inside the outlined zone' : ''}.</p>
      <StreamAreaPicker area={rectangle} onChange={area => { if (area) setRectangle(area) }}
        zone={placement.zone} disabled={busy} />
      <button className={button + ' bg-sky-700 text-white'} disabled={busy}>{busy ? 'Sending…' : 'Send revised offer'}</button>
    </fieldset>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    <button type="button" className={button} disabled={busy} onClick={close}>Cancel changes</button>
  </form>
}
