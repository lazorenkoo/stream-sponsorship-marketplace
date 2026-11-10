import { Radio } from 'lucide-react'
import { useEffect, useState } from 'react'

export function Header() {
  const [advertiser, setAdvertiser] = useState(false)
  const [signedOut, setSignedOut] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/users/me', { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!controller.signal.aborted) setSignedOut(response.status === 401)
        if (response.ok) {
          const profile: { roles: string[] } = await response.json()
          if (!controller.signal.aborted) setAdvertiser(profile.roles.includes('ADVERTISER'))
        }
      })
      .catch(() => {})
    return () => controller.abort()
  }, [])

  return (
    <header className="border-b border-sky-100 bg-white">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 py-4 sm:px-8">
        <a href="/" className="flex items-center gap-2 text-xl font-bold tracking-tight text-slate-900">
          <span className="grid size-9 place-items-center rounded-xl bg-sky-100 text-sky-700">
            <Radio size={19} aria-hidden="true" />
          </span>
          StreamBid
        </a>

        <nav aria-label="Marketplace" className="order-last flex w-full gap-1 sm:order-none sm:w-auto">
          <a href="/" aria-current="page" className="rounded-full bg-sky-100 px-4 py-2 text-sm font-semibold text-sky-900">
            Streamers
          </a>
          <a href="/campaigns" className="rounded-full px-4 py-2 text-sm font-medium text-slate-600 hover:bg-sky-50">
            Ad Campaigns
          </a>
        </nav>

        <div className="flex flex-wrap items-center gap-2 text-sm font-semibold">
          {advertiser && <a href="/creatives" className="rounded-lg px-3 py-2 text-slate-700 hover:bg-sky-50">Creatives</a>}
          <a href="/bookings" className="rounded-lg px-3 py-2 text-slate-700 hover:bg-sky-50">Bookings</a>
          <a href="/offers" className="rounded-lg px-3 py-2 text-slate-700 hover:bg-sky-50">Offers</a>
          <a href="/profile" className="rounded-lg px-3 py-2 text-slate-700 hover:bg-sky-50">Profile</a>
          {signedOut && <>
            <a href="/login" className="rounded-lg px-3 py-2 text-slate-700 hover:bg-sky-50">Log in</a>
            <a href="/register" className="rounded-lg bg-sky-700 px-3 py-2 text-white hover:bg-sky-800">Sign up</a>
          </>}
        </div>
      </div>
    </header>
  )
}
