import { ProfilePhoto } from './ProfilePhoto'
import { useEffect, useState, type FormEvent } from 'react'

type Settings = { displayName: string; coverUrl: string | null; channelDescription: string | null }
type Channel = { id: string; displayName: string; platform: string; authorizationRequired: boolean }
const field = 'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2'
const button = 'rounded-lg px-4 py-2.5 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:opacity-60'

async function write(path: string, method: string, payload?: Settings | { listed: boolean }) {
  const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
  if (!csrf.ok) throw new Error()
  const { headerName, token } = await csrf.json()
  return fetch(path, {
    method,
    headers: { [headerName]: token, 'Content-Type': 'application/json' },
    body: payload ? JSON.stringify(payload) : undefined,
  })
}

function ChannelVisibility({ channel }: { channel: Channel }) {
  const path = '/api/channels/' + channel.id + '/listing'
  const [listed, setListed] = useState<boolean | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch(path, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const listing = await response.json() as { listed: boolean }
        if (!controller.signal.aborted) setListed(listing.listed)
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('Could not load marketplace visibility.')
      })
    return () => controller.abort()
  }, [path, retry])

  async function update(visible: boolean) {
    setPending(true)
    setError('')
    try {
      const response = await write(path, 'PUT', { listed: visible })
      if (response.status === 409) {
        setListed(null)
        setError('Reconnect your channel before showing it in the marketplace.')
        return
      }
      if (!response.ok) throw new Error()
      const listing = await response.json() as { listed: boolean }
      setListed(listing.listed)
    } catch {
      setError('Could not update marketplace visibility. Try again.')
    } finally { setPending(false) }
  }

  const visible = listed === true && !channel.authorizationRequired
  return (
    <div className="space-y-2 border-t border-slate-100 pt-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="break-words text-sm font-medium">{channel.displayName}</p>
          <p className="text-xs text-slate-600">{channel.platform} · {channel.authorizationRequired ? 'Reconnect required' : 'Connected'}</p>
        </div>
        <label className="flex items-center gap-2 text-sm font-medium">
          <span>Show in marketplace</span>
          <input type="checkbox" role="switch" checked={visible}
            aria-label={'Show ' + channel.displayName + ' in marketplace'}
            disabled={listed === null || pending || channel.authorizationRequired}
            onChange={event => void update(event.target.checked)} className="peer sr-only" />
          <span aria-hidden="true" className="relative h-6 w-11 rounded-full bg-slate-400 transition-colors after:absolute after:left-0.5 after:top-0.5 after:size-5 after:rounded-full after:bg-white after:transition-transform peer-checked:bg-sky-700 peer-checked:after:translate-x-5 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-sky-700 peer-disabled:opacity-60" />
        </label>
      </div>
      <p role="status" className="text-xs text-slate-600">
        {pending ? 'Saving…' : listed === null ? (error ? 'Visibility unavailable' : 'Checking visibility…') : visible ? 'Visible in marketplace' : 'Hidden from marketplace'}
      </p>
      {error && <p role="alert" className="text-sm text-red-700">{error}{listed === null && <button
        type="button" className="ml-2 font-medium underline"
        onClick={() => { setError(''); setRetry(value => value + 1) }}>Refresh status</button>}</p>}
    </div>
  )
}

