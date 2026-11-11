import { useEffect, useState, type FormEvent } from 'react'
import { postJson } from './offerApi'
import { StreamAreaPicker, type Rectangle } from './StreamAreaPicker'

type Channel = { id: string; displayName: string; authorizationRequired: boolean }
type Placement = {
  id: string; channelId: string; title: string; position: string; widthPx: number; heightPx: number
  durationSeconds: number; minimumPrice: number | null; currency: string; negotiable: boolean
  capacity: number; zone: Rectangle | null; active: boolean; version: number
}
type Window = { id: string; startsAt: string; endsAt: string }

const field = 'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2'
const button = 'rounded-lg bg-sky-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50'
const secondary = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold disabled:opacity-50'
const dateTime = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })
const pageSize = 20

export function PlacementSettings({ channels }: { channels: Channel[] }) {
  const [placements, setPlacements] = useState<Placement[]>([])
  const [after, setAfter] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [retry, setRetry] = useState(0)
  const [status, setStatus] = useState('loading')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (after) query.set('after', after)
    fetch('/api/placements?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const page: Placement[] = await response.json()
        if (controller.signal.aborted) return
        setPlacements(previous => [...previous, ...page.slice(0, pageSize).filter(item => !previous.some(existing => existing.id === item.id))])
        setNext(page.length > pageSize ? page[pageSize - 1].id : null)
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [after, retry])

  return (
    <section className="space-y-3 p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold">Ad placements</h2>
        {!!channels.length && <button type="button" className={button} onClick={() => setCreating(value => !value)}>
          {creating ? 'Close form' : 'New placement'}
        </button>}
      </div>
      {!channels.length && <p className="text-sm text-slate-600">Connect a channel to create an ad placement.</p>}
      {creating && <PlacementForm channels={channels} onSaved={placement => {
        setPlacements(previous => [placement, ...previous.filter(item => item.id !== placement.id)])
        setCreating(false)
      }} />}
      {status === 'loading' && <p role="status" className="text-sm">Loading placementsâ€¦</p>}
      {status === 'error' && <p role="alert" className="text-sm">Could not load placements. <button type="button" className="underline"
        onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Retry</button></p>}
      {status === 'ready' && channels.length > 0 && !placements.length && <p className="text-sm">No ad placements yet.</p>}
      {placements.map(placement => <PlacementCard key={placement.id} placement={placement} channels={channels}
        onSaved={saved => setPlacements(previous => previous.map(item => item.id === saved.id ? saved : item))} />)}
      {next && status === 'ready' && <button type="button" className={secondary}
        onClick={() => { setStatus('loading'); setAfter(next) }}>Show more</button>}
    </section>
  )
}

