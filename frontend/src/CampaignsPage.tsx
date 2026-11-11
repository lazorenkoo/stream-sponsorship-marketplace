import { CampaignInvite } from './CampaignInvite'
import { useEffect, useState, type FormEvent } from 'react'
import { CampaignInbox } from './CampaignInbox'
import { postJson } from './offerApi'

type Campaign = {
  id: string; title: string; description: string; createdAt: string; version: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
}
type Targeting = { platforms: string[]; version: number }

const pageSize = 20
const platforms = { TWITCH: 'Twitch', KICK: 'Kick', YOUTUBE: 'YouTube' }
const statuses = { DRAFT: 'Draft', PUBLISHED: 'Published', ARCHIVED: 'Archived' }
const primary = 'rounded-lg bg-sky-700 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-800 disabled:opacity-50'
const secondary = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold hover:bg-sky-50 disabled:opacity-50'
const field = 'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2'
const endpoint = '/api/campaigns'

function errorMessage(failure: unknown, fallback: string) {
  return failure instanceof Error && failure.message === '409'
    ? 'The campaign changed or this action is unavailable. Reload the page and check its current status.'
    : fallback
}

export function CampaignsPage() {
  const [campaigns, setCampaigns] = useState<Campaign[]>([])
  const [after, setAfter] = useState<Campaign | null>(null)
  const [next, setNext] = useState<Campaign | null>(null)
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (after) {
      query.set('before', after.createdAt)
      query.set('beforeId', after.id)
    }
    fetch(endpoint + '?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 401) { setStatus('guest'); return }
        if (response.status === 403) { setStatus('forbidden'); return }
        if (!response.ok) throw new Error()
        const page: Campaign[] = await response.json()
        if (controller.signal.aborted) return
        setCampaigns(previous => [...previous, ...page.slice(0, pageSize).filter(
          campaign => !previous.some(existing => existing.id === campaign.id))])
        setNext(page.length > pageSize ? page[pageSize - 1] : null)
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [after, retry])

  return <main className="mx-auto max-w-4xl space-y-4 px-4 py-8 text-slate-900">
    <a href="/campaigns" className="text-sm font-medium text-sky-800 hover:underline">Back to Ad Campaigns</a>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold">My campaigns</h1>
      {status === 'ready' && !creating && <button type="button" className={primary} onClick={() => setCreating(true)}>New campaign</button>}
    </div>
    {status === 'guest' && <p><a href="/login" className="text-sky-800 underline">Log in</a> to manage campaigns.</p>}
    {status === 'forbidden' && <p>An advertiser account is required to manage campaigns.</p>}
    {status === 'loading' && <p role="status">Loading campaigns…</p>}
    {status === 'error' && <p role="alert">Could not load campaigns. <button type="button" className="underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {creating && <div className="rounded-xl border border-sky-100 bg-white p-4">
      <CampaignForm onCancel={() => setCreating(false)} onSaved={saved => {
        setCampaigns(previous => [saved, ...previous.filter(campaign => campaign.id !== saved.id)])
        setCreating(false)
      }} />
    </div>}
    {status === 'ready' && !campaigns.length && !creating && <p>No campaigns yet.</p>}
    <ul className="space-y-3">
      {campaigns.map(campaign => <CampaignCard key={campaign.id} campaign={campaign}
        onSaved={saved => setCampaigns(previous => previous.map(item => item.id === saved.id ? saved : item))} />)}
    </ul>
    {next && status === 'ready' && <button type="button" className={secondary}
      onClick={() => { setStatus('loading'); setAfter(next) }}>Show more</button>}
  </main>
}

function CampaignForm({ campaign, onSaved, onCancel }: {
  campaign?: Campaign; onSaved: (campaign: Campaign) => void; onCancel: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title')).trim()
    if (!title) { setError('Enter a campaign title.'); return }
    setBusy(true)
    setError('')
    try {
      const body = { title, description: String(data.get('description')).trim() }
      onSaved(campaign
        ? await postJson<Campaign>(endpoint + '/' + campaign.id, { ...body, expectedVersion: campaign.version }, 'PUT')
        : await postJson<Campaign>(endpoint, body))
    } catch (failure) {
      setError(errorMessage(failure, 'Could not save the campaign. Check the details and try again.'))
    } finally { setBusy(false) }
  }

  return <form className="space-y-3 text-sm" onSubmit={save}>
    <label className="block">Title<input name="title" className={field} required maxLength={120} disabled={busy}
      defaultValue={campaign?.title} /></label>
    <label className="block">Description<textarea name="description" className={field} rows={3} maxLength={5000}
      disabled={busy} defaultValue={campaign?.description} /></label>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    <div className="flex flex-wrap gap-2">
      <button className={primary} disabled={busy}>{busy ? 'Saving…' : 'Save draft'}</button>
      <button type="button" className={secondary} disabled={busy} onClick={onCancel}>Cancel</button>
    </div>
  </form>
}

