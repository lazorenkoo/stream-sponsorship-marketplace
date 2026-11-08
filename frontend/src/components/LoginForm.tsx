import { useState, type FormEvent } from 'react'
import { LogIn, LoaderCircle } from 'lucide-react'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { login, type Session } from '../lib/api'

type Props = { onSuccess: (session: Session) => void }
export function LoginForm({ onSuccess }: Props) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const form = event.currentTarget
    const data = new FormData(form)
    setPending(true)
    setError('')
    try {
      const session = await login(String(data.get('email')).trim(), String(data.get('password')))
      form.reset()
      onSuccess(session)
    } catch (failure) {
      setError(failure instanceof Error && failure.name !== 'TypeError' ? failure.message : 'Не удалось связаться с сервером. Повторите попытку.')
    } finally { setPending(false) }
  }
  return (
    <section id="login" aria-labelledby="login-title" className="mt-10 max-w-md rounded-3xl border border-sky-100 bg-white p-7 shadow-sm">
      <h2 id="login-title" className="text-xl font-semibold">С возвращением</h2>
      <p className="mt-2 text-sm text-slate-500">Войдите, чтобы найти партнёров и согласовать размещение.</p>
      <form onSubmit={submit} className="mt-6 space-y-4" aria-busy={pending}>
        <div className="space-y-2"><label htmlFor="email" className="text-sm font-medium">Email</label>
          <Input id="email" name="email" type="email" autoComplete="username" maxLength={254} required disabled={pending} /></div>
        <div className="space-y-2"><label htmlFor="password" className="text-sm font-medium">Пароль</label>
          <Input id="password" name="password" type="password" autoComplete="current-password" required disabled={pending} /></div>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <Button type="submit" disabled={pending} className="w-full">{pending ? <LoaderCircle className="animate-spin" /> : <LogIn />}{pending ? 'Входим…' : 'Войти'}</Button>
      </form>
    </section>
  )
}
