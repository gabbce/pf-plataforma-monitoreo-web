import accessRequest from '../../../docs/contratos/web/ejemplos/login-request.json'
import coolantHistory from '../../../docs/contratos/web/ejemplos/history-coolant.json'
import rpmFirstPage from '../../../docs/contratos/web/ejemplos/history-rpm-page-1.json'
import rpmSecondPage from '../../../docs/contratos/web/ejemplos/history-rpm-page-2.json'
import lateHistory from '../../../docs/contratos/web/ejemplos/history-late-record.json'
import latestAfterLate from '../../../docs/contratos/web/ejemplos/latest-after-late-record.json'
import latestInvalid from '../../../docs/contratos/web/ejemplos/latest-invalid.json'
import latestNormal from '../../../docs/contratos/web/ejemplos/latest-normal.json'
import latestPartial from '../../../docs/contratos/web/ejemplos/latest-partial.json'
import latestStale from '../../../docs/contratos/web/ejemplos/latest-stale.json'
import machineDetail from '../../../docs/contratos/web/ejemplos/machine-detail.json'
import machinesAuthorized from '../../../docs/contratos/web/ejemplos/machines-authorized.json'
import machinesEmpty from '../../../docs/contratos/web/ejemplos/machines-empty.json'

import { compareReadings, type Measurement } from '@/readings'
import { WebError, type WebDataSource } from '@/web-data'

const gap = coolantHistory.value.items[5]
const pageSize = rpmFirstPage.value.size
const baseHistory = [
  ...rpmFirstPage.value.items,
  ...rpmSecondPage.value.items,
  ...coolantHistory.value.items,
]
const latestByScenario = new Map([
  ['quality', latestInvalid.value],
  ['partial', latestPartial.value],
  ['stale', latestStale.value],
  ['late', latestAfterLate.value],
])

function simulateDelay(ms: number, signal: AbortSignal) {
  signal.throwIfAborted()
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, ms)

    function abort() {
      window.clearTimeout(timer)
      reject(signal.reason)
    }

    signal.addEventListener('abort', abort, { once: true })
  })
}

function scenarioHistory(scenario: string | null) {
  switch (scenario) {
    case 'quality':
      return [...baseHistory, latestInvalid.value.items[0].measurement]
    case 'partial':
      return coolantHistory.value.items
    case 'late': {
      const byRecordId = new Map(
        [...rpmFirstPage.value.items, ...rpmSecondPage.value.items, ...lateHistory.value.items]
          .map((record) => [record.recordId, record]),
      )
      return [...byRecordId.values()]
    }
    case 'gap':
      return baseHistory.filter(({ recordId }) => recordId !== gap.recordId)
    case 'dense': {
      // The fixture concentrates the RPM samples inside a single minute.
      const end = Date.parse(latestNormal.value.items[0].measurement.occurredAt)
      return baseHistory.map((record, index) => {
        if (record.definitionId !== 'engine.rpm') return record
        const occurredAt = new Date(end - (9 - index) * 1000).toISOString()
        const receivedAt = new Date(Date.parse(occurredAt) + 5000).toISOString()
        return { ...record, occurredAt, receivedAt }
      })
    }
    default:
      return baseHistory
  }
}

export function createDemoDataSource(scenario: string | null): WebDataSource {
  const latest = latestByScenario.get(scenario ?? '') ?? latestNormal.value
  const history: Measurement[] = scenarioHistory(scenario)
  const delay = (signal: AbortSignal) => simulateDelay(scenario === 'loading' ? 850 : 100, signal)
  let expiresOnMachine = scenario === 'expired'
  let failsOnHistory = scenario === 'error'

  function checkMachine(chassis: string) {
    if (chassis !== machineDetail.value.chassis || scenario === 'denied' || scenario === 'empty-machines') {
      throw new WebError(403)
    }
  }

  return {
    async signIn(identifier, password, signal) {
      await delay(signal)
      if (identifier !== accessRequest.value.identifier || password !== accessRequest.value.password) {
        throw new WebError(401)
      }
    },

    async machines(signal) {
      await delay(signal)
      return (scenario === 'empty-machines' ? machinesEmpty : machinesAuthorized).value.items
    },

    async machine(chassis, signal) {
      await delay(signal)
      if (expiresOnMachine) {
        expiresOnMachine = false
        throw new WebError(401)
      }
      checkMachine(chassis)
      return machineDetail.value
    },

    async latest(chassis, signal) {
      await delay(signal)
      checkMachine(chassis)
      return latest
    },

    async history(chassis, query, page, asOf, signal) {
      await delay(signal)
      checkMachine(chassis)
      if (failsOnHistory && asOf === undefined) {
        failsOnHistory = false
        throw new WebError(500)
      }
      const cutoff = asOf ?? latest.asOf
      const asOfTime = Date.parse(cutoff)
      const items = history.filter((reading) => {
        const occurred = Date.parse(reading.occurredAt)
        return reading.definitionId === query.definitionId &&
          occurred >= query.from && occurred < query.to &&
          Date.parse(reading.receivedAt) <= asOfTime
      }).sort(compareReadings)
      return {
        asOf: cutoff,
        page,
        size: pageSize,
        total: items.length,
        items: items.slice((page - 1) * pageSize, page * pageSize),
      }
    },

    knownGaps(definitionId) {
      return scenario === 'gap' && definitionId === gap.definitionId ? [Date.parse(gap.occurredAt)] : []
    },
  }
}
