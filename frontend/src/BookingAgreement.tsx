import { useEffect, useState } from 'react'
import type { Revision } from './OfferHistory'

export function BookingAgreement({ offerId, revision }: { offerId: string; revision: number }) {
  const [agreement, setAgreement] = useState<Revision | null>(null)
  const [error, setError] = useState(false)
  const [retry, setRetry] = useState(0)
  const [imageError, setImageError] = useState(false)
  const [imageRetry, setImageRetry] = useState(0)
  const preview = '/api/offers/' + offerId + '/revisions/' + revision + '/creative'

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ beforeRevision: String(revision + 1), limit: '1' })
    fetch('/api/offers/' + offerId + '/revisions?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const revisions: Revision[] = await response.json()
        if (revisions.length !== 1 || revisions[0].revision !== revision) throw new Error()
        if (!controller.signal.aborted) setAgreement(revisions[0])
      })
      .catch(() => { if (!controller.signal.aborted) setError(true) })
    return () => controller.abort()
  }, [offerId, revision, retry])

  return <section className="mt-4 space-y-3 border-t border-slate-200 pt-4">
    <h2 className="font-semibold">Agreed terms</h2>
    {error && <p role="alert" className="text-sm text-red-700">Could not load the agreed terms.{' '}
      <button type="button" className="underline" onClick={() => { setError(false); setRetry(value => value + 1) }}>Try again</button>
    </p>}
    {!agreement && !error && <p role="status" className="text-sm">Loading agreed terms…</p>}
    {agreement && <div className="grid gap-4 sm:grid-cols-[1fr_12rem]">
      <dl className="grid content-start grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-slate-600">Price</dt>
        <dd className="font-medium">{agreement.terms.price.toFixed(2)} {agreement.terms.currency}</dd>
        <dt className="text-slate-600">Duration</dt><dd>{agreement.terms.durationSeconds} s</dd>
        <dt className="text-slate-600">Category</dt><dd className="break-words">{agreement.terms.advertisingCategory}</dd>
        <dt className="text-slate-600">Offer version</dt><dd>Revision {agreement.revision + 1}</dd>
      </dl>
      <div className="space-y-2">
        {imageError ? <p role="alert" className="text-sm">Could not load the creative.{' '}
          <button type="button" className="text-sky-800 underline" onClick={() => {
            setImageError(false)
            setImageRetry(value => value + 1)
          }}>Try again</button>
        </p> : <a href={preview} target="_blank" rel="noreferrer" className="block rounded-lg bg-slate-100 p-2">
          <img key={imageRetry} src={preview} alt="Agreed ad creative" loading="lazy"
            className="max-h-48 w-full object-contain" onError={() => setImageError(true)} />
        </a>}
        <a href={preview} target="_blank" rel="noreferrer" className="inline-block text-sm font-medium text-sky-800 underline">
          Open agreed creative
        </a>
      </div>
    </div>}
  </section>
}
