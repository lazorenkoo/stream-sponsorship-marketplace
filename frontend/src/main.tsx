import { createRoot } from 'react-dom/client'
import { AuthPage } from './AuthPage'
import { Header } from './Header'
import './styles.css'

const path = window.location.pathname

createRoot(document.getElementById('root')!).render(
  <div className="min-h-screen bg-sky-50/50">
    <Header />
    {(path === '/login' || path === '/register') && <AuthPage register={path === '/register'} />}
  </div>,
)
