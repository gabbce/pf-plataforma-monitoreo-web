import { useState } from 'react'

import logo from '../../brand/canavesio-logo.svg'
import { MeasurementChart } from '@/components/measurement-chart'
import { PeriodSelector, type PeriodMinutes } from '@/components/period-selector'
import { ReadingPanel } from '@/components/reading-panel'
import { getDemoReadings } from '@/demo-readings'
import { formatMeasurementTime, formatReadingValue, qualityLabel } from '@/readings'

const scenario = new URLSearchParams(window.location.search).get('scenario')
const { machine, series, asOf } = getDemoReadings(scenario)

export default function App() {
  const [period, setPeriod] = useState<PeriodMinutes>(10)
  const from = asOf - period * 60_000

  return (
    <>
      <header className="border-b-2 border-brand">
        <div className="mx-auto flex min-h-20 max-w-[1440px] flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 md:px-8 lg:px-11">
          <img src={logo} alt="Canavesio" className="h-auto w-40 md:w-55" />
          <p className="font-semibold md:text-xl">Plataforma de monitoreo</p>
        </div>
      </header>
      <main className="mx-auto grid max-w-[1440px] gap-8 px-4 py-8 md:px-8 lg:px-11 lg:py-11">
        <div className="grid gap-1.5">
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">Chasis {machine.chassis}</h1>
          <p className="text-muted-foreground md:text-lg">Embolsadora de forrajes</p>
          {machine.lastAuthenticatedContactAt && (
            <p className="text-sm text-muted-foreground">
              Último contacto con la máquina:{' '}
              <time dateTime={machine.lastAuthenticatedContactAt}>
                {formatMeasurementTime(machine.lastAuthenticatedContactAt)}
              </time>
            </p>
          )}
        </div>
        <section aria-labelledby="trends-heading" className="grid gap-6">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
            <h2 id="trends-heading" className="text-xl font-semibold md:text-2xl">Tendencias recientes</h2>
            <PeriodSelector value={period} onChange={setPeriod} />
          </div>
          <div className="grid gap-x-12 gap-y-9 md:grid-cols-2">
            {series.map(({ definition, current, samples }) => (
              <section key={definition.definitionId} className="grid min-w-0 content-start gap-4">
                <div className="min-w-0">
                  <h3 className="text-lg font-semibold md:text-xl">{definition.name}</h3>
                  {current && (
                    <>
                      <p className="mt-2 flex flex-wrap items-baseline gap-2 tabular-nums">
                        <span className="text-4xl font-bold tracking-tight md:text-5xl">
                          {formatReadingValue(current.measurement)}
                        </span>
                        <span className="text-xl font-semibold">{definition.unit}</span>
                        {current.measurement.quality !== 'VALID' && (
                          <span className="text-sm font-medium text-primary">{qualityLabel(current.measurement.quality)}</span>
                        )}
                      </p>
                      <p className="mt-3 text-sm text-muted-foreground">
                        Medición:{' '}
                        <time dateTime={current.measurement.occurredAt}>
                          {formatMeasurementTime(current.measurement.occurredAt)}
                        </time>
                      </p>
                      <ReadingPanel
                        chassis={machine.chassis}
                        name={definition.name}
                        reading={current.measurement}
                        asOf={asOf}
                      />
                    </>
                  )}
                </div>
                <MeasurementChart name={definition.name} unit={definition.unit} samples={samples} from={from} to={asOf} />
              </section>
            ))}
          </div>
        </section>
      </main>
    </>
  )
}
