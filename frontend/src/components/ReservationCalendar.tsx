import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { Button } from './ui/button'
import { Input } from './ui/input'

type Window = {
  startsAt: string
  endsAt: string
  rectangle: { x: number; y: number; width: number; height: number }
}
type Page = { reservations: Window[]; nextCursor: string | null }
type Request = { from: string; to: string; cursor?: string }

export function ReservationCalendar({ channelId }: { channelId: string }) {
  const [request, setRequest] = useState<Request | null>(null)
  const [page, setPage] = useState<Page | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!request) return
    const controller = new AbortController()
    const query = new URLSearchParams({ from: request.from, to: request.to })
    if (request.cursor) query.set('cursor', request.cursor)
    api<Page>(`/api/catalog/channels/${channelId}/reservations?${query}`, { signal: controller.signal })
      .then(result => {
        if (controller.signal.aborted) return
        setPage(previous => ({
          ...result,
          reservations: request.cursor ? [...(previous?.reservations ?? []), ...result.reservations] : result.reservations,
        }))
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('Не удалось загрузить резервы. Повторите поиск.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [channelId, request])

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    const from = new Date(String(fields.get('from')))
    const to = new Date(String(fields.get('to')))
    const duration = to.getTime() - from.getTime()
    if (!Number.isFinite(duration) || duration <= 0 || duration > 31 * 86400000) {
      setError('Выберите период до 31 дня. Конец должен быть позже начала.')
      return
    }
    setError('')
    setPage(null)
    setLoading(true)
    setRequest({ from: from.toISOString(), to: to.toISOString() })
  }

  return (
    <section className="space-y-4 rounded-2xl border border-sky-100 p-5" aria-busy={loading}>
      <h4 className="font-semibold">Занятые области экрана</h4>
      <p className="text-sm text-slate-600">Выберите период по местному времени.</p>
      <form onSubmit={search} className="flex flex-wrap items-end gap-3">
        <label className="min-w-0 space-y-1 text-sm">Начало<Input type="datetime-local" name="from" required disabled={loading} /></label>
        <label className="min-w-0 space-y-1 text-sm">Конец<Input type="datetime-local" name="to" required disabled={loading} /></label>
        <Button type="submit" disabled={loading}>Показать резервы</Button>
      </form>
      {loading && <p role="status">Загружаем резервы…</p>}
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      {page && !page.reservations.length && <p>В этом периоде нет резервов.</p>}
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {page?.reservations.map(({ startsAt, endsAt, rectangle }) => (
          <li key={[startsAt, endsAt, rectangle.x, rectangle.y, rectangle.width, rectangle.height].join(':')} className="space-y-2">
            <div className="relative aspect-video rounded-lg bg-sky-50" role="img" aria-label={`Занятая область: слева ${rectangle.x / 100}%, сверху ${rectangle.y / 100}%, ширина ${rectangle.width / 100}%, высота ${rectangle.height / 100}%`}>
              <div className="absolute border border-sky-700 bg-sky-400/60" style={{ left: `${rectangle.x / 100}%`, top: `${rectangle.y / 100}%`, width: `${rectangle.width / 100}%`, height: `${rectangle.height / 100}%` }} />
            </div>
            <p className="text-sm text-slate-700">{new Date(startsAt).toLocaleString('ru-RU')} — {new Date(endsAt).toLocaleString('ru-RU')}</p>
          </li>
        ))}
      </ul>
      {request && page?.nextCursor && !loading && !error && <Button variant="outline" onClick={() => {
        setLoading(true)
        setRequest({ ...request, cursor: page.nextCursor! })
      }}>Показать ещё</Button>}
    </section>
  )
}
