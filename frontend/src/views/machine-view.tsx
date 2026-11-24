import { useCallback, useSyncExternalStore } from 'react'
import { Link } from 'react-router'

import { MachineBreadcrumb } from '@/components/app-layout'
import { MeasurementChart } from '@/components/measurement-chart'
import { PeriodSelector, type PeriodMinutes } from '@/components/period-selector'
import { LoadingStatus, QueryError } from '@/components/query-feedback'
import { ReadingPanel } from '@/components/reading-panel'
import { Button } from '@/components/ui/button'
import {
  formatMeasurementTime,
  formatReadingValue,
  freshnessLabel,
  qualityLabel,
  toChartSamples,
} from '@/readings'
import { useQuery } from '@/use-query'
import {
  loadHistory,
  recentInterval,
  type LatestReadings,
  type Machine,
  type MeasurementDefinition,
  type WebDataSource,
} from '@/web-data'

const wideLayout = '(min-width: 48rem)'

function subscribeLayout(onChange: () => void) {
  const query = window.matchMedia(wideLayout)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

function isWideLayout() {
  return window.matchMedia(wideLayout).matches
}

function ReadingSummary({
  definition,
  reading,
  chassis,
  asOf,
}: {
  definition: MeasurementDefinition
  reading: LatestReadings['items'][number] | undefined
  chassis: string
  asOf: number
}) {
  const { name, unit } = definition

  if (!reading) {
    return (
      <div className="grid gap-2">
        <h2 className="text-xl font-semibold">{name}</h2>
        <p className="text-sm text-muted-foreground">Sin mediciones disponibles.</p>
      </div>
    )
  }

  const { measurement, freshness } = reading
  const freshnessText = freshnessLabel(freshness)
  return (
    <div className="grid gap-2">
      <h2 className="text-xl font-semibold md:text-2xl">{name}</h2>
      <div className="flex flex-wrap items-baseline gap-2 tabular-nums">
        <span className="text-4xl font-bold tracking-tight md:text-5xl">{formatReadingValue(measurement)}</span>
        <span className="text-xl font-semibold">{unit}</span>
      </div>
      <p className="text-sm text-muted-foreground">
        Medición:{' '}
        <time dateTime={measurement.occurredAt}>{formatMeasurementTime(measurement.occurredAt)}</time>
      </p>
      {measurement.quality !== 'VALID' && <p className="text-sm font-medium">{qualityLabel(measurement.quality)}</p>}
      {freshnessText && <p className="text-sm font-medium">{freshnessText}</p>}
      <div><ReadingPanel chassis={chassis} name={name} reading={measurement} asOf={asOf} /></div>
    </div>
  )
}

function RecentTrend({
  source,
  chassis,
  definition,
  period,
  asOf,
  showTitle,
  onExpire,
}: {
  source: WebDataSource
  chassis: string
  definition: MeasurementDefinition
  period: PeriodMinutes
  asOf: string
  showTitle: boolean
  onExpire: () => void
}) {
  const { from, to } = recentInterval(asOf, period)
  const { definitionId, name, unit } = definition
  const load = useCallback((signal: AbortSignal) => (
    loadHistory(source.history, chassis, { definitionId, from, to }, signal, asOf)
  ), [source, chassis, definitionId, from, to, asOf])
  const { state, retry } = useQuery(load)

  if (state.status === 'loading') return <LoadingStatus>Cargando tendencia…</LoadingStatus>
  if (state.status === 'error') return <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />

  return (
    <MeasurementChart
      chassis={chassis}
      name={name}
      unit={unit}
      samples={toChartSamples(state.data.items, source.knownGaps(definitionId))}
      from={from}
      to={to}
      asOf={Date.parse(state.data.asOf)}
      showTitle={showTitle}
    />
  )
}

export default function MachineView({
  machine,
  latest,
  period,
  onPeriodChange,
  source,
  onExpire,
}: {
  machine: Machine
  latest: LatestReadings
  period: PeriodMinutes
  onPeriodChange: (period: PeriodMinutes) => void
  source: WebDataSource
  onExpire: () => void
}) {
  const wide = useSyncExternalStore(subscribeLayout, isWideLayout)
  const { chassis, lastAuthenticatedContactAt, definitions } = machine
  const asOf = Date.parse(latest.asOf)

  function summary(definition: MeasurementDefinition) {
    return (
      <ReadingSummary
        key={definition.definitionId}
        definition={definition}
        reading={latest.items.find(({ measurement }) => measurement.definitionId === definition.definitionId)}
        chassis={chassis}
        asOf={asOf}
      />
    )
  }

  return (
    <div className="grid gap-6 md:gap-8">
      <MachineBreadcrumb chassis={chassis} />
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="grid gap-1.5">
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">Chasis {chassis}</h1>
          <p className="text-muted-foreground md:text-xl">Embolsadora de forrajes</p>
          <p className="text-sm text-muted-foreground">
            Último contacto con la máquina:{' '}
            {lastAuthenticatedContactAt ? (
              <time dateTime={lastAuthenticatedContactAt}>{formatMeasurementTime(lastAuthenticatedContactAt)}</time>
            ) : 'Sin contacto registrado'}
          </p>
        </div>
        <Button className="min-h-11 min-w-42.5 px-6" asChild>
          <Link to={`/maquinas/${encodeURIComponent(chassis)}/historico`}>Ver historial</Link>
        </Button>
      </div>
      {wide && <div className="grid grid-cols-2 gap-x-12">{definitions.map(summary)}</div>}
      <section aria-labelledby="trends-heading" className="grid gap-6">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
          <h2 id="trends-heading" className="text-xl font-semibold md:text-2xl">Tendencias recientes</h2>
          <PeriodSelector value={period} onChange={onPeriodChange} />
        </div>
        <div className="grid gap-x-12 gap-y-9 md:grid-cols-2">
          {definitions.map((definition) => (
            <div key={definition.definitionId} className="grid min-w-0 content-start gap-4">
              {!wide && summary(definition)}
              <RecentTrend
                source={source}
                chassis={chassis}
                definition={definition}
                period={period}
                asOf={latest.asOf}
                showTitle={wide}
                onExpire={onExpire}
              />
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
