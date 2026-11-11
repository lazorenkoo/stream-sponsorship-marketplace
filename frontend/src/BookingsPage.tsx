import { useEffect, useState, type FormEvent } from 'react'
import { postJson } from './offerApi'

type Booking = {
  id: string
  channelId: string
  status: 'BOOKED' | 'CANCELLED'
  version: number
  window: {
    startsAt: string
    endsAt: string
    rectangle: { x: number; y: number; width: number; height: number }
  }
}
type BookingPage = { bookings: Booking[]; nextCursor: string | null }
type Cancellation = { actorRole: string; reason: string; cancelledAt: string }

const button = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold disabled:opacity-50'
const formatDate = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

export function BookingsPage({ id }: { id?: string }) {
  const [bookings, setBookings] = useState<Booking[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [status, setStatus] = useState('loading')
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: '20' })
    if (cursor) query.set('after', cursor)
    const path = id ? '/api/bookings/' + id : '/api/bookings?' + query

    async function load() {
      const response = await fetch(path, { signal: controller.signal, cache: 'no-store' })
      if (controller.signal.aborted) return
      if (response.status === 401) { setStatus('guest'); return }
      if (response.status === 404) { setStatus('missing'); return }
      if (!response.ok) throw new Error()

      const page: BookingPage = id
        ? { bookings: [await response.json()], nextCursor: null }
        : await response.json()
      if (controller.signal.aborted) return
      setBookings(previous => cursor ? [...previous, ...page.bookings] : page.bookings)
      setNext(page.nextCursor)
      setStatus('ready')
    }

    load().catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [cursor, id, refresh])

  return (
    <main className="mx-auto max-w-4xl space-y-4 px-4 py-8 text-slate-900">
      {id && <a className="text-sm font-medium text-sky-800 hover:underline" href="/bookings">← All bookings</a>}
      <h1 className="text-2xl font-semibold">{id ? 'Booking details' : 'Bookings'}</h1>
      {status === 'loading' && <p role="status">Loading bookings…</p>}
      {status === 'guest' && <p><a className="text-sky-800 underline" href="/login">Log in</a> to view your bookings.</p>}
      {status === 'missing' && <p>This booking is unavailable.</p>}
      {status === 'error' && <p role="alert">Could not load bookings. <button type="button" className="underline"
        onClick={() => { setStatus('loading'); setRefresh(value => value + 1) }}>Try again</button></p>}
      {status === 'ready' && bookings.length === 0 && <p>No bookings yet.</p>}
      <ul className="space-y-3">
        {bookings.map(booking => <li key={booking.id} className="rounded-xl border border-sky-100 bg-white p-4">
          <div className="grid gap-4 sm:grid-cols-[1fr_12rem]">
            <div className="space-y-2">
              <p className="font-semibold">{booking.status === 'CANCELLED' ? 'Cancelled' : 'Booked'}</p>
              <p className="text-sm text-slate-700">
                {formatDate.format(new Date(booking.window.startsAt))} – {formatDate.format(new Date(booking.window.endsAt))}
              </p>
              {id
                ? <a className="text-sm text-sky-800 underline" href={'/streamers/' + booking.channelId}>View channel</a>
                : <a className="text-sm font-medium text-sky-800 underline" href={'/bookings/' + booking.id}>View booking</a>}
            </div>
            <svg viewBox="0 0 10000 10000" preserveAspectRatio="none" role="img" aria-label="Reserved ad area"
              className="aspect-video w-full rounded-lg bg-slate-100">
              <rect {...booking.window.rectangle} fill="#7dd3fc" stroke="#0369a1" strokeWidth="45" />
            </svg>
          </div>
          {id && <BookingCancellation booking={booking} onCancelled={saved => setBookings([saved])} />}
        </li>)}
      </ul>
      {next && status === 'ready' && <button type="button" className={button}
        onClick={() => { setStatus('loading'); setCursor(next) }}>Show more</button>}
    </main>
  )
}

function BookingCancellation({ booking, onCancelled }: { booking: Booking; onCancelled: (booking: Booking) => void }) {
  const [editing, setEditing] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [details, setDetails] = useState<Cancellation | null>(null)
  const [detailsError, setDetailsError] = useState(false)
  const path = '/api/bookings/' + booking.id

  useEffect(() => {
    if (booking.status !== 'CANCELLED') return
    const controller = new AbortController()
    fetch(path + '/cancellation', { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const cancellation: Cancellation = await response.json()
        if (!controller.signal.aborted) setDetails(cancellation)
      })
      .catch(() => { if (!controller.signal.aborted) setDetailsError(true) })
    return () => controller.abort()
  }, [booking.status, path])

  async function cancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const reason = String(new FormData(event.currentTarget).get('reason') ?? '').trim()
    if (!reason) { setError('Enter a cancellation reason.'); return }
    setPending(true)
    setError('')
    try {
      onCancelled(await postJson<Booking>(path + '/cancel', { expectedVersion: booking.version, reason }))
    } catch (failure) {
      setError(failure instanceof Error && failure.message === '409'
        ? 'This booking changed or has already started. Reload the page to check its status.'
        : 'Could not cancel the booking. Try again.')
    } finally { setPending(false) }
  }

  if (booking.status === 'CANCELLED') return (
    <div className="mt-3 space-y-1 border-t border-slate-200 pt-3 text-sm">
      {details ? <>
        <p>Cancelled by {details.actorRole.toLowerCase()} · {formatDate.format(new Date(details.cancelledAt))}</p>
        <p className="break-words text-slate-700">{details.reason}</p>
      </> : <p role={detailsError ? 'alert' : 'status'}>{detailsError ? 'Could not load the cancellation reason.' : 'Loading cancellation details…'}</p>}
    </div>
  )
  if (Date.parse(booking.window.startsAt) <= Date.now()) return null
  if (!editing) return <button type="button" className={button + ' mt-3 text-red-700'} onClick={() => setEditing(true)}>Cancel booking</button>

  return (
    <form className="mt-3 space-y-3 border-t border-slate-200 pt-3" onSubmit={cancel}>
      <label className="block text-sm font-medium">Cancellation reason
        <input name="reason" required maxLength={500} disabled={pending}
          className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" />
      </label>
      {error && <p className="text-sm text-red-700" role="alert">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <button className={button + ' border-red-700 bg-red-700 text-white'} disabled={pending}>
          {pending ? 'Cancelling…' : 'Confirm cancellation'}
        </button>
        <button type="button" className={button} disabled={pending} onClick={() => { setError(''); setEditing(false) }}>Keep booking</button>
      </div>
    </form>
  )
}
