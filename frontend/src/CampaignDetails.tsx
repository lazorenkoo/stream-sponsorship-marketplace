import { useEffect, useState } from 'react'
import { postJson } from './offerApi'
import { platforms, type PublishedCampaign } from './StreamerCatalog'

type Channel = { id: string; displayName: string; platform: string; authorizationRequired: boolean }
type Participation = {
  id: string
  channelId: string
  direction: 'APPLICATION' | 'INVITATION'
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'
  version: number
}
const statusLabels = { PENDING: 'Pending', ACCEPTED: 'Accepted', REJECTED: 'Declined', WITHDRAWN: 'Withdrawn' }
const primary = 'rounded-lg bg-sky-700 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-800 disabled:opacity-50'
const secondary = 'rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold hover:bg-sky-50 disabled:opacity-50'

export function CampaignDetails({ id }: { id: string }) {
  const [campaign, setCampaign] = useState<PublishedCampaign | null>(null)
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/catalog/campaigns/' + id, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 404) { setStatus('unavailable'); return }
        if (!response.ok) throw new Error()
        const published: PublishedCampaign = await response.json()
        if (controller.signal.aborted) return
        setCampaign(published)
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [id, retry])

  return <main className="mx-auto max-w-3xl space-y-4 px-4 py-8 text-slate-900">
    <a href="/campaigns" className="text-sm font-medium text-sky-800 hover:underline">← All ad campaigns</a>
    {status === 'loading' && <p role="status">Loading campaign…</p>}
    {status === 'unavailable' && <p>This campaign is no longer published.</p>}
    {status === 'error' && <p role="alert">Could not load the campaign. <button type="button" className="underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {campaign && <>
      <article className="space-y-3 rounded-xl border border-sky-100 bg-white p-5">
        <h1 className="break-words text-2xl font-semibold">{campaign.title}</h1>
        <p className="text-sm text-slate-600">{campaign.platforms.map(platform => platforms[platform] ?? platform).join(' · ')}</p>
        {campaign.description && <p className="whitespace-pre-wrap break-words">{campaign.description}</p>}
      </article>
      <CampaignApplications campaign={campaign} />
    </>}
  </main>
}

function CampaignApplications({ campaign }: { campaign: PublishedCampaign }) {
  const [channels, setChannels] = useState<Channel[]>([])
  const [requests, setRequests] = useState<Participation[]>([])
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal, cache: 'no-store' as const }
    async function load() {
      const me = await fetch('/api/users/me', options)
      if (controller.signal.aborted) return
      if (me.status === 401) { setStatus('guest'); return }
      if (!me.ok) throw new Error()
      const profile: { roles: string[] } = await me.json()
      if (controller.signal.aborted) return
      if (!profile.roles.includes('STREAMER')) { setStatus('hidden'); return }
      const linkedResponse = await fetch('/api/channels', options)
      if (!linkedResponse.ok) throw new Error()
      const linked: Channel[] = await linkedResponse.json()
      const existing: Participation[] = []
      const query = new URLSearchParams({ campaignId: campaign.id, limit: '100' })
      for (;;) {
        const response = await fetch('/api/participations?' + query, options)
        if (!response.ok) throw new Error()
        existing.push(...await response.json() as Participation[])
        const after = response.headers.get('X-Next-Cursor')
        if (!after) break
        query.set('after', after)
      }
      if (controller.signal.aborted) return
      setChannels(linked)
      setRequests(existing)
      setStatus('ready')
    }
    load().catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [campaign.id, retry])

  async function change(channelId: string, request?: Participation, action: 'WITHDRAW' | 'ACCEPT' | 'REJECT' = 'WITHDRAW') {
    if (busy) return
    setBusy(channelId)
    setMessage('')
    setError('')
    try {
      const saved = request
        ? await postJson<Participation>('/api/participations/' + request.id + '/decision',
          { action, expectedVersion: request.version })
        : await postJson<Participation>('/api/campaigns/' + campaign.id + '/applications', { channelId })
      setRequests(previous => [...previous.filter(request => request.id !== saved.id), saved])
      setMessage(!request ? 'Application sent to the advertiser.'
        : action === 'WITHDRAW' ? 'Application withdrawn.' : action === 'ACCEPT' ? 'Invitation accepted.' : 'Invitation declined.')
    } catch (failure) {
      const conflict = failure instanceof Error && (failure.message === '409' || failure.message === '404')
      setError(conflict
        ? 'The campaign, channel or request has changed. Refresh the application status before trying again.'
        : 'Could not update the request. Try again.')
    } finally { setBusy('') }
  }

  function reload() {
    setStatus('loading')
    setError('')
    setMessage('')
    setRetry(value => value + 1)
  }

  if (status === 'hidden') return null
  if (status === 'guest') return <p><a className="font-medium text-sky-800 underline" href="/login">Log in as a streamer</a> to apply.</p>
  const matching = channels.filter(channel => campaign.platforms.includes(channel.platform))
  return <section className="space-y-3 rounded-xl border border-sky-100 bg-white p-5">
    <h2 className="font-semibold">Apply with your channel</h2>
    {status === 'loading' && <p role="status">Loading channels and applications…</p>}
    {status === 'error' && <p role="alert">Could not load applications. <button type="button" className="underline" onClick={reload}>Try again</button></p>}
    {status === 'ready' && !matching.length && <p className="text-sm">
      {channels.length ? 'Your connected channels do not match the campaign platforms.' : 'Connect a channel before applying.'}{' '}
      <a href="/profile" className="font-medium text-sky-800 underline">Manage channels</a>
    </p>}
    {status === 'ready' && <ul className="divide-y divide-slate-200">
      {matching.map(channel => {
        const request = requests.find(item => item.channelId === channel.id)
        return <li key={channel.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
          <div className="min-w-0">
            <p className="break-words text-sm font-medium">{channel.displayName} · {platforms[channel.platform] ?? channel.platform}</p>
            {request && <p className="text-sm text-slate-600">{request.direction === 'APPLICATION' ? 'Application' : 'Invitation'} · {statusLabels[request.status]}</p>}
            {channel.authorizationRequired && <a className="text-sm text-sky-800 underline" href="/profile">Reconnect channel</a>}
          </div>
          {!request && !channel.authorizationRequired && <button type="button" className={primary} disabled={!!busy}
            onClick={() => void change(channel.id)}>{busy === channel.id ? 'Sending…' : 'Send application'}</button>}
          {request?.direction === 'INVITATION' && request.status === 'PENDING' && <div className="flex flex-wrap gap-2">
            <button type="button" className={primary} disabled={!!busy || channel.authorizationRequired}
              onClick={() => void change(channel.id, request, 'ACCEPT')}>Accept invitation</button>
            <button type="button" className={secondary} disabled={!!busy}
              onClick={() => void change(channel.id, request, 'REJECT')}>Decline invitation</button>
          </div>}
          {request?.direction === 'APPLICATION' && request.status === 'PENDING' && <button type="button"
            className={secondary} disabled={!!busy} onClick={() => void change(channel.id, request)}>
            {busy === channel.id ? 'Withdrawing…' : 'Withdraw application'}
          </button>}
        </li>
      })}
    </ul>}
    {message && <p role="status" className="text-sm text-sky-900">{message}</p>}
    {error && <p role="alert" className="text-sm text-red-700">{error}{' '}
      <button type="button" className="underline" onClick={reload}>Refresh status</button>
    </p>}
  </section>
}
