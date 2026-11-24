import { Link } from 'react-router'

import { EmptyStatus, LoadingStatus, QueryError } from '@/components/query-feedback'
import { Button } from '@/components/ui/button'
import { formatMeasurementTime } from '@/readings'
import { useQuery } from '@/use-query'
import type { WebDataSource } from '@/web-data'

export function MachinesView({ source, onExpire }: { source: WebDataSource; onExpire: () => void }) {
  const { state, retry } = useQuery(source.machines)

  return (
    <div className="grid gap-6">
      <h1 className="text-3xl font-bold tracking-tight md:text-4xl">Máquinas</h1>
      <p className="text-muted-foreground">Selecciona una máquina para consultar sus mediciones.</p>
      {state.status === 'loading' && <LoadingStatus>Cargando máquinas…</LoadingStatus>}
      {state.status === 'error' && <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />}
      {state.status === 'success' && state.data.length === 0 && (
        <EmptyStatus>No tienes máquinas autorizadas.</EmptyStatus>
      )}
      {state.status === 'success' && state.data.length > 0 && (
        <ul className="grid gap-4">
          {state.data.map((machine) => (
            <li
              key={machine.chassis}
              className="flex flex-col items-start gap-4 rounded-md bg-muted p-5 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="grid gap-1">
                <h2 className="text-xl font-semibold">Chasis {machine.chassis}</h2>
                <p className="text-muted-foreground">Embolsadora de forrajes</p>
                <p className="text-sm text-muted-foreground">
                  Último contacto:{' '}
                  {machine.lastAuthenticatedContactAt ? (
                    <time dateTime={machine.lastAuthenticatedContactAt}>
                      {formatMeasurementTime(machine.lastAuthenticatedContactAt)}
                    </time>
                  ) : 'Sin contacto registrado'}
                </p>
              </div>
              <Button className="min-h-11 w-full sm:w-auto" asChild>
                <Link to={`/maquinas/${encodeURIComponent(machine.chassis)}`}>
                  Ver máquina<span className="sr-only"> {machine.chassis}</span>
                </Link>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
