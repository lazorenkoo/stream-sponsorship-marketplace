import { useEffect, useRef, useState, type FormEvent, type PointerEvent } from 'react'
import type { Placement } from './StreamerDetails'

type Point = { x: number; y: number }
type Rectangle = Point & { width: number; height: number }
type Creative = { id: string; title: string; status: string }
type Draft = { id: string; version: number; status: string }
type ReservationPage = { reservations: { rectangle: Rectangle }[]; nextCursor: string | null }

const canvasSize = 10000
const input = 'block w-full rounded-lg border border-slate-300 px-3 py-2'
const primary = 'rounded-lg bg-sky-700 px-4 py-2 font-semibold text-white disabled:opacity-50'

async function post(path: string, body: object): Promise<Draft> {
  const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
  if (!csrf.ok) throw new Error()
  const { headerName, token } = await csrf.json()
  const response = await fetch(path, {
    method: 'POST',
    headers: { [headerName]: token, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(String(response.status))
  return response.json()
}

function overlaps(a: Rectangle, b: Rectangle) {
  return a.x < b.x + b.width && b.x < a.x + a.width
    && a.y < b.y + b.height && b.y < a.y + a.height
}

export function OfferForm({ channelId, placement, close }: { channelId: string; placement: Placement; close: () => void }) {
  const [creatives, setCreatives] = useState<Creative[] | null>(null)
  const [start, setStart] = useState('')
  const [duration, setDuration] = useState(String(placement.durationSeconds))
  const [rectangle, setRectangle] = useState<Rectangle | null>(null)
  const [calendar, setCalendar] = useState<{ range: string; occupied: Rectangle[] } | null>(null)
  const [calendarError, setCalendarError] = useState('')
  const [retry, setRetry] = useState(0)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const anchor = useRef<Point | null>(null)

  const startsAt = new Date(start).getTime()
  const seconds = Number(duration)
  const range = Number.isFinite(startsAt) && Number.isInteger(seconds) && seconds > 0 && seconds <= 604800
    ? new URLSearchParams({ from: new Date(startsAt).toISOString(), to: new Date(startsAt + seconds * 1000).toISOString() }).toString() : ''
  const occupied = calendar?.range === range ? calendar.occupied : null
  const blocked = rectangle && occupied?.some(area => overlaps(rectangle, area))

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      const approved: Creative[] = []
      let after = ''
      while (true) {
        const query = new URLSearchParams({ limit: '100' })
        if (after) query.set('after', after)
        const response = await fetch('/api/creatives?' + query, { signal: controller.signal, cache: 'no-store' })
        if (!response.ok) throw new Error()
        const page: Creative[] = await response.json()
        approved.push(...page.filter(creative => creative.status === 'APPROVED'))
        if (page.length < 100) break
        after = page[page.length - 1].id
      }
      if (!controller.signal.aborted) setCreatives(approved)
    }
    load().catch(() => { if (!controller.signal.aborted) setError('Could not load your creatives. Reopen the form to retry.') })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!range) return
    const controller = new AbortController()
    async function load() {
      const areas: Rectangle[] = []
      let cursor: string | null = null
      do {
        const query = new URLSearchParams(range)
        query.set('limit', '100')
        if (cursor) query.set('cursor', cursor)
        const response = await fetch('/api/catalog/channels/' + channelId + '/reservations?' + query,
          { signal: controller.signal, cache: 'no-store' })
        if (!response.ok) throw new Error()
        const page: ReservationPage = await response.json()
        areas.push(...page.reservations.map(reservation => reservation.rectangle))
        cursor = page.nextCursor
      } while (cursor)
      if (!controller.signal.aborted) setCalendar({ range, occupied: areas })
    }
    load().catch(() => { if (!controller.signal.aborted) setCalendarError('Could not check reservations.') })
    return () => controller.abort()
  }, [channelId, range, retry])

  function point(event: PointerEvent<SVGSVGElement>): Point {
    const bounds = event.currentTarget.getBoundingClientRect()
    const normalize = (value: number) => Math.max(0, Math.min(canvasSize, Math.round(value * canvasSize)))
    return { x: normalize((event.clientX - bounds.left) / bounds.width), y: normalize((event.clientY - bounds.top) / bounds.height) }
  }

  function draw(event: PointerEvent<SVGSVGElement>) {
    if (!anchor.current) return
    const end = point(event)
    const { x, y } = anchor.current
    setRectangle({ x: Math.min(x, end.x), y: Math.min(y, end.y), width: Math.abs(end.x - x), height: Math.abs(end.y - y) })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || sent || !rectangle?.width || !rectangle.height || !occupied || blocked) return
    if (startsAt <= Date.now()) { setError('Choose a start time in the future.'); return }
    const data = new FormData(event.currentTarget)
    setBusy(true)
    setError('')
    let saved = draft
    try {
      saved ??= await post('/api/offers', { placementId: placement.id, terms: {
        creativeId: data.get('creativeId'), price: placement.negotiable ? data.get('price') : placement.minimumPrice,
        currency: placement.currency, durationSeconds: seconds, mode: 'SCHEDULED',
        startsAt: new Date(startsAt).toISOString(), advertisingCategory: data.get('category'), rectangle,
      } })
      setDraft(saved)
      await post('/api/offers/' + saved.id + '/send', { expectedVersion: saved.version })
      setSent(true)
    } catch (failure) {
      setError(saved
        ? 'The offer was saved as a draft. Check the placement availability and retry sending.'
        : failure instanceof Error && failure.message === '409'
          ? 'This placement or creative is no longer eligible for the offer.'
          : 'Could not create the offer. Check the terms and try again.')
    } finally { setBusy(false) }
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-sky-200 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold">Make an offer · {placement.title}</h2>
        <button type="button" disabled={busy} onClick={close} className="text-sm text-sky-800 underline">Close</button>
      </div>
      {sent ? <p role="status">Offer sent. The streamer can now review it.</p> : <>
        <fieldset disabled={busy || !!draft} className="grid gap-3 sm:grid-cols-2">
          <label>Creative<select name="creativeId" required className={input}>
            <option value="">{creatives === null ? 'Loading creatives…' : 'Choose an approved creative'}</option>
            {creatives?.map(creative => <option key={creative.id} value={creative.id}>{creative.title}</option>)}
          </select></label>
          <label>Advertising category
            {placement.unrestrictedCategories
              ? <input name="category" className={input} required maxLength={40} pattern="[a-z0-9]+(-[a-z0-9]+)*" placeholder="gaming" />
              : <select name="category" className={input} required>{placement.allowedCategories.map(category => <option key={category}>{category}</option>)}</select>}
          </label>
          <label>Start time (local)<input type="datetime-local" required className={input} value={start}
            onChange={event => { setStart(event.target.value); setCalendar(null); setCalendarError('') }} /></label>
          <label>Duration (seconds)<input type="number" required min={1} max={604800} className={input}
            readOnly={!placement.negotiable} value={duration}
            onChange={event => { setDuration(event.target.value); setCalendar(null); setCalendarError('') }} /></label>
          <label>Price ({placement.currency})<input type="number" name="price" required min={0} max={9999999999.99}
            step="0.01" className={input} readOnly={!placement.negotiable} defaultValue={placement.minimumPrice.toFixed(2)} /></label>
        </fieldset>
        {creatives?.length === 0 && <p>No approved creatives are available on this account.</p>}
        <p className="text-sm">Drag to draw an ad area. Red areas are reserved for the selected time.</p>
        <svg viewBox="0 0 10000 10000" preserveAspectRatio="none" aria-label="Stream layout"
          className="block aspect-video w-full touch-none cursor-crosshair rounded-lg bg-slate-100"
          onPointerDown={event => {
            if (draft || busy || !occupied || !event.isPrimary || event.button !== 0) return
            anchor.current = point(event)
            setRectangle(null)
            event.currentTarget.setPointerCapture(event.pointerId)
          }}
          onPointerMove={draw}
          onPointerUp={event => { draw(event); anchor.current = null }}
          onLostPointerCapture={() => { anchor.current = null }}>
          {occupied?.map((area, index) => <rect key={index} {...area} fill="#fecaca" stroke="#b91c1c" strokeWidth="35" />)}
          {rectangle && <rect {...rectangle} fill="#7dd3fc" fillOpacity=".65" stroke="#0369a1" strokeWidth="35" />}
        </svg>
        {range && !occupied && !calendarError && <p role="status">Checking reservations…</p>}
        {calendarError && <p role="alert">{calendarError} <button type="button" className="underline"
          onClick={() => { setCalendarError(''); setRetry(value => value + 1) }}>Retry</button></p>}
        {blocked && <p role="alert">The selected area overlaps a reservation.</p>}
        {error && <p role="alert" className="text-red-700">{error}</p>}
        <button className={primary} disabled={busy || !occupied || !rectangle?.width || !rectangle.height || !!blocked || !creatives?.length}>
          {busy ? 'Sending…' : draft ? 'Retry sending' : 'Send offer'}
        </button>
      </>}
    </form>
  )
}
