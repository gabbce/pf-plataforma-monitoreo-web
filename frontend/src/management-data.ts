import {
  WebError,
  type CatalogDataSource,
  type LatestReadings,
  type Machine,
  type MachineSummary,
  type WebDataSource,
} from './web-data.ts'

async function request(
  path: string,
  signal: AbortSignal,
  expected: 200 | 204 = 200,
  options: RequestInit = {},
) {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  const response = await fetch(`/api/v1${path}`, {
    ...options,
    headers,
    signal,
    cache: 'no-store',
    credentials: 'same-origin',
  })
  if (response.status !== expected) {
    throw new WebError(response.status === 401 || response.status === 403 ? response.status : 500)
  }
  return response
}

async function csrfToken(path: string, signal: AbortSignal) {
  const response = await request(path, signal)
  const data: { token?: unknown } | null = await response.json()
  if (typeof data?.token !== 'string' || data.token.length === 0) throw new WebError(500)
  return data.token
}

export const managementData: CatalogDataSource & Pick<WebDataSource, 'latest'> = {
  async signIn(identifier, password, signal) {
    const token = await csrfToken('/auth/csrf', signal)
    await request('/auth/session', signal, 204, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
      body: JSON.stringify({ identifier, password }),
    })
  },

  async machines(signal) {
    const response = await request('/machines', signal)
    const { items }: { items: MachineSummary[] } = await response.json()
    return items
  },

  async machine(chassis, signal) {
    const response = await request(`/machines/${encodeURIComponent(chassis)}`, signal)
    const machine: Machine = await response.json()
    return machine
  },

  async latest(chassis, signal) {
    const response = await request(`/machines/${encodeURIComponent(chassis)}/measurements/latest`, signal)
    const latest: LatestReadings = await response.json()
    return latest
  },
}

export async function signOut(signal: AbortSignal) {
  try {
    const token = await csrfToken('/auth/csrf?purpose=logout', signal)
    await request('/auth/session', signal, 204, {
      method: 'DELETE',
      headers: { 'X-CSRF-TOKEN': token },
    })
  } catch (error) {
    if (!(error instanceof WebError && error.status === 401)) throw error
  }
}
