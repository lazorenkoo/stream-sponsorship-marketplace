import { Ellipsis, UserRound } from 'lucide-react'
import { useEffect, useRef, useState, type PointerEvent, type ReactNode } from 'react'

type Crop = { x: number; y: number; size: number }
const photoPath = '/api/users/me/photo'
const clamp = (value: number, max: number) => Math.max(0, Math.min(max, value))

export function ProfilePhoto({ children }: { children: ReactNode }) {
  const [file, setFile] = useState<File | null>(null)
  const [source, setSource] = useState('')
  const [crop, setCrop] = useState<Crop | null>(null)
  const [revision, setRevision] = useState(0)
  const [hasPhoto, setHasPhoto] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const picker = useRef<HTMLInputElement>(null)
  const photoActions = useRef<HTMLDetailsElement>(null)
  const image = useRef<HTMLImageElement>(null)
  const drag = useRef<{ id: number; x: number; y: number; crop: Crop; resize: boolean } | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch(photoPath, { signal: controller.signal, cache: 'no-store' })
      .then(response => setHasPhoto(response.ok))
      .catch(() => {})
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!file) { setSource(''); return }
    const url = URL.createObjectURL(file)
    setSource(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  function resize(value: number, current: Crop, width: number, height: number) {
    const size = Math.max(1, Math.min(Math.min(width, height), value))
    setCrop({ x: clamp(current.x, width - size), y: clamp(current.y, height - size), size })
  }

  function move(event: PointerEvent<HTMLDivElement>) {
    const start = drag.current
    const photo = image.current
    if (!start || start.id !== event.pointerId || !photo) return
    const bounds = photo.getBoundingClientRect()
    const dx = (event.clientX - start.x) * photo.naturalWidth / bounds.width
    const dy = (event.clientY - start.y) * photo.naturalHeight / bounds.height
    if (start.resize) {
      resize(start.crop.size + (dx + dy) / 2, start.crop, photo.naturalWidth, photo.naturalHeight)
    } else {
      setCrop({ ...start.crop, x: clamp(start.crop.x + dx, photo.naturalWidth - start.crop.size),
        y: clamp(start.crop.y + dy, photo.naturalHeight - start.crop.size) })
    }
  }

  async function save() {
    const photo = image.current
    if (!photo || !crop || pending) return
    setPending(true)
    setError('')
    try {
      const canvas = document.createElement('canvas')
      canvas.width = canvas.height = 512
      const context = canvas.getContext('2d', { alpha: false })
      if (!context) throw new Error()
      context.fillStyle = 'white'
      context.fillRect(0, 0, 512, 512)
      context.drawImage(photo, crop.x, crop.y, crop.size, crop.size, 0, 0, 512, 512)
      const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9))
      if (!blob) throw new Error()
      const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
      if (!csrf.ok) throw new Error()
      const { headerName, token } = await csrf.json()
      const body = new FormData()
      body.set('photo', blob, 'profile.jpg')
      const response = await fetch(photoPath, { method: 'PUT', headers: { [headerName]: token }, body })
      if (!response.ok) throw new Error()
      setHasPhoto(true)
      setRevision(value => value + 1)
      setFile(null)
      setCrop(null)
      setSaved(true)
    } catch {
      setError('Could not save your photo. Try again.')
    } finally { setPending(false) }
  }

  const width = image.current?.naturalWidth || 1
  const height = image.current?.naturalHeight || 1
  return (
    <section aria-label="Profile photo" className="space-y-4">
      <div className="grid items-start gap-5 sm:grid-cols-[auto_minmax(0,1fr)] sm:gap-6">
        <div className="relative w-fit">
          <div className="grid size-44 place-items-center sm:size-52 overflow-hidden rounded-xl border border-sky-200 bg-sky-100 text-sky-700">
            {hasPhoto ? <img className="size-full object-cover" src={photoPath + '?v=' + revision} alt="Current profile photo" />
              : <UserRound size={44} aria-hidden="true" />}
          </div>
          <details ref={photoActions} className="absolute right-2 top-2 z-10">
            <summary aria-label="Profile photo options"
              className="grid size-8 cursor-pointer list-none place-items-center rounded-md bg-white/70 text-slate-700 hover:bg-white hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 [&::-webkit-details-marker]:hidden">
              <Ellipsis size={18} aria-hidden="true" />
            </summary>
            <div className="absolute right-0 top-full mt-2 w-max rounded-lg border border-sky-200 bg-white p-1 shadow-lg">
              <button type="button" disabled={pending}
                className="rounded-md px-3 py-2 text-sm font-semibold text-sky-900 hover:bg-sky-100 focus-visible:outline-2 focus-visible:outline-sky-700 disabled:opacity-60"
                onClick={() => {
                  if (photoActions.current) photoActions.current.open = false
                  picker.current?.click()
                }}>{hasPhoto ? 'Change photo' : 'Upload photo'}</button>
            </div>
          </details>
        </div>
        {children}
      </div>
      <input ref={picker} aria-label="Choose a profile photo" hidden type="file"
        accept="image/jpeg,image/png,image/webp" disabled={pending}
        onChange={event => {
          const next = event.target.files?.[0]
          event.target.value = ''
          if (!next) return
          setSaved(false)
          setCrop(null)
          setFile(null)
          setError('')
          if (next.size > 10 * 1024 * 1024) { setError('Choose a photo under 10 MB.'); return }
          setFile(next)
        }} />
      {source && <>
        <p className="text-sm text-slate-600">Drag the square to move it. Drag its corner to resize it.</p>
        <div className="relative isolate w-fit max-w-full overflow-hidden rounded-lg">
          <img key={source} ref={image} src={source} alt="Photo to crop" draggable={false} className="block max-h-[420px] max-w-full"
            onError={() => { setFile(null); setCrop(null); setError('Choose a valid image file.') }}
            onLoad={event => {
              const { naturalWidth, naturalHeight } = event.currentTarget
              const size = Math.min(naturalWidth, naturalHeight)
              setCrop({ x: (naturalWidth - size) / 2, y: (naturalHeight - size) / 2, size })
            }} />
          {crop && <div className="absolute touch-none cursor-move border-2 border-white shadow-[0_0_0_100vmax_rgb(0_0_0/0.48)]"
            style={{ left: crop.x / width * 100 + '%', top: crop.y / height * 100 + '%', width: crop.size / width * 100 + '%', height: crop.size / height * 100 + '%' }}
            onPointerDown={event => {
              if (pending || event.button !== 0 || drag.current) return
              drag.current = { id: event.pointerId, x: event.clientX, y: event.clientY, crop, resize: event.target !== event.currentTarget }
              event.currentTarget.setPointerCapture(event.pointerId)
            }}
            onPointerMove={move} onLostPointerCapture={() => { drag.current = null }}>
            <span className="absolute bottom-0 right-0 size-5 cursor-nwse-resize rounded-tl bg-white" />
          </div>}
        </div>
        <button type="button" className="rounded-lg bg-sky-700 px-4 py-2 font-semibold text-white shadow-sm hover:bg-sky-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:opacity-60"
          disabled={!crop || pending} onClick={save}>{pending ? 'Saving…' : 'Save photo'}</button>
      </>}
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      {saved && <p role="status">Photo saved.</p>}
    </section>
  )
}