function CampaignCard({ campaign, onSaved }: { campaign: Campaign; onSaved: (campaign: Campaign) => void }) {
  const [panel, setPanel] = useState<'edit' | 'platforms' | 'archive' | 'applications' | 'invite' | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const path = endpoint + '/' + campaign.id

  async function archive() {
    setError('')
    setBusy(true)
    try {
      onSaved(await postJson<Campaign>(path + '/archive', { expectedVersion: campaign.version }))
      setPanel(null)
    } catch (failure) {
      setError(errorMessage(failure, 'Could not archive the campaign. Try again.'))
    } finally { setBusy(false) }
  }

  return <li className="space-y-3 rounded-xl border border-sky-100 bg-white p-4">
    <div className="flex flex-wrap items-start justify-between gap-2">
      <h2 className="min-w-0 break-words font-semibold">{campaign.title}</h2>
      <span className="rounded-md bg-sky-50 px-2 py-1 text-xs font-medium text-sky-900">{statuses[campaign.status]}</span>
    </div>
    {campaign.description && <p className="whitespace-pre-wrap break-words text-sm text-slate-700">{campaign.description}</p>}
    {!panel && campaign.status !== 'ARCHIVED' && <div className="flex flex-wrap gap-2">
      {campaign.status === 'DRAFT' && <>
        <button type="button" className={secondary} onClick={() => setPanel('edit')}>Edit draft</button>
        <button type="button" className={primary} onClick={() => setPanel('platforms')}>Set platforms and publish</button>
      </>}
      {campaign.status === 'PUBLISHED' && <a className={secondary} href={'/campaigns/' + campaign.id}>View campaign</a>}
      <button type="button" className={secondary} onClick={() => setPanel('archive')}>Archive</button>
    </div>}
    {!panel && campaign.status !== 'DRAFT' && <button type="button" className={secondary}
      onClick={() => setPanel('applications')}>View applications</button>}
    {!panel && campaign.status === 'PUBLISHED' && <button type="button" className={secondary}
      onClick={() => setPanel('invite')}>Invite streamers</button>}
    {panel === 'invite' && <CampaignInvite id={campaign.id} version={campaign.version} close={() => setPanel(null)} />}
    {panel === 'applications' && <CampaignInbox campaignId={campaign.id} published={campaign.status === 'PUBLISHED'}
      onClose={() => setPanel(null)} />}
    {panel === 'edit' && <CampaignForm campaign={campaign} onCancel={() => setPanel(null)}
      onSaved={saved => { onSaved(saved); setPanel(null) }} />}
    {panel === 'platforms' && <CampaignPlatforms campaign={campaign} onSaved={onSaved}
      onClose={() => setPanel(null)} />}
    {panel === 'archive' && <div className="space-y-2 border-t border-slate-200 pt-3 text-sm">
      <p>Archive this campaign? It will be hidden from the marketplace.</p>
      {error && <p role="alert" className="text-red-700">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <button type="button" className={secondary + ' text-red-700'} disabled={busy}
          onClick={() => void archive()}>{busy ? 'Archiving…' : 'Confirm archive'}</button>
        <button type="button" className={secondary} disabled={busy} onClick={() => { setError(''); setPanel(null) }}>Keep campaign</button>
      </div>
    </div>}
  </li>
}

function CampaignPlatforms({ campaign, onSaved, onClose }: {
  campaign: Campaign; onSaved: (campaign: Campaign) => void; onClose: () => void
}) {
  const [targeting, setTargeting] = useState<Targeting | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const path = endpoint + '/' + campaign.id

  useEffect(() => {
    const controller = new AbortController()
    fetch(path + '/targeting', { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const saved: Targeting = await response.json()
        if (controller.signal.aborted) return
        setTargeting(saved)
        setSelected(saved.platforms)
      })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load platforms.') })
    return () => controller.abort()
  }, [path, retry])

  const stale = targeting !== null && targeting.version !== campaign.version
  const changed = !!targeting && (selected.length !== targeting.platforms.length
    || selected.some(platform => !targeting.platforms.includes(platform)))

  async function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!targeting || !selected.length || busy || stale) return
    setBusy(true)
    setError('')
    try {
      let saved = targeting
      if (changed) {
        saved = await postJson<Targeting>(path + '/targeting',
          { platforms: selected, expectedVersion: targeting.version }, 'PUT')
        setTargeting(saved)
        setSelected(saved.platforms)
        onSaved({ ...campaign, version: saved.version })
      }
      onSaved(await postJson<Campaign>(path + '/publish', { expectedVersion: saved.version }))
      onClose()
    } catch (failure) {
      setError(errorMessage(failure, 'Could not publish the campaign. Try again.'))
    } finally { setBusy(false) }
  }

  return <form onSubmit={publish} className="space-y-3 border-t border-slate-200 pt-3 text-sm">
    {!targeting && !error && <p role="status">Loading platforms…</p>}
    {stale && <p role="alert" className="text-red-700">The campaign changed. Reload to review it before publishing.</p>}
    {targeting && !stale && <fieldset className="flex flex-wrap gap-3" disabled={busy}>
      <legend className="mb-2 font-medium">Target platforms</legend>
      {Object.entries(platforms).map(([value, label]) => <label key={value} className="flex items-center gap-2">
        <input type="checkbox" checked={selected.includes(value)} onChange={event => {
          const checked = event.target.checked
          setSelected(previous => checked ? [...previous, value] : previous.filter(platform => platform !== value))
        }} />{label}
      </label>)}
    </fieldset>}
    {error && <p role="alert" className="text-red-700">{error}{!targeting && <> <button type="button" className="underline"
      onClick={() => { setError(''); setRetry(value => value + 1) }}>Try again</button></>}</p>}
    <div className="flex flex-wrap gap-2">
      <button className={primary} disabled={busy || stale || !selected.length}>{busy ? 'Publishing…' : 'Publish campaign'}</button>
      <button type="button" className={secondary} disabled={busy} onClick={onClose}>Close</button>
    </div>
  </form>
}
