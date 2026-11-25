import assert from 'node:assert/strict'
import { test, type TestContext } from 'node:test'

import { managementData, signOut } from '../src/management-data.ts'

const signal = new AbortController().signal

function mockFetch(context: TestContext, ...responses: Response[]) {
  const requests: Request[] = []
  context.mock.method(globalThis, 'fetch', async (url: string, init: RequestInit) => {
    assert.equal(init.signal, signal)
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.cache, 'no-store')
    requests.push(new Request(`http://localhost:8095${url}`, init))
    const response = responses.shift()
    assert.ok(response, 'unexpected request')
    return response
  })
  return requests
}

test('sign-in sends credentials with a pre-login token to the same-origin API', async (context) => {
  const requests = mockFetch(context,
    Response.json({ token: 'login-token' }),
    new Response(null, { status: 204 }),
  )
  await managementData.signIn('demo-user', 'example-password', signal)

  assert.deepEqual(requests.map(({ method, url }) => [method, new URL(url).pathname]), [
    ['GET', '/api/v1/auth/csrf'],
    ['POST', '/api/v1/auth/session'],
  ])
  assert.equal(requests[1].headers.get('X-CSRF-TOKEN'), 'login-token')
  assert.equal(requests[1].headers.get('Content-Type'), 'application/json')
  assert.deepEqual(await requests[1].json(), { identifier: 'demo-user', password: 'example-password' })
})

test('an empty authorized collection is distinct from denial and session expiration', async (context) => {
  mockFetch(context,
    Response.json({ items: [] }),
    new Response(null, { status: 403 }),
    new Response(null, { status: 401 }),
  )
  assert.deepEqual(await managementData.machines(signal), [])
  await assert.rejects(managementData.machine('000123', signal), { status: 403 })
  await assert.rejects(managementData.machines(signal), { status: 401 })
})

test('detail preserves the textual chassis, catalog and absence of contact', async (context) => {
  const machine = {
    chassis: '000123',
    lastAuthenticatedContactAt: null,
    definitions: [{ definitionId: 'engine.rpm', name: 'RPM del motor', unit: 'rpm' }],
  }
  const requests = mockFetch(context, Response.json(machine))
  assert.deepEqual(await managementData.machine(machine.chassis, signal), machine)
  assert.deepEqual(requests.map(({ method, url }) => [method, new URL(url).pathname]), [
    ['GET', '/api/v1/machines/000123'],
  ])
})

test('sign-out uses the session-specific token and DELETE', async (context) => {
  const requests = mockFetch(context,
    Response.json({ token: 'logout-token' }),
    new Response(null, { status: 204 }),
  )
  await signOut(signal)

  assert.deepEqual(requests.map(({ method, url }) => [method, url]), [
    ['GET', 'http://localhost:8095/api/v1/auth/csrf?purpose=logout'],
    ['DELETE', 'http://localhost:8095/api/v1/auth/session'],
  ])
  assert.equal(requests[1].headers.get('X-CSRF-TOKEN'), 'logout-token')
})

test('sign-out recognizes an already expired session at either step', async (context) => {
  const requests = mockFetch(context,
    new Response(null, { status: 401 }),
    Response.json({ token: 'logout-token' }),
    new Response(null, { status: 401 }),
  )

  await signOut(signal)
  assert.deepEqual(requests.map(({ method }) => method), ['GET'])
  await signOut(signal)
  assert.deepEqual(requests.map(({ method }) => method), ['GET', 'GET', 'DELETE'])
})

test('sign-out preserves unexpected responses and requests a fresh token on retry', async (context) => {
  const requests = mockFetch(context,
    Response.json({ token: 'old-token' }),
    new Response(null, { status: 403 }),
    Response.json({ token: 'new-token' }),
    new Response(null, { status: 204 }),
  )

  await assert.rejects(signOut(signal), { status: 403 })
  await signOut(signal)
  assert.deepEqual(requests.filter(({ method }) => method === 'DELETE')
    .map(({ headers }) => headers.get('X-CSRF-TOKEN')), ['old-token', 'new-token'])
})
