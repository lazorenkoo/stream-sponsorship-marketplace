import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { currentSession, login, logout, ApiError } from '../src/lib/api.ts'
const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })

test('login obtains CSRF, submits form credentials and reads actual session', async () => {
  const calls: Array<{ path: string; init?: RequestInit }> = []
  const replies = [Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }), new Response(null, { status: 204 }), Response.json({ displayName: 'Owner', roles: ['ADVERTISER'] })]
  globalThis.fetch = async (path, init) => { calls.push({ path: String(path), init }); return replies.shift()! }
  assert.equal((await login('owner@example.com', 'test password')).displayName, 'Owner')
  assert.equal(calls[1].path, '/api/auth/login')
  assert.equal(new Headers(calls[1].init?.headers).get('X-CSRF-TOKEN'), 'test-token')
  assert.equal((calls[1].init?.body as URLSearchParams).get('email'), 'owner@example.com')
  assert.equal(calls[1].init?.credentials, 'include')
})
test('anonymous session is null but server failure remains an error', async () => {
  globalThis.fetch = async () => new Response(null, { status: 401 })
  assert.equal(await currentSession(), null)
  globalThis.fetch = async () => new Response(null, { status: 503 })
  await assert.rejects(currentSession(), error => error instanceof ApiError && error.status === 503)
})
test('logout requests a fresh CSRF token and accepts an empty success response', async () => {
  let calls = 0
  globalThis.fetch = async () => ++calls === 1 ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' }) : new Response(null, { status: 204 })
  await logout()
  assert.equal(calls, 2)
})
