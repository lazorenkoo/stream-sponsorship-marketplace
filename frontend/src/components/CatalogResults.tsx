import { useEffect, useState } from 'react'
import { Button } from './ui/button'
import { api } from '../lib/api'

type Kind = 'streamers' | 'campaigns'
type Streamers = { streamers: { channelId: string; displayName: string; platform: string }[]; nextCursor: string | null }
type Campaigns = { campaigns: { id: string; title: string; description: string; platforms: string[] }[]; nextBefore: string | null; nextBeforeId: string | null }
type Card = { id: string; title: string; description?: string; platforms: string[] }
type Page = { cards: Card[]; next: string | null }
const platformNames: Record<string, string> = { TWITCH: 'Twitch', YOUTUBE: 'YouTube', KICK: 'Kick' }

async function loadCatalog(kind: Kind, platform: string, cursor: string | null, signal: AbortSignal): Promise<Page> {
  const query = new URLSearchParams(cursor ?? '')
  query.set('limit', '12')
  if (platform) query.set('platform', platform)
  if (kind === 'streamers') {
    const result = await api<Streamers>(`/api/catalog/streamers?${query}`, { signal })
    return { cards: result.streamers.map(row => ({ id: row.channelId, title: row.displayName, platforms: [row.platform] })),
      next: result.nextCursor ? new URLSearchParams({ after: result.nextCursor }).toString() : null }
  }
  const result = await api<Campaigns>(`/api/catalog/campaigns?${query}`, { signal })
  return { cards: result.campaigns,
    next: result.nextBefore && result.nextBeforeId ? new URLSearchParams({ before: result.nextBefore, beforeId: result.nextBeforeId }).toString() : null }
}

export function CatalogResults({ kind }: { kind: Kind }) {
  const [platform, setPlatform] = useState('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [page, setPage] = useState<Page>({ cards: [], next: null })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(false)
    loadCatalog(kind, platform, cursor, controller.signal).then(result => {
      if (!controller.signal.aborted) setPage(previous => ({ cards: cursor ? [...previous.cards, ...result.cards] : result.cards, next: result.next }))
    }).catch(() => { if (!controller.signal.aborted) setError(true) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [kind, platform, cursor, attempt])
  return (
    <div className="mt-6 space-y-5" aria-busy={loading}>
      <label className="flex flex-wrap items-center gap-3 text-sm font-medium">Платформа
        <select value={platform} className="rounded-xl border border-sky-200 bg-white px-3 py-2 text-slate-800"
          onChange={event => { setPlatform(event.target.value); setCursor(null); setPage({ cards: [], next: null }) }}>
          <option value="">Все платформы</option>
          {Object.entries(platformNames).map(([value, name]) => <option key={value} value={value}>{name}</option>)}
        </select>
      </label>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {page.cards.map(card => <article key={card.id} className="min-w-0 rounded-2xl border border-sky-100 bg-sky-50/50 p-5">
          <h3 className="break-words text-lg font-semibold">{card.title}</h3>
          {card.description && <p className="mt-2 break-words text-sm text-slate-600">{card.description}</p>}
          <div className="mt-4 flex flex-wrap gap-2">{card.platforms.map(value => <span key={value} className="rounded-full bg-white px-3 py-1 text-xs font-medium text-sky-700">{platformNames[value] ?? value}</span>)}</div>
        </article>)}
      </div>
      {loading && <p role="status" className="text-sm text-slate-500">Загружаем каталог…</p>}
      {error && <div role="alert" className="flex flex-wrap items-center gap-3 text-sm text-red-700">Не удалось загрузить каталог.<Button variant="outline" onClick={() => setAttempt(value => value + 1)}>Повторить</Button></div>}
      {!loading && !error && !page.cards.length && <p className="text-sm text-slate-500">{kind === 'streamers' ? 'Нет доступных каналов по этому фильтру.' : 'Нет опубликованных кампаний по этому фильтру.'}</p>}
      {!loading && !error && page.next && <Button variant="outline" onClick={() => setCursor(page.next)}>Показать ещё</Button>}
    </div>
  )
}