function PlacementForm({ channels, placement, onSaved }: {
  channels: Channel[]; placement?: Placement; onSaved: (placement: Placement) => void
}) {
  const [negotiable, setNegotiable] = useState(placement?.negotiable ?? false)
  const [limitArea, setLimitArea] = useState(!!placement?.zone)
  const [zone, setZone] = useState<Rectangle | null>(placement?.zone ?? null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (limitArea && !zone) {
      setError('Draw a zone on the stream layout.')
      return
    }
    const data = new FormData(event.currentTarget)
    const price = data.get('minimumPrice')
    const request = {
      channelId: placement?.channelId ?? data.get('channelId'),
      title: String(data.get('title')).trim(),
      position: String(data.get('position')).trim(),
      widthPx: Number(data.get('widthPx')),
      heightPx: Number(data.get('heightPx')),
      durationSeconds: Number(data.get('durationSeconds')),
      minimumPrice: price === '' ? null : price,
      currency: String(data.get('currency')).trim().toUpperCase(),
      negotiable,
      capacity: Number(data.get('capacity')),
      zone: limitArea ? zone : null,
    }
    setBusy(true)
    setError('')
    try {
      const saved = placement
        ? await postJson<Placement>('/api/placements/' + placement.id, { placement: request, expectedVersion: placement.version }, 'PUT')
        : await postJson<Placement>('/api/placements', request)
      onSaved(saved)
    } catch (failure) {
      setError(failure instanceof Error && failure.message === '409'
        ? 'The placement changed. Refresh the page before editing.'
        : 'Could not save the placement. Check the details and try again.')
    } finally { setBusy(false) }
  }

  return (
    <form className="rounded-lg border border-sky-100 p-3" onSubmit={save}>
      <fieldset disabled={busy} className="grid gap-3 text-sm sm:grid-cols-2">
        {!placement && <label>Channel<select className={field} name="channelId" required>
          {channels.map(channel => <option key={channel.id} value={channel.id}>{channel.displayName}</option>)}
        </select></label>}
        <label>Title<input name="title" className={field} required maxLength={120} defaultValue={placement?.title} /></label>
        <label>Placement notes<input name="position" className={field} required maxLength={120} defaultValue={placement?.position ?? 'Custom area'} /></label>
        <label>Width (px)<input name="widthPx" className={field} type="number" required min={1} max={7680} defaultValue={placement?.widthPx} /></label>
        <label>Height (px)<input name="heightPx" className={field} type="number" required min={1} max={4320} defaultValue={placement?.heightPx} /></label>
        <label>Duration (seconds)<input name="durationSeconds" className={field} type="number" required min={1} max={604800} defaultValue={placement?.durationSeconds} /></label>
        <label>Price<input name="minimumPrice" className={field} type="number" required={!negotiable} min={0} max={9999999999.99} step="0.01" defaultValue={placement?.minimumPrice ?? ''} /></label>
        <label>Currency<input name="currency" className={field} required pattern="[A-Za-z]{3}" maxLength={3} defaultValue={placement?.currency} placeholder="USD" /></label>
        <label>Capacity<input name="capacity" className={field} type="number" required min={1} max={100} defaultValue={placement?.capacity ?? 1} /></label>
        <label className="flex items-center gap-2"><input type="checkbox" checked={negotiable}
          onChange={event => setNegotiable(event.target.checked)} /> Negotiable terms</label>
        <div className="space-y-2 sm:col-span-2">
          <label className="flex items-center gap-2"><input type="checkbox" checked={limitArea}
            onChange={event => setLimitArea(event.target.checked)} /> Limit ads to a drawn zone</label>
          {limitArea && <>
            <p className="text-slate-600">Drag on the layout to draw the area available for ads.</p>
            <StreamAreaPicker area={zone} onChange={setZone} disabled={busy} />
          </>}
        </div>
        <div><button className={button}>{busy ? 'Savingâ€¦' : 'Save placement'}</button></div>
      </fieldset>
      {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
    </form>
  )
}

function PlacementCard({ placement, channels, onSaved }: {
  placement: Placement; channels: Channel[]; onSaved: (placement: Placement) => void
}) {
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [windows, setWindows] = useState<Window[] | null>(null)
  const [cursor, setCursor] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [windowError, setWindowError] = useState(false)
  const path = '/api/placements/' + placement.id
  const channel = channels.find(item => item.id === placement.channelId)
  const price = placement.minimumPrice === null ? 'Negotiable price'
    : (placement.negotiable ? 'From ' : '') + placement.minimumPrice.toFixed(2) + ' ' + placement.currency

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (cursor) query.set('after', cursor)
    fetch(path + '/availability?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const page: Window[] = await response.json()
        if (controller.signal.aborted) return
        setWindows(previous => [...(cursor ? previous ?? [] : []), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].startsAt : null)
      })
      .catch(() => { if (!controller.signal.aborted) setWindowError(true) })
    return () => controller.abort()
  }, [cursor, open, path, revision])

  function refreshWindows() {
    setWindows(null)
    setNext(null)
    setCursor(null)
    setWindowError(false)
    setRevision(value => value + 1)
  }

  async function changeAvailability(event: FormEvent<HTMLFormElement> | null, windowId?: string) {
    event?.preventDefault()
    if (busy) return
    const form = event?.currentTarget
    setError('')
    setBusy(true)
    try {
      if (form) {
        const data = new FormData(form)
        const startsAt = new Date(String(data.get('start')))
        const endsAt = new Date(String(data.get('end')))
        if (!(startsAt.getTime() > Date.now()) || !(endsAt.getTime() - startsAt.getTime() >= placement.durationSeconds * 1000)) {
          setError('Choose a future window that covers the placement duration.')
          return
        }
        await postJson(path + '/availability', { startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(), expectedVersion: placement.version })
        form.reset()
      } else {
        await postJson(path + '/availability/' + windowId + '?expectedVersion=' + placement.version, undefined, 'DELETE')
      }
      onSaved({ ...placement, version: placement.version + 1 })
      refreshWindows()
    } catch (failure) {
      setError(failure instanceof Error && failure.message === '409'
        ? 'The window overlaps an existing window, or the placement changed. Check the schedule before trying again.'
        : 'Could not change availability. Try again.')
    } finally { setBusy(false) }
  }

  async function toggle() {
    if (busy) return
    setError('')
    setBusy(true)
    try {
      onSaved(await postJson<Placement>(path + '/activation', { active: !placement.active, expectedVersion: placement.version }, 'PUT'))
    } catch {
      setError(placement.active ? 'Could not hide the placement. Refresh and try again.'
        : 'Could not publish. Check that the channel is connected and listed, and that a future window covers the placement duration.')
    } finally { setBusy(false) }
  }

  return (
    <article className="space-y-3 rounded-lg border border-slate-200 p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="break-words font-medium">{placement.title}</h3>
          <p className="text-sm text-slate-600">{channel?.displayName} Â· {placement.active ? 'Published' : 'Unpublished'} Â· {placement.durationSeconds} s · {price} · {placement.zone ? 'Drawn zone' : 'Any free area'}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={secondary} onClick={() => { if (open) refreshWindows(); setOpen(!open) }} aria-expanded={open}>Availability</button>
          {!placement.active && <button type="button" className={secondary} disabled={busy} onClick={() => setEditing(value => !value)}>
            {editing ? 'Close editor' : 'Edit'}
          </button>}
          <button type="button" className={placement.active ? secondary : button} disabled={busy || editing || (!placement.active && channel?.authorizationRequired)}
            onClick={() => void toggle()}>{placement.active ? 'Unpublish' : 'Publish'}</button>
        </div>
      </div>
      {editing && <PlacementForm placement={placement} channels={channels} onSaved={saved => { onSaved(saved); setEditing(false) }} />}
      {open && <div className="space-y-3 border-t border-slate-200 pt-3 text-sm">
        {windowError
          ? <p role="alert">Could not load availability. <button type="button" className="underline" onClick={refreshWindows}>Retry</button></p>
          : windows === null ? <p role="status">Loading availabilityâ€¦</p>
          : windows.length === 0 ? <p>No availability windows yet.</p>
          : <ul className="space-y-2">{windows.map(window => <li key={window.id} className="flex flex-wrap items-center justify-between gap-2">
            <span>{dateTime.format(new Date(window.startsAt))} â€“ {dateTime.format(new Date(window.endsAt))}</span>
            {!placement.active && <button type="button" className="text-red-700 underline" disabled={busy || editing}
              onClick={() => void changeAvailability(null, window.id)}>Remove</button>}
          </li>)}</ul>}
        {next && !windowError && <button type="button" className={secondary} onClick={() => { setCursor(next); setNext(null) }}>Show more windows</button>}
        {placement.active ? <p className="text-slate-600">Unpublish to edit availability.</p>
          : <form className="flex flex-wrap items-end gap-3" onSubmit={event => void changeAvailability(event)}>
            <label>Start (local time)<input type="datetime-local" className={field} name="start" required disabled={busy || editing} /></label>
            <label>End (local time)<input type="datetime-local" className={field} name="end" required disabled={busy || editing} /></label>
            <button className={button} disabled={busy || editing}>Add window</button>
          </form>}
      </div>}
      {error && <p className="text-sm text-red-700" role="alert">{error}</p>}
    </article>
  )
}