export function ProfileSettings() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [channels, setChannels] = useState<Channel[] | null>(null)
  const [streamer, setStreamer] = useState(false)
  const [pending, setPending] = useState(false)
  const twitchResult = new URLSearchParams(window.location.search).get('twitch')
  const [message, setMessage] = useState(twitchResult === 'connected' ? 'Twitch channel connected.' : '')
  const [error, setError] = useState(twitchResult === 'failed' ? 'Could not connect Twitch. Try again.' : '')
  const [signedOut, setSignedOut] = useState(false)

  useEffect(() => {
    const url = new URL(window.location.href)
    if (url.searchParams.has('twitch')) {
      url.searchParams.delete('twitch')
      window.history.replaceState(window.history.state, '', url)
    }
    const controller = new AbortController()
    async function load() {
      const options = { signal: controller.signal, cache: 'no-store' as const }
      const profile = await fetch('/api/users/me', options)
      if (profile.status === 401) { setSignedOut(true); return }
      if (!profile.ok) throw new Error()
      const user = await profile.json() as { roles: string[] }
      const details = await fetch('/api/users/me/settings', options)
      if (!details.ok) throw new Error()
      setSettings(await details.json())
      if (user.roles.includes('STREAMER')) {
        setStreamer(true)
        const linked = await fetch('/api/channels', options)
        if (!linked.ok) throw new Error()
        setChannels(await linked.json())
      }
    }
    load().catch(() => { if (!controller.signal.aborted) setError('Could not load profile settings. Refresh to try again.') })
    return () => controller.abort()
  }, [])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!settings || pending) return
    setPending(true)
    setError('')
    setMessage('')
    try {
      const response = await write('/api/users/me/settings', 'PUT', settings)
      if (!response.ok) throw new Error()
      setSettings(await response.json())
      setMessage('Profile saved.')
    } catch {
      setError('Could not save your profile. Check your profile details and try again.')
    } finally { setPending(false) }
  }

  async function connect() {
    setPending(true)
    setError('')
    setMessage('')
    try {
      const response = await write('/api/channels/connect/twitch', 'POST')
      if (response.status === 404 || response.status === 503) {
        setError('Twitch connection is unavailable on this server.')
        return
      }
      if (!response.ok) throw new Error()
      const { authorizationUrl } = await response.json()
      const target = new URL(authorizationUrl)
      if (target.origin !== 'https://id.twitch.tv') throw new Error()
      window.location.assign(target.href)
    } catch {
      setError('Could not start Twitch authorization. Please try again.')
    } finally { setPending(false) }
  }

  return (
    <main className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:py-8 text-slate-900">
      <h1 className="text-2xl font-semibold tracking-tight">Profile settings</h1>
      {signedOut && <p><a className="text-sky-800 underline" href="/login">Log in</a> to edit your profile.</p>}
      {error && <p role="alert" className="text-red-700">{error}</p>}
      {message && <p role="status" className="text-sky-800">{message}</p>}
      {!settings && !signedOut && !error && <p role="status">Loading profile…</p>}
      {settings && <div className="divide-y divide-slate-200 rounded-xl border border-slate-200 bg-white">
        <div className="p-4 sm:p-5">
          <ProfilePhoto>
            <form id="profile-settings-form" onSubmit={save} className="min-w-0 space-y-4">
              <label className="block text-sm font-medium">Display name
                <input className={field} required maxLength={80} value={settings.displayName} onChange={event => setSettings({ ...settings, displayName: event.target.value })} />
              </label>
              {streamer && <label className="block text-sm font-medium">Channel description
                <textarea className={field + ' min-h-24 resize-y'} rows={3} maxLength={500}
                  placeholder="Tell advertisers what you stream."
                  value={settings.channelDescription ?? ''}
                  onChange={event => setSettings({ ...settings, channelDescription: event.target.value })} />
              </label>}
            </form>
          </ProfilePhoto>
        </div>
        {streamer && <section className="space-y-3 p-4 sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="font-semibold">Connected channels</h2>
              {channels?.length === 0 && <p className="mt-1 text-sm text-slate-600">No channels connected yet.</p>}
            </div>
            <button type="button" className={button + ' border border-sky-300 bg-sky-50 text-sky-900 hover:bg-sky-100'} disabled={pending} onClick={connect}>Connect Twitch</button>
          </div>
          {channels?.map(channel => <ChannelVisibility key={channel.id} channel={channel} />)}
        </section>}
        <div className="flex justify-end px-4 py-3 sm:px-5">
          <button type="submit" form="profile-settings-form"
            className={button + ' bg-sky-700 text-white hover:bg-sky-800'} disabled={pending}>Save profile</button>
        </div>
      </div>}
    </main>
  )
}
