import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { api } from '../lib/api'
import { ReservationCalendar } from './ReservationCalendar'
import { Button } from './ui/button'

export type Placement = {
  id: string
  title: string
  position: string
  widthPx: number
  heightPx: number
  durationSeconds: number
  minimumPrice: number | null
  currency: string
  negotiable: boolean
  unrestrictedCategories: boolean
  allowedCategories: string[]
}

const pageSize = 20

export function ChannelPlacements({ channelId, title, onBack }: { channelId: string; title: string; onBack: () => void }) {
  const [placements, setPlacements] = useState<Placement[]>([])
  const [after, setAfter] = useState<string | null>(null)
  const [more, setMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ channelId, limit: String(pageSize) })
    if (after) query.set('after', after)
    setLoading(true)
    setError(false)
    api<Placement[]>(`/api/catalog/placements?${query}`, { signal: controller.signal })
      .then(rows => {
        if (controller.signal.aborted) return
        setPlacements(previous => after ? [...previous, ...rows] : rows)
        setMore(rows.length === pageSize)
      })
      .catch(() => { if (!controller.signal.aborted) setError(true) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [channelId, after, attempt])

  return (
    <section className="mt-5 space-y-4" aria-label="Рекламные места канала" aria-busy={loading}>
      <Button variant="ghost" onClick={onBack}><ArrowLeft />К списку стримеров</Button>
      <h3 className="break-words text-xl font-semibold">{title}</h3>
      <ReservationCalendar channelId={channelId} />
      <div className="grid gap-4 md:grid-cols-2">
        {placements.map(place => (
          <article key={place.id} className="min-w-0 rounded-2xl border border-sky-100 bg-sky-50/60 p-5">
            <h4 className="break-words font-semibold">{place.title}</h4>
            <p className="mt-1 break-words text-sm text-slate-600">{place.position}</p>
            <p className="mt-3 text-sm">{place.widthPx} × {place.heightPx} px · {place.durationSeconds} сек.</p>
            <p className="mt-2 font-medium text-sky-800">{place.minimumPrice === null ? 'Цена по договорённости' : `${place.minimumPrice} ${place.currency}`}</p>
            {place.negotiable && place.minimumPrice !== null && <p className="mt-1 text-sm text-slate-600">Условия можно обсудить</p>}
            {!place.unrestrictedCategories && <p className="mt-2 break-words text-sm text-slate-600">{place.allowedCategories.join(', ')}</p>}
          </article>
        ))}
      </div>
      {loading && <p role="status">Загружаем рекламные места…</p>}
      {error && <div role="alert" className="space-y-2">
        <p>Не удалось загрузить рекламные места.</p>
        <Button variant="outline" onClick={() => setAttempt(value => value + 1)}>Повторить</Button>
      </div>}
      {!loading && !error && placements.length === 0 && <p>У канала сейчас нет доступных рекламных мест.</p>}
      {!loading && !error && more && <Button variant="outline" onClick={() => setAfter(placements.at(-1)!.id)}>Показать ещё</Button>}
    </section>
  )
}
