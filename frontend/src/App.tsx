import { Suspense, lazy } from 'react'
import { HashRouter } from 'react-router'

import { AppLayout } from '@/components/app-layout'
import { LoadingStatus } from '@/components/query-feedback'
import { AccessView } from '@/views/access-view'

const ScenarioApp = import.meta.env.MODE === 'escenarios'
  ? lazy(() => import('@/scenarios/scenario-app'))
  : null

export default function App() {
  return (
    <HashRouter>
      {ScenarioApp ? (
        <Suspense fallback={<AppLayout><LoadingStatus>Cargando…</LoadingStatus></AppLayout>}>
          <ScenarioApp />
        </Suspense>
      ) : (
        <AppLayout><AccessView /></AppLayout>
      )}
    </HashRouter>
  )
}
