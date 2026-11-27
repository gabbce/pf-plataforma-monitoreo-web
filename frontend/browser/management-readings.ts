import assert from 'node:assert/strict'
import { after, before, test, type TestContext } from 'node:test'

import { chromium, type Browser, type Page } from 'playwright'
import { createServer, type ViteDevServer } from 'vite'

import machineExample from '../../docs/contratos/web/ejemplos/machine-detail.json' with { type: 'json' }
import latestExample from '../../docs/contratos/web/ejemplos/latest-normal.json' with { type: 'json' }
import partialExample from '../../docs/contratos/web/ejemplos/latest-partial.json' with { type: 'json' }

const machine = machineExample.value
const otherMachine = { ...machine, chassis: '000456', lastAuthenticatedContactAt: null }
const machinePath = `/api/v1/machines/${machine.chassis}`
const latestPath = `${machinePath}/measurements/latest`
let server: ViteDevServer | undefined
let browser: Browser | undefined
let baseUrl: string

before(async () => {
  server = await createServer({ server: { host: '127.0.0.1', port: 0 }, clearScreen: false })
  await server.listen()
  const address = server.httpServer?.address()
  assert.ok(address && typeof address === 'object')
  baseUrl = `http://127.0.0.1:${address.port}`
  browser = await chromium.launch()
})

after(async () => {
  await browser?.close()
  await server?.close()
})

async function openPage(context: TestContext) {
  assert.ok(browser)
  const page = await browser.newPage()
  page.setDefaultTimeout(6_000)
  context.after(() => page.context().close())

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const method = route.request().method()
    switch (`${method} ${url.pathname}${url.search}`) {
      case 'GET /api/v1/machines':
        await route.fulfill({
          json: { items: [machine, otherMachine].map(({ chassis, lastAuthenticatedContactAt }) => (
            { chassis, lastAuthenticatedContactAt }
          )) },
        })
        break
      case `GET ${machinePath}`:
        await route.fulfill({ json: machine })
        break
      case `GET /api/v1/machines/${otherMachine.chassis}`:
        await route.fulfill({ json: otherMachine })
        break
      case `GET /api/v1/machines/${otherMachine.chassis}/measurements/latest`:
        await route.fulfill({ json: partialExample.value })
        break
      case 'GET /api/v1/auth/csrf?purpose=logout':
        await route.fulfill({ json: { token: 'logout-test-token' } })
        break
      case 'DELETE /api/v1/auth/session':
        await route.fulfill({ status: 204 })
        break
      default:
        await route.fulfill({ status: 404 })
    }
  })
  return page
}

function openDetail(page: Page) {
  return page.goto(`${baseUrl}/#/maquinas/${machine.chassis}`)
}

async function assertDetailHidden(page: Page) {
  assert.equal(await page.getByRole('heading', { name: /Chasis 000123|Últimas lecturas/ }).count(), 0)
  assert.equal(await page.getByText('Último contacto con la máquina:', { exact: false }).count(), 0)
  assert.equal(await page.getByText('RPM del motor', { exact: true }).count(), 0)
}

async function holdLatest(page: Page) {
  const pending = Promise.withResolvers<{ status: number } | { json: object }>()
  const requested = Promise.withResolvers<void>()
  let response = pending.promise
  await page.route((url) => url.pathname === latestPath, async (route) => {
    requested.resolve()
    await route.fulfill(await response)
  })
  await openDetail(page)
  await page.getByText('Cargando últimas lecturas…').waitFor()
  await page.getByRole('heading', { name: 'Chasis 000123' }).waitFor()
  await requested.promise
  return {
    finish: pending.resolve,
    prepareRetry: () => { response = Promise.resolve({ json: latestExample.value }) },
  }
}

for (const [status, message] of [
  [401, 'La sesión venció. Vuelve a ingresar.'],
  [403, 'Acceso denegado'],
] as const) {
  test(`latest ${status} removes authorized detail and contact`, { timeout: 15_000 }, async (context) => {
    const page = await openPage(context)
    const pending = await holdLatest(page)
    pending.finish({ status })

    await page.getByText(message, { exact: true }).waitFor()
    await assertDetailHidden(page)
  })
}

test('latest 500 keeps the machine detail and retries only the readings', { timeout: 15_000 }, async (context) => {
  const page = await openPage(context)
  let detailRequests = 0
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === machinePath) detailRequests++
  })
  const pending = await holdLatest(page)
  pending.finish({ status: 500 })

  await page.getByRole('alert').getByText('No se pudo completar la consulta').waitFor()
  await page.getByRole('heading', { name: 'Chasis 000123' }).waitFor()
  await page.getByText('Último contacto con la máquina:', { exact: false }).waitFor()
  await page.getByText('RPM del motor', { exact: true }).waitFor()
  const detailRequestsBeforeRetry = detailRequests
  assert.ok(detailRequestsBeforeRetry > 0)
  pending.prepareRetry()
  await page.getByRole('region', { name: 'Últimas lecturas' }).getByRole('button', { name: 'Reintentar' }).click()

  await page.getByRole('button', { name: 'Ver detalle de RPM del motor' }).waitFor()
  assert.equal(await page.getByRole('alert').count(), 0)
  assert.equal(detailRequests, detailRequestsBeforeRetry)
})

async function holdLatestWithoutNetworkCancellation(page: Page) {
  // Deliver a late response even when fetch would normally be cancelled by the app.
  await page.addInitScript((path) => {
    const fetchWithAbort = window.fetch.bind(window)
    window.fetch = (input, init) => fetchWithAbort(input, input === path ? { ...init, signal: null } : init)
  }, latestPath)
  return holdLatest(page)
}

async function deliverLateReadings(page: Page, pending: Awaited<ReturnType<typeof holdLatest>>) {
  const arriving = page.waitForResponse((response) => new URL(response.url()).pathname === latestPath)
  pending.finish({ json: latestExample.value })
  const response = await arriving
  assert.equal(await response.finished(), null)
  await page.evaluate(() => new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  }))
}

test('late latest readings cannot overwrite a different machine', { timeout: 15_000 }, async (context) => {
  const page = await openPage(context)
  const pending = await holdLatestWithoutNetworkCancellation(page)

  await page.getByRole('link', { name: 'Máquinas' }).first().click()
  await page.getByRole('link', { name: `Ver máquina ${otherMachine.chassis}` }).click()
  await page.getByRole('heading', { name: `Chasis ${otherMachine.chassis}` }).waitFor()
  await page.getByRole('button', { name: 'Ver detalle de Temperatura del refrigerante' }).waitFor()
  await deliverLateReadings(page, pending)

  await page.getByRole('heading', { name: `Chasis ${otherMachine.chassis}` }).waitFor()
  await page.getByText('Sin mediciones disponibles.', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Ver detalle de RPM del motor' }).count(), 0)
})

test('late latest readings remain hidden after sign-out', { timeout: 15_000 }, async (context) => {
  const page = await openPage(context)
  const pending = await holdLatestWithoutNetworkCancellation(page)

  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  await page.getByText('La sesión se cerró.', { exact: true }).waitFor()
  await deliverLateReadings(page, pending)

  await assertDetailHidden(page)
  await page.getByRole('heading', { name: 'Ingresar' }).waitFor()
})
