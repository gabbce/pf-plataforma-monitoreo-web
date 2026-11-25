import { MachineBreadcrumb } from '@/components/app-layout'
import { EmptyStatus } from '@/components/query-feedback'
import { formatMeasurementTime } from '@/readings'
import type { Machine } from '@/web-data'

export function MachineCatalogView({ machine }: { machine: Machine }) {
  const { chassis, lastAuthenticatedContactAt, definitions } = machine

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
      <section aria-labelledby="definitions-heading" className="grid gap-3">
        <h2 id="definitions-heading" className="text-xl font-semibold md:text-2xl">Variables del catálogo</h2>
        {definitions.length === 0 ? <EmptyStatus>No hay variables en el catálogo.</EmptyStatus> : (
          <ul className="grid divide-y">
            {definitions.map(({ definitionId, name, unit }) => (
              <li key={definitionId} className="flex flex-wrap items-baseline justify-between gap-2 py-3">
                <span className="font-medium">{name}</span>
                <span className="text-sm text-muted-foreground">{unit}</span>
              </li>
            ))}
          </ul>
        )}
        <p className="text-sm text-muted-foreground">La consulta de mediciones todavía no está disponible.</p>
      </section>
    </div>
  )
}
