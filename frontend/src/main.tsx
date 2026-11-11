import { CampaignDetails } from './CampaignDetails'
import { CampaignsPage } from './CampaignsPage'
import { CreativesPage } from './CreativesPage'
import { BookingsPage } from './BookingsPage'
import { OffersPage } from './OffersPage'
import { StreamerDetails } from './StreamerDetails'
import { StreamerCatalog } from './StreamerCatalog'
import { createRoot } from 'react-dom/client'
import { AuthPage } from './AuthPage'
import { ProfileSettings } from './ProfileSettings'
import { Header } from './Header'
import './styles.css'

const path = window.location.pathname
const streamerId = /^\/streamers\/([0-9a-f-]{36})$/i.exec(path)?.[1]

const campaignId = /^\/campaigns\/([0-9a-f-]{36})$/i.exec(path)?.[1]
const bookingId = /^\/bookings\/([0-9a-f-]{36})$/i.exec(path)?.[1]

createRoot(document.getElementById('root')!).render(
  <div className="min-h-screen bg-sky-50/50">
    <Header />
    {path === '/' && <StreamerCatalog />}
    {streamerId && <StreamerDetails id={streamerId} />}
    {path === '/campaigns' && <StreamerCatalog campaigns />}
    {campaignId && <CampaignDetails id={campaignId} />}
    {path === '/campaigns/manage' && <CampaignsPage />}
    {(path === '/bookings' || bookingId) && <BookingsPage id={bookingId} />}
    {path === '/creatives' && <CreativesPage />}
    {path === '/offers' && <OffersPage />}
    {path === '/profile' && <ProfileSettings />}
    {(path === '/login' || path === '/register') && <AuthPage register={path === '/register'} />}
  </div>,
)
