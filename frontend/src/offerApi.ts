export async function postJson<T>(path: string, body?: object, method: 'POST' | 'PUT' | 'DELETE' = 'POST'): Promise<T> {
  const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
  if (!csrf.ok) throw new Error()
  const { headerName, token } = await csrf.json()
  const response = await fetch(path, {
    method,
    headers: { [headerName]: token, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(String(response.status))
  return response.status === 204 ? undefined as T : response.json()
}
