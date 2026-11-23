import coolantHistory from '../../docs/contratos/web/ejemplos/history-coolant.json'
import rpmFirstPage from '../../docs/contratos/web/ejemplos/history-rpm-page-1.json'
import rpmSecondPage from '../../docs/contratos/web/ejemplos/history-rpm-page-2.json'
import latestInvalid from '../../docs/contratos/web/ejemplos/latest-invalid.json'
import latestNormal from '../../docs/contratos/web/ejemplos/latest-normal.json'
import machineDetail from '../../docs/contratos/web/ejemplos/machine-detail.json'

import type { ChartSample } from './readings'

export function getDemoReadings(scenario: string | null) {
  const machine = machineDetail.value
  const latest = scenario === 'quality' ? latestInvalid.value : latestNormal.value
  const history = [
    ...rpmFirstPage.value.items,
    ...rpmSecondPage.value.items,
    ...coolantHistory.value.items,
  ]

  return {
    machine,
    asOf: Date.parse(latest.asOf),
    series: machine.definitions.map((definition) => {
      const current = latest.items.find((item) => item.measurement.definitionId === definition.definitionId)
      const records = history.filter((record) => record.definitionId === definition.definitionId)
      const samples: ChartSample[] = records.map((record, index) => ({
        timestamp: Date.parse(record.occurredAt),
        reading: scenario === 'gap' && definition.definitionId === 'engine.coolant_temperature' && index === 5
          ? null
          : record,
      }))

      if (current?.measurement.quality === 'INVALID') {
        samples.push({
          timestamp: Date.parse(current.measurement.occurredAt),
          reading: current.measurement,
        })
      }

      return { definition, current, samples }
    }),
  }
}
