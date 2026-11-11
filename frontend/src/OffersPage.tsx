import { useEffect, useState } from 'react'
import { postJson } from './offerApi'
import { OfferHistory } from './OfferHistory'

type Offer = {
  id: string
  version: number
  revision: number
  status: string
  recipient: boolean
  advertiser: boolean
  terms: {
    price: number
    currency: string
    startsAt: string | null
    durationSeconds: number
    advertisingCategory: string
    rectangle: { x: number; y: number; width: number; height: number }
  }
}

type Action = 'accept' | 'decline' | 'send' | 'withdraw'

const confirmations: Record<Action, string> = {
  accept: 'Offer accepted. Reservation created.',
  decline: 'Offer declined.',
  send: 'Offer sent to the streamer.',
  withdraw: 'Offer withdrawn.',
}
const statusLabel: Record<string, string> = {
  DRAFT: 'Draft', PENDING: 'Awaiting a response', COUNTERED: 'Counteroffer', ACCEPTED: 'Accepted',
  REJECTED: 'Declined', WITHDRAWN: 'Withdrawn',
}
const pageSize = 20
const actionClass = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold disabled:opacity-50'

export function OffersPage() {
  const [offers, setOffers] = useState<Offer[]>([])
  const [cursor, setCursor] = useState('')
  const [next, setNext] = useState('')
  const [status, setStatus] = useState('loading')
  const [busy, setBusy] = useState('')
  const [message, setMessage] = useState('')
  const [bookingId, setBookingId] = useState('')
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [withdrawing, setWithdrawing] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (cursor) query.set('after', cursor)
    fetch('/api/offers?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 401) { setStatus('guest'); return }
        if (!response.ok) throw new Error()
        const page: Offer[] = await response.json()
        if (controller.signal.aborted) return
        setOffers(previous => [...(cursor ? previous : []), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].id : '')
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [cursor, retry])

  async function respond(offer: Offer, action: Action) {
    if (busy) return
    setBusy(offer.id)
    setError('')
    setMessage('')
    setBookingId('')
    try {
      const path = '/api/offers/' + offer.id
      const expectedVersion = offer.version
      let result: Offer
      if (action === 'accept') {
        const booking = await postJson<{ id: string }>(path + '/accept', { expectedVersion })
        setBookingId(booking.id)
        result = { ...offer, status: 'ACCEPTED', version: expectedVersion + 1 }
      } else if (action === 'send') {
        result = await postJson<Offer>(path + '/send', { expectedVersion })
      } else {
        result = await postJson<Offer>(path + '/close', { expectedVersion, action: action === 'decline' ? 'REJECT' : 'WITHDRAW' })
      }
      setOffers(previous => previous.map(item => item.id === offer.id ? result : item))
      setWithdrawing('')
      setMessage(confirmations[action])
    } catch (failure) {
      setError(failure instanceof Error && failure.message === '409'
        ? 'This offer changed or the placement is unavailable. Refresh before trying again.'
        : 'Could not respond to this offer. Try again.')
    } finally { setBusy('') }
  }

  return (
    <main className="mx-auto max-w-4xl space-y-4 px-4 py-8 text-slate-900">
      <h1 className="text-2xl font-semibold">Offers</h1>
      {status === 'loading' && <p role="status">Loading offers…</p>}
      {status === 'guest' && <p><a className="text-sky-800 underline" href="/login">Log in</a> to view your offers.</p>}
      {status === 'error' && <p role="alert">Could not load offers. <button type="button" className="underline"
        onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
      {status === 'ready' && offers.length === 0 && <p>No offers yet.</p>}
      {message && <p role="status">{message}{bookingId && <> <a className="text-sky-800 underline"
        href={'/bookings/' + bookingId}>View booking</a></>}</p>}
      {error && <p role="alert" className="text-red-700">{error}</p>}
      <ul className="space-y-3">
        {offers.map(offer => {
          const { terms } = offer
          const preview = '/api/offers/' + offer.id + '/revisions/' + offer.revision + '/creative'
          const open = offer.status === 'PENDING' || offer.status === 'COUNTERED'
          const canRespond = offer.recipient && open
          const canSend = offer.advertiser && offer.status === 'DRAFT'
          const canWithdraw = offer.advertiser && (open || canSend)
          return (
            <li key={offer.id} className="grid gap-4 rounded-xl border border-sky-100 bg-white p-4 sm:grid-cols-[1fr_12rem]">
              <div className="space-y-2">
                <h2 className="font-semibold">{terms.advertisingCategory} · {terms.price.toFixed(2)} {terms.currency}</h2>
                <p className="text-sm text-slate-700">{terms.startsAt ? new Date(terms.startsAt).toLocaleString('en') : 'Flexible start'} · {terms.durationSeconds} s</p>
                <p className="text-sm text-slate-600">{offer.advertiser ? 'Your offer' : 'For your channel'} · {statusLabel[offer.status] ?? offer.status}</p>
                {canRespond && <div className="flex gap-2">
                  <button type="button" disabled={!!busy || !terms.startsAt} onClick={() => void respond(offer, 'accept')}
                    className={actionClass + ' bg-sky-700 text-white'}>Accept and reserve</button>
                  <button type="button" disabled={!!busy} onClick={() => void respond(offer, 'decline')}
                    className={actionClass}>Decline</button>
                </div>}
                {(canSend || canWithdraw) && <div className="flex flex-wrap gap-2">
                  {canSend && <button type="button" disabled={!!busy} className={actionClass + ' bg-sky-700 text-white'}
                    onClick={() => void respond(offer, 'send')}>Send draft</button>}
                  {canWithdraw && (withdrawing === offer.id
                    ? <>
                      <button type="button" disabled={!!busy} className={actionClass + ' text-red-700'}
                        onClick={() => void respond(offer, 'withdraw')}>Confirm withdrawal</button>
                      <button type="button" disabled={!!busy} className={actionClass}
                        onClick={() => setWithdrawing('')}>Keep offer</button>
                    </>
                    : <button type="button" disabled={!!busy} className={actionClass}
                      onClick={() => setWithdrawing(offer.id)}>Withdraw offer</button>)}
                </div>}
                <OfferHistory key={offer.id + ':' + offer.revision} offerId={offer.id} />
              </div>
              <div className="space-y-3">
                <a href={preview} target="_blank" rel="noreferrer" className="block rounded-lg bg-slate-100 p-2 text-sm text-sky-800">
                  <img key={preview} src={preview} alt="Proposed creative" loading="lazy" className="mb-2 max-h-48 w-full object-contain" />
                  Open creative
                </a>
                <svg viewBox="0 0 10000 10000" preserveAspectRatio="none" role="img" aria-label="Proposed ad area"
                  className="aspect-video w-full rounded-lg bg-slate-100">
                  <rect {...terms.rectangle} fill="#7dd3fc" stroke="#0369a1" strokeWidth="45" />
                </svg>
              </div>
            </li>
          )
        })}
      </ul>
      {next && status === 'ready' && <button type="button" className={actionClass}
        onClick={() => { setStatus('loading'); setCursor(next) }}>Show more</button>}
    </main>
  )
}
