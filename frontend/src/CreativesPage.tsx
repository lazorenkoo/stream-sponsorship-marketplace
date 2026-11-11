import { useEffect, useState, type FormEvent } from 'react'

type Creative = { id: string; title: string; format: string; widthPx: number; heightPx: number }

const pageSize = 20
const input = 'mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm'
const button = 'rounded-lg bg-sky-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50'

function CreativeCard({ creative }: { creative: Creative }) {
  const [unavailable, setUnavailable] = useState(false)

  return (
    <li className="min-w-0 overflow-hidden rounded-xl border border-sky-100 bg-white">
      <div className="grid aspect-video place-items-center bg-slate-100">
        {unavailable ? <p className="text-sm text-slate-600">Preview unavailable</p>
          : <img src={'/api/creatives/' + creative.id + '/content'} alt={creative.title}
              className="h-full w-full object-contain" loading="lazy" onError={() => setUnavailable(true)} />}
      </div>
      <div className="space-y-1 p-4">
        <h2 className="break-words font-semibold">{creative.title}</h2>
        <p className="text-sm text-slate-600">{creative.format} · {creative.widthPx} × {creative.heightPx} px</p>
      </div>
    </li>
  )
}

export function CreativesPage() {
  const [creatives, setCreatives] = useState<Creative[]>([])
  const [access, setAccess] = useState('loading')
  const [cursor, setCursor] = useState<string | null>(null)
  const [next, setNext] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [listError, setListError] = useState('')
  const [uploadError, setUploadError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/users/me', { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 401) { setAccess('guest'); return }
        if (!response.ok) throw new Error()
        const profile: { roles: string[] } = await response.json()
        if (!controller.signal.aborted) setAccess(profile.roles.includes('ADVERTISER') ? 'allowed' : 'forbidden')
      })
      .catch(() => { if (!controller.signal.aborted) setAccess('error') })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (access !== 'allowed') return
    const controller = new AbortController()
    const query = new URLSearchParams({ limit: String(pageSize + 1) })
    if (cursor) query.set('after', cursor)
    fetch('/api/creatives?' + query, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error()
        const page: Creative[] = await response.json()
        if (controller.signal.aborted) return
        setCreatives(previous => [...(cursor ? previous : []), ...page.slice(0, pageSize)])
        setNext(page.length > pageSize ? page[pageSize - 1].id : null)
      })
      .catch(() => { if (!controller.signal.aborted) setListError('Could not load creatives.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [access, cursor, revision])

  function refresh() {
    setCursor(null)
    setListError('')
    setLoading(true)
    setRevision(value => value + 1)
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (uploading) return
    const form = event.currentTarget
    const body = new FormData(form)
    const file = body.get('file')
    const title = String(body.get('title') ?? '').trim()
    setMessage('')
    setUploadError('')
    if (!title || !(file instanceof File) || !file.size) {
      setUploadError('Enter a title and choose an image.')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      setUploadError('Choose an image no larger than 10 MB.')
      return
    }
    body.set('title', title)
    setUploading(true)
    try {
      const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
      if (!csrf.ok) throw new Error()
      const { headerName, token } = await csrf.json()
      const response = await fetch('/api/creatives', { method: 'POST', headers: { [headerName]: token }, body })
      if (!response.ok) throw new Error(String(response.status))
      form.reset()
      setMessage('Creative uploaded. You can now attach it to an offer.')
      refresh()
    } catch (failure) {
      const status = failure instanceof Error ? failure.message : ''
      if (status === '503') setUploadError('Image storage is temporarily unavailable.')
      else if (status === '400') setUploadError('Check the title and choose a valid PNG, JPEG, WebP or GIF image.')
      else if (status === '413') setUploadError('Choose an image no larger than 10 MB.')
      else setUploadError('Could not upload the creative. Try again.')
    } finally { setUploading(false) }
  }

  return (
    <main className="mx-auto max-w-5xl space-y-5 px-4 py-8 text-slate-900">
      <h1 className="text-2xl font-semibold">Creatives</h1>
      {access === 'loading' && <p role="status">Checking your account…</p>}
      {access === 'guest' && <p><a href="/login" className="text-sky-800 underline">Log in</a> to manage creatives.</p>}
      {access === 'forbidden' && <p>Creatives are available to advertisers.</p>}
      {access === 'error' && <p role="alert">Could not check your account. Refresh to try again.</p>}
      {access === 'allowed' && <>
        <form onSubmit={upload} className="max-w-xl space-y-3 rounded-xl border border-sky-100 bg-white p-4">
          <label className="block text-sm font-medium">Title<input className={input} name="title" required maxLength={120} disabled={uploading} /></label>
          <label className="block text-sm font-medium">Image<input className={input} name="file" type="file" required
            accept="image/png,image/jpeg,image/webp,image/gif" disabled={uploading} /></label>
          <p className="text-xs text-slate-600">PNG, JPEG, WebP or GIF · Up to 10 MB. GIFs can be animated.</p>
          {uploadError && <p role="alert" className="text-sm text-red-700">{uploadError}</p>}
          {message && <p role="status" className="text-sm text-sky-800">{message}</p>}
          <button className={button} disabled={uploading}>{uploading ? 'Uploading…' : 'Upload creative'}</button>
        </form>
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">Your creatives</h2>
          <button type="button" className="text-sm font-medium text-sky-800 underline disabled:opacity-50"
            disabled={loading} onClick={refresh}>Refresh status</button>
        </div>
        {loading && <p role="status">Loading creatives…</p>}
        {listError && <p role="alert">{listError}</p>}
        {!loading && !listError && !creatives.length && <p>No creatives yet.</p>}
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {creatives.map(creative => <CreativeCard key={creative.id} creative={creative} />)}
        </ul>
        {next && !loading && !listError && <button type="button" className={button}
          onClick={() => { setLoading(true); setCursor(next) }}>Show more</button>}
      </>}
    </main>
  )
}
