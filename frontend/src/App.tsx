import { useState } from 'react'
import { Button } from './components/ui/button'
import { RegisterForm } from './components/RegisterForm'
import { LoginForm } from './components/LoginForm'
import { CatalogResults } from './components/CatalogResults'
import { useSession } from './lib/use-session'
import { MonitorPlay, Radio, Sparkles } from 'lucide-react'

const catalogs = {
  streamers: { label: 'Стримеры', title: 'Найдите место для вашего бренда', description: 'Выберите канал, отметьте область на экране и предложите удобное время размещения.' },
  campaigns: { label: 'Рекламные кампании', title: 'Найдите рекламу для своей трансляции', description: 'Изучайте предложения брендов и согласовывайте условия, которые подходят вашему каналу.' },
}

export default function App() {
  const [selected, setSelected] = useState<keyof typeof catalogs>('streamers')
  const catalog = catalogs[selected]
  const auth = useSession()
  const [registering, setRegistering] = useState(false)
  const [accountCreated, setAccountCreated] = useState(false)
  return (
    <div className="min-h-screen bg-sky-50/60 text-slate-900">
      <header className="border-b border-sky-100 bg-white/90">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-5 px-6 py-5">
          <a href="/" className="flex items-center gap-2 text-xl font-bold tracking-tight"><MonitorPlay className="text-sky-600" />StreamBid</a>
          <nav aria-label="Каталоги" className="flex flex-wrap gap-2">
            {(Object.keys(catalogs) as Array<keyof typeof catalogs>).map(key => (
              <Button variant="ghost" key={key} type="button" aria-pressed={selected === key} onClick={() => setSelected(key)}
                className={`rounded-full px-4 py-2 text-sm font-medium transition ${selected === key ? 'bg-sky-100 text-sky-800' : 'text-slate-600 hover:bg-slate-100'}`}>{catalogs[key].label}</Button>
            ))}
          </nav>
          <div className="flex items-center gap-3 text-sm">
            {auth.loading ? <span role="status" className="text-slate-500">Проверяем вход…</span> : auth.session ? <>
              <span className="max-w-40 truncate font-medium">{auth.session.displayName}</span>
              <Button variant="outline" size="sm" onClick={auth.signOut} disabled={auth.signingOut}>{auth.signingOut ? 'Выходим…' : 'Выйти'}</Button>
            </> : !auth.error && <Button asChild variant="outline" size="sm"><a href="#login">Войти</a></Button>}
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-6 py-12 md:py-20">
        <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-xs font-semibold text-sky-700"><Sparkles size={14} />Прямой контакт. Понятные условия.</div>
        <section aria-labelledby="catalog-title" className="max-w-3xl">
          <h1 id="catalog-title" className="text-4xl leading-tight font-semibold tracking-tight md:text-6xl">{catalog.title}</h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-600">{catalog.description}</p>
        </section>
        {auth.error && <div role="alert" className="mt-8 flex flex-wrap items-center gap-4 rounded-xl bg-red-50 p-4 text-sm text-red-800">{auth.error}<Button variant="outline" onClick={auth.retry}>Повторить</Button></div>}
        {!auth.loading && !auth.error && !auth.session && (registering
          ? <RegisterForm onBack={() => setRegistering(false)} onCreated={() => { setRegistering(false); setAccountCreated(true) }} />
          : <>
              {accountCreated && <p role="status" className="mt-6 text-sm text-sky-800">Аккаунт создан. Теперь можно войти.</p>}
              <LoginForm onSuccess={session => { setAccountCreated(false); auth.setSession(session) }} />
              <Button className="mt-3" variant="ghost" onClick={() => setRegistering(true)}>Создать аккаунт</Button>
            </>)}
        <section aria-label={catalog.label} className="mt-12 rounded-3xl border border-sky-100 bg-white p-8 shadow-sm md:p-12">
          <h2 className="flex items-center gap-3 text-xl font-semibold"><Radio className="text-sky-600" />{catalog.label}</h2>
          {auth.session ? <CatalogResults key={selected} kind={selected} /> : !auth.loading && <p className="mt-4 max-w-xl leading-relaxed text-slate-500">Войдите в аккаунт, чтобы просматривать каталог и согласовывать размещения.</p>}
        </section>
      </main>
    </div>
  )
}
