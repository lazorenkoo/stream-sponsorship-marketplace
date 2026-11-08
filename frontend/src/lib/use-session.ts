import { useCallback, useEffect, useState } from 'react'
import { currentSession, logout, type Session } from './api'

export function useSession() {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [signingOut, setSigningOut] = useState(false)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    currentSession().then(value => { if (active) setSession(value) })
      .catch(() => { if (active) setError('Не удалось проверить сессию. Проверьте подключение и повторите попытку.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision])
  const retry = useCallback(() => setRevision(value => value + 1), [])
  async function signOut() {
    if (signingOut) return
    setSigningOut(true)
    setError('')
    try { await logout(); setSession(null) }
    catch { setError('Не удалось выйти. Повторите попытку.') }
    finally { setSigningOut(false) }
  }
  return { session, setSession, loading, error, retry, signOut, signingOut }
}
