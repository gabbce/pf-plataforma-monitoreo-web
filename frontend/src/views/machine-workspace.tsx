import { Suspense, lazy, useCallback, useMemo, useState } from 'react'
import { Navigate, Route, Routes, useMatch, useParams } from 'react-router'

import type { PeriodMinutes } from '@/components/period-selector'
import { LoadingStatus, QueryError } from '@/components/query-feedback'
import { formatDateInput } from '@/readings'
import { useQuery } from '@/use-query'
import { recentInterval, type LatestReadings, type Machine, type WebDataSource } from '@/web-data'
import type { HistorySelection } from '@/views/history-view'

const MachineView = lazy(() => import('@/views/machine-view'))
const HistoryView = lazy(() => import('@/views/history-view'))

export function MachineWorkspace({ source, onExpire }: { source: WebDataSource; onExpire: () => void }) {
  const { chassis = '' } = useParams()
  const load = useCallback(async (signal: AbortSignal) => {
    const machine = await source.machine(chassis, signal)
    const latest = await source.latest(chassis, signal)
    return { machine, latest }
  }, [chassis, source])
  const { state, retry } = useQuery(load)

  if (state.status === 'loading') return <LoadingStatus>Cargando máquina…</LoadingStatus>
  if (state.status === 'error') return <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />

  return <MachineRoutes key={chassis} {...state.data} source={source} onExpire={onExpire} />
}

function MachineRoutes({
  machine,
  latest,
  source,
  onExpire,
}: {
  machine: Machine
  latest: LatestReadings
  source: WebDataSource
  onExpire: () => void
}) {
  const [period, setPeriod] = useState<PeriodMinutes>(10)
  const [selection, setSelection] = useState<HistorySelection>()
  const showingHistory = Boolean(useMatch('/maquinas/:chassis/historico'))
  const initial = useMemo<HistorySelection>(() => {
    const applied = { ...recentInterval(latest.asOf, period), definitionId: 'engine.rpm' }
    return {
      draft: {
        from: formatDateInput(applied.from),
        to: formatDateInput(applied.to),
        definitionId: applied.definitionId,
      },
      applied,
      page: 1,
    }
  }, [latest.asOf, period])

  if (showingHistory && selection === undefined) {
    setSelection(initial)
  }

  return (
    <Suspense fallback={<LoadingStatus>Cargando vista…</LoadingStatus>}>
      <Routes>
        <Route index element={(
          <MachineView
            machine={machine}
            latest={latest}
            source={source}
            period={period}
            onPeriodChange={setPeriod}
            onExpire={onExpire}
          />
        )} />
        <Route path="historico" element={(
          <HistoryView
            machine={machine}
            source={source}
            selection={selection ?? initial}
            onSelectionChange={setSelection}
            onExpire={onExpire}
          />
        )} />
        <Route path="*" element={<Navigate to="/maquinas" replace />} />
      </Routes>
    </Suspense>
  )
}
