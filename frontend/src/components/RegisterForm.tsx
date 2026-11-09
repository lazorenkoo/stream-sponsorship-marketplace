import { useState, type FormEvent } from 'react'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { api, ApiError } from '../lib/api'

export function RegisterForm({ onCreated, onBack }: { onCreated: () => void; onBack: () => void }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const form = event.currentTarget
    const fields = new FormData(form)
    setPending(true)
    setError('')
    try {
      await api('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(Object.fromEntries(fields)),
      })
      form.reset()
      onCreated()
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status === 409
        ? 'Аккаунт с таким email уже существует.'
        : 'Не удалось зарегистрироваться. Проверьте данные и повторите попытку.')
    } finally {
      setPending(false)
    }
  }

  return (
    <section id="login" aria-labelledby="register-title" className="mt-10 max-w-md rounded-3xl border border-sky-100 bg-white p-7 shadow-sm">
      <h2 id="register-title" className="text-xl font-semibold">Создать аккаунт</h2>
      <form onSubmit={submit} className="mt-6 space-y-4" aria-busy={pending}>
        <label className="block space-y-2 text-sm font-medium">Имя
          <Input name="displayName" autoComplete="nickname" maxLength={80} required disabled={pending} />
        </label>
        <label className="block space-y-2 text-sm font-medium">Email
          <Input name="email" type="email" autoComplete="email" maxLength={254} required disabled={pending} />
        </label>
        <label className="block space-y-2 text-sm font-medium">Пароль
          <Input name="password" type="password" autoComplete="new-password" minLength={10} maxLength={72} required disabled={pending} />
        </label>
        <label className="block space-y-2 text-sm font-medium">Тип аккаунта
          <select name="role" className="block w-full rounded-xl border border-slate-200 bg-white p-3" disabled={pending}>
            <option value="ADVERTISER">Рекламодатель</option>
            <option value="STREAMER">Стример</option>
          </select>
        </label>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <Button type="submit" disabled={pending}>{pending ? 'Создаём аккаунт…' : 'Зарегистрироваться'}</Button>
        <Button variant="ghost" disabled={pending} onClick={onBack}>Ко входу</Button>
      </form>
    </section>
  )
}
