import { createRoot } from 'react-dom/client'
import { Header } from './Header'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <div className="min-h-screen bg-sky-50/50">
    <Header />
  </div>,
)
