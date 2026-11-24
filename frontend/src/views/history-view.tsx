import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router'

import { MachineBreadcrumb } from '@/components/app-layout'
import { HistoryTable } from '@/components/history-table'
import { MeasurementChart } from '@/components/measurement-chart'
import { EmptyStatus, LoadingStatus, QueryError } from '@/components/query-feedback'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { parseDateInput, toChartSamples } from '@/readings'
import { useQuery } from '@/use-query'
import { loadHistory, type HistoryQuery, type Machine, type WebDataSource } from '@/web-data'

export type HistorySelection = {
  draft: { from: string; to: string; definitionId: string }
  applied: HistoryQuery
  page: number
}

export default function HistoryView({
  machine,
  source,
  selection,
  onSelectionChange,
  onExpire,
}: {
  machine: Machine
  source: WebDataSource
  selection: HistorySelection
  onSelectionChange: (selection: HistorySelection) => void
  onExpire: () => void
}) {
  const [fieldError, setFieldError] = useState<{ field: 'from' | 'to'; message: string }>()
  const { applied, draft, page } = selection
  const { chassis, definitions } = machine
  const definition = definitions.find(({ definitionId }) => definitionId === applied.definitionId)!
  const load = useCallback((signal: AbortSignal) => (
    loadHistory(source.history, chassis, applied, signal)
  ), [source, chassis, applied])
  const { state, retry } = useQuery(load)

  function updateDraft(field: keyof HistorySelection['draft'], value: string) {
    setFieldError(undefined)
    onSelectionChange({ ...selection, draft: { ...draft, [field]: value } })
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const from = parseDateInput(draft.from)
    const to = parseDateInput(draft.to)
    if (from === null) {
      setFieldError({ field: 'from', message: 'Ingresa una fecha y hora de inicio válidas.' })
      return
    }
    if (to === null || to <= from) {
      setFieldError({
        field: 'to',
        message: to === null ? 'Ingresa una fecha y hora de fin válidas.' : 'El fin debe ser posterior al inicio.',
      })
      return
    }

    setFieldError(undefined)
    onSelectionChange({ ...selection, applied: { from, to, definitionId: draft.definitionId }, page: 1 })
  }

  return (
    <div className="grid gap-6 md:gap-8">
      <MachineBreadcrumb chassis={chassis} history />
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="grid gap-1.5">
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">Histórico</h1>
          <p className="text-muted-foreground md:text-xl">Chasis {chassis} · Embolsadora de forrajes</p>
        </div>
        <Button variant="link" className="min-h-11 px-0" asChild>
          <Link to={`/maquinas/${encodeURIComponent(chassis)}`}>Volver al resumen</Link>
        </Button>
      </div>
      <form onSubmit={submit} noValidate>
        <FieldGroup className="grid gap-4 lg:grid-cols-[1fr_1fr_1fr_auto]">
          {(['from', 'to'] as const).map((field) => {
            const error = fieldError?.field === field ? fieldError.message : undefined
            return (
              <Field key={field} data-invalid={Boolean(error)} className="min-w-0">
                <FieldLabel htmlFor={field}>{field === 'from' ? 'Desde' : 'Hasta'}</FieldLabel>
                <Input
                  id={field}
                  name={field}
                  type="datetime-local"
                  required
                  value={draft[field]}
                  onChange={(event) => updateDraft(field, event.currentTarget.value)}
                  aria-invalid={Boolean(error)}
                  aria-describedby={error ? `${field}-error` : undefined}
                  className="min-h-11"
                />
                {error && <FieldError id={`${field}-error`}>{error}</FieldError>}
              </Field>
            )
          })}
          <Field className="min-w-0">
            <FieldLabel id="history-variable-label" htmlFor="history-variable">Variable</FieldLabel>
            <Select value={draft.definitionId} onValueChange={(value) => updateDraft('definitionId', value)}>
              <SelectTrigger
                id="history-variable"
                aria-labelledby="history-variable-label"
                className="min-h-11 w-full"
              >
                <SelectValue />
              </SelectTrigger>
              <SelectContent position="popper" align="start">
                <SelectGroup>
                  {definitions.map(({ definitionId, name }) => (
                    <SelectItem key={definitionId} className="min-h-11" value={definitionId}>{name}</SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          <Button type="submit" className="min-h-11 min-w-35.5 justify-self-start px-6 lg:mt-[1.625rem]">Consultar</Button>
        </FieldGroup>
      </form>
      <section aria-label="Resultado histórico" className="grid gap-7" aria-busy={state.status === 'loading'}>
        {state.status === 'loading' && <LoadingStatus>Cargando histórico…</LoadingStatus>}
        {state.status === 'error' && <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />}
        {state.status === 'success' && state.data.total === 0 && (
          <EmptyStatus>No se encontraron mediciones para este período y variable.</EmptyStatus>
        )}
        {state.status === 'success' && state.data.total > 0 && (
          <>
            <MeasurementChart
              chassis={chassis}
              name={definition.name}
              unit={definition.unit}
              samples={toChartSamples(state.data.items, source.knownGaps(definition.definitionId))}
              from={applied.from}
              to={applied.to}
              asOf={Date.parse(state.data.asOf)}
            />
            <HistoryTable
              definition={definition}
              chassis={chassis}
              results={state.data}
              page={page}
              onPageChange={(next) => onSelectionChange({ ...selection, page: next })}
            />
          </>
        )}
      </section>
    </div>
  )
}
