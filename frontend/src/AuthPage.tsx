import { useState, type FormEvent } from 'react'

const inputClass = 'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900'

export function AuthPage({ register }: { register: boolean }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const title = register ? 'Sign up' : 'Log in'
  const registered = !register && new URLSearchParams(window.location.search).get('registered') === '1'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const fields = new FormData(event.currentTarget)
    const email = String(fields.get('email')).trim()
    const password = String(fields.get('password'))
    setPending(true)
    setError('')
    try {
      const csrfResponse = await fetch('/api/auth/csrf', { cache: 'no-store' })
      if (!csrfResponse.ok) throw new Error('Session setup failed')
      const csrf: { headerName: string; token: string } = await csrfResponse.json()
      const headers = new Headers({ [csrf.headerName]: csrf.token })
      let body: BodyInit = new URLSearchParams({ email, password })
      if (register) {
        headers.set('Content-Type', 'application/json')
        body = JSON.stringify({ email, password, displayName: String(fields.get('displayName')).trim(), role: fields.get('role') })
      }
      const response = await fetch(register ? '/api/auth/register' : '/api/auth/login', { method: 'POST', headers, body })
      if (response.ok) {
        window.location.assign(register ? '/login?registered=1' : '/')
        return
      }
      if (register && response.status === 409) setError('An account with this email already exists.')
      else if (!register && response.status === 401) setError('Check your email and password and try again.')
      else if (response.status === 400) setError('Check the information you entered and try again.')
      else setError('We could not complete your request. Please try again.')
    } catch {
      setError('We could not reach the server. Please try again.')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="mx-auto max-w-md px-4 py-12">
      <section className="rounded-2xl border border-sky-100 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
        {registered && <p role="status" className="mt-3 text-sm text-sky-800">Account created. Log in to continue.</p>}
        <form onSubmit={submit} className="mt-5" aria-busy={pending}>
          <fieldset disabled={pending} className="space-y-4 text-sm font-medium text-slate-700">
            {register && <label className="block">Name
              <input className={inputClass} name="displayName" autoComplete="nickname" maxLength={80} required />
            </label>}
            <label className="block">{register ? 'Email' : 'Email or username'}
              <input className={inputClass} name="email" type={register ? 'email' : 'text'} autoComplete="username" maxLength={254} required />
            </label>
            <label className="block">Password
              <input className={inputClass} name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'}
                minLength={register ? 10 : undefined} maxLength={72} required />
              {register && <span className="mt-1 block text-xs text-slate-600">Use at least 10 characters.</span>}
            </label>
            {register && <label className="block">Account type
              <select className={inputClass} name="role" required>
                <option value="ADVERTISER">Advertiser</option>
                <option value="STREAMER">Streamer</option>
              </select>
            </label>}
            {error && <p role="alert" className="text-red-700">{error}</p>}
            <button className="w-full rounded-lg bg-sky-700 px-4 py-2.5 font-semibold text-white hover:bg-sky-800 disabled:opacity-60" type="submit">
              {pending ? 'Please wait…' : title}
            </button>
          </fieldset>
        </form>
        <p className="mt-4 text-sm text-slate-600">
          {register ? 'Already have an account?' : 'New to StreamBid?'}{' '}
          <a className="font-semibold text-sky-800 underline" href={register ? '/login' : '/register'}>
            {register ? 'Log in' : 'Sign up'}
          </a>
        </p>
      </section>
    </main>
  )
}
