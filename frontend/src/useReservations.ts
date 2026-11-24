import { useEffect, useState } from 'react'
import type { Rectangle } from './StreamAreaPicker'

export type Reservation = { startsAt: string; endsAt: string; rectangle: Rectangle }
type Page = { reservations: Reservation[]; nextCursor: string | null }

export function overlaps(a: Rectangle, b: Rectangle) {
  return a.x < b.x + b.width && b.x < a.x + a.width
    && a.y < b.y + b.height && b.y < a.y + a.height
}

export function useReservations(channelId: string | undefined, start: string, seconds: number) {
  const [calendar, setCalendar] = useState<{ key: string; reservations?: Reservation[]; error?: string } | null>(null)
  const [revision, setRevision] = useState(0)
  const from = Date.parse(start)
  const valid = Number.isFinite(from) && Number.isInteger(seconds) && seconds > 0 && seconds <= 604800
  const range = valid ? new URLSearchParams({
    from: start,
    to: new Date(from + seconds * 1000).toISOString()
      .replace(/\.\d+Z$/, start.match(/\.\d+Z$/)?.[0] ?? 'Z'),
  }).toString() : ''
  const key = channelId && range ? channelId + '?' + range + '&revision=' + revision : ''

  useEffect(() => {
    if (!key || !channelId) return
    const controller = new AbortController()
    async function load() {
      const reservations: Reservation[] = []
      let cursor: string | null = null
      do {
        const query = new URLSearchParams(range)
        query.set('limit', '100')
        if (cursor) query.set('cursor', cursor)
        const response = await fetch('/api/catalog/channels/' + channelId + '/reservations?' + query,
          { signal: controller.signal, cache: 'no-store' })
        if (!response.ok) throw new Error()
        const page: Page = await response.json()
        reservations.push(...page.reservations)
        cursor = page.nextCursor
      } while (cursor)
      if (!controller.signal.aborted) setCalendar({ key, reservations })
    }
    load().catch(() => {
      if (!controller.signal.aborted) setCalendar({ key, error: 'Could not check reservations.' })
    })
    return () => controller.abort()
  }, [key, channelId, range])

  const current = key && calendar?.key === key ? calendar : null
  return {
    reservations: current?.reservations ?? null,
    error: current?.error ?? '',
    loading: !!key && !current,
    refresh: () => setRevision(value => value + 1),
  }
}
