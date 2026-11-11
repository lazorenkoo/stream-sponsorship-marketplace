import { useEffect, useState } from 'react'
import { postJson } from './offerApi'
import { platforms } from './StreamerCatalog'

type Candidate = { channelId: string; displayName: string; platform: string }
type Page = { candidates: Candidate[]; nextCursor: string | null }
const button = 'rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium disabled:opacity-50'

export function CampaignInvite({ id, version, close }: { id: string; version: number; close: () => void }) {
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [after, setAfter] = useState('')
  const [next, setNext] = useState<string | null>(null)
  const [status, setStatus] = useState('loading')
  const [retry, setRetry] = useState(0)
  const [sent, setSent] = useState<string[]>([])
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ expectedVersion: String(version), limit: '10' })
    if (after) query.set('after', after)
    fetch('/api/campaigns/' + id + '/candidates?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 409) { setStatus('changed'); return }
        if (!response.ok) throw new Error()
        const page: Page = await response.json()
        if (controller.signal.aborted) return
        setCandidates(previous => after ? [...previous, ...page.candidates] : page.candidates)
        setNext(page.nextCursor)
        setStatus('ready')
      })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [id, version, after, retry])

  async function invite(channel: Candidate) {
    if (busy) return
    setBusy(channel.channelId)
    setError('')
    try {
      await postJson('/api/campaigns/' + id + '/invitations', { channelId: channel.channelId })
      setSent(previous => [...previous, channel.channelId])
    } catch (failure) {
      setError(failure instanceof Error && failure.message === '409'
        ? 'This channel already has a request or is no longer eligible.'
        : 'Could not send the invitation. Try again.')
    } finally { setBusy('') }
  }

  return <section className="space-y-3 border-t border-slate-200 pt-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-semibold">Invite streamers</h3>
      <button type="button" className={button} disabled={!!busy} onClick={close}>Close</button>
    </div>
    {status === 'loading' && <p role="status">Loading channels…</p>}
    {status === 'changed' && <p role="alert">The campaign changed. Reload the page before inviting streamers.</p>}
    {status === 'error' && <p role="alert">Could not load channels. <button type="button" className="underline"
      onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Try again</button></p>}
    {status === 'ready' && !candidates.length && <p>No matching channels.</p>}
    <ul className="divide-y divide-slate-200">
      {candidates.map(channel => <li key={channel.channelId} className="flex flex-wrap items-center justify-between gap-2 py-2">
        <a href={'/streamers/' + channel.channelId} className="break-words text-sm font-medium text-sky-800 underline">
          {channel.displayName} · {platforms[channel.platform] ?? channel.platform}
        </a>
        <button type="button" className={button} disabled={!!busy || status !== 'ready' || sent.includes(channel.channelId)}
          onClick={() => void invite(channel)}>{sent.includes(channel.channelId) ? 'Invitation sent' : busy === channel.channelId ? 'Sending…' : 'Send invitation'}</button>
      </li>)}
    </ul>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {next && status === 'ready' && <button type="button" className={button} disabled={!!busy}
      onClick={() => { setStatus('loading'); setAfter(next) }}>Show more channels</button>}
  </section>
}
