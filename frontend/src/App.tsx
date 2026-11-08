import { useState } from 'react'
import { Button } from './components/ui/button'
import { MonitorPlay, Radio, Sparkles } from 'lucide-react'

const catalogs = {
  streamers: { label: 'Стримеры', title: 'Найдите место для вашего бренда', description: 'Выберите канал, отметьте область на экране и предложите удобное время размещения.' },
  campaigns: { label: 'Рекламные кампании', title: 'Найдите рекламу для своей трансляции', description: 'Изучайте предложения брендов и согласовывайте условия, которые подходят вашему каналу.' },
}

export default function App() {
  const [selected, setSelected] = useState<keyof typeof catalogs>('streamers')
  const catalog = catalogs[selected]
  return (
    <div className="min-h-screen bg-sky-50/60 text-slate-900">
      <header className="border-b border-sky-100 bg-white/90">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-5 px-6 py-5">
          <a href="/" className="flex items-center gap-2 text-xl font-bold tracking-tight"><MonitorPlay className="text-sky-600" />StreamBid</a>
          <nav aria-label="Каталоги" className="flex gap-2">
            {(Object.keys(catalogs) as Array<keyof typeof catalogs>).map(key => (
              <Button variant="ghost" key={key} type="button" aria-pressed={selected === key} onClick={() => setSelected(key)}
                className={`rounded-full px-4 py-2 text-sm font-medium transition ${selected === key ? 'bg-sky-100 text-sky-800' : 'text-slate-600 hover:bg-slate-100'}`}>{catalogs[key].label}</Button>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-6 py-12 md:py-20">
        <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-sky-200 bg-white px-3 py-1.5 text-xs font-semibold text-sky-700"><Sparkles size={14} />Прямой контакт. Понятные условия.</div>
        <section aria-labelledby="catalog-title" className="max-w-3xl">
          <h1 id="catalog-title" className="text-4xl leading-tight font-semibold tracking-tight md:text-6xl">{catalog.title}</h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-600">{catalog.description}</p>
        </section>
        <section aria-label={catalog.label} className="mt-12 rounded-3xl border border-sky-100 bg-white p-8 shadow-sm md:p-12">
          <h2 className="flex items-center gap-3 text-xl font-semibold"><Radio className="text-sky-600" />{catalog.label}</h2>
          <p className="mt-4 max-w-xl leading-relaxed text-slate-500">Войдите в аккаунт, чтобы просматривать каталог и согласовывать размещения.</p>
        </section>
      </main>
    </div>
  )
}
