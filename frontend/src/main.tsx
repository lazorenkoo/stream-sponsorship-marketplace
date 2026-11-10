import { StreamerDetails } from './StreamerDetails'
import { StreamerCatalog } from './StreamerCatalog'
import { createRoot } from 'react-dom/client'
import { AuthPage } from './AuthPage'
import { ProfileSettings } from './ProfileSettings'
import { Header } from './Header'
import './styles.css'

const path = window.location.pathname
const streamerId = /^\/streamers\/([0-9a-f-]{36})$/i.exec(path)?.[1]

createRoot(document.getElementById('root')!).render(
  <div className="min-h-screen bg-sky-50/50">
    <Header />
    {path === '/' && <StreamerCatalog />}
    {streamerId && <StreamerDetails id={streamerId} />}
    {path === '/campaigns' && <StreamerCatalog campaigns />}
    {path === '/profile' && <ProfileSettings />}
    {(path === '/login' || path === '/register') && <AuthPage register={path === '/register'} />}
  </div>,
)
