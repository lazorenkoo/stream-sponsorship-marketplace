export type Session = { id: string; email: string; displayName: string; roles: string[] }
type Csrf = { token: string; headerName: string }
export class ApiError extends Error {
  readonly status: number
  constructor(status: number) {
    super(status === 401 ? 'Проверьте email и пароль или войдите заново.' : status === 403 ? 'Недостаточно прав. Обновите страницу и повторите действие.' : 'Не удалось выполнить запрос. Попробуйте ещё раз.')
    this.status = status
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/api/')) throw new Error('Invalid API path')
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const response = await fetch('/api/auth/csrf', { credentials: 'include', cache: 'no-store' })
    if (!response.ok) throw new ApiError(response.status)
    const csrf: Csrf = await response.json()
    headers.set(csrf.headerName, csrf.token)
  }
  const response = await fetch(path, { ...init, method, headers, credentials: 'include', cache: 'no-store' })
  if (!response.ok) throw new ApiError(response.status)
  return response.status === 204 ? undefined as T : await response.json() as T
}

export async function currentSession(): Promise<Session | null> {
  try { return await api<Session>('/api/users/me') }
  catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}
export async function login(email: string, password: string): Promise<Session> {
  await api<void>('/api/auth/login', { method: 'POST', body: new URLSearchParams({ email, password }) })
  return api<Session>('/api/users/me')
}
export async function logout(): Promise<void> { await api<void>('/api/auth/logout', { method: 'POST' }) }
