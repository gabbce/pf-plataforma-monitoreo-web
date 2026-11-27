import { useCallback } from 'react'

import { MachineBreadcrumb } from '@/components/app-layout'
import { EmptyStatus, LoadingStatus, QueryError } from '@/components/query-feedback'
import { ReadingSummary } from '@/components/reading-summary'
import { managementData } from '@/management-data'
import { formatMeasurementTime } from '@/readings'
import { useQuery } from '@/use-query'
import type { Machine } from '@/web-data'

export function MachineCatalogView({ machine, onExpire }: { machine: Machine; onExpire: () => void }) {
  const { chassis, lastAuthenticatedContactAt, definitions } = machine
  const load = useCallback((signal: AbortSignal) => managementData.latest(chassis, signal), [chassis])
  const { state, retry } = useQuery(load)

  if (state.status === 'error' && state.error.status !== 500) {
    return <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />
  }

  let readings
  if (state.status === 'loading') {
    readings = <LoadingStatus>Cargando últimas lecturas…</LoadingStatus>
  } else if (state.status === 'error') {
    readings = <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />
  } else if (state.data.items.length === 0) {
    readings = <EmptyStatus>No hay mediciones disponibles para esta máquina.</EmptyStatus>
  } else {
    const { asOf, items } = state.data
    const cutoff = Date.parse(asOf)
    readings = (
      <div className="grid gap-x-12 gap-y-6 md:grid-cols-2">
        {definitions.map((definition) => (
          <ReadingSummary
            key={definition.definitionId}
            definition={definition}
            reading={items.find(({ measurement }) => measurement.definitionId === definition.definitionId)}
            chassis={chassis}
            asOf={cutoff}
            heading="h3"
            showAllStatuses
          />
        ))}
      </div>
    )
  }

  return (
    <div className="grid gap-6 md:gap-8">
      <MachineBreadcrumb chassis={chassis} />
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
      <section aria-labelledby="latest-readings-heading" className="grid gap-3">
        <h2 id="latest-readings-heading" className="text-xl font-semibold md:text-2xl">Últimas lecturas</h2>
        {readings}
        {(state.status !== 'success' || state.data.items.length === 0) && definitions.length > 0 && (
          <ul className="grid divide-y">
            {definitions.map(({ definitionId, name, unit }) => (
              <li key={definitionId} className="flex flex-wrap items-baseline justify-between gap-2 py-3">
                <span className="font-medium">{name}</span>
                <span className="text-sm text-muted-foreground">{unit}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
