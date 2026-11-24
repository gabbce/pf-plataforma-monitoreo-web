import { useCallback, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router'

import { AppLayout } from '@/components/app-layout'
import { AccessView } from '@/views/access-view'
import { MachinesView } from '@/views/machines-view'
import { MachineWorkspace } from '@/views/machine-workspace'
import { createDemoDataSource } from '@/scenarios/demo-data'

export default function ScenarioApp() {
  const [source] = useState(() => createDemoDataSource(
    new URLSearchParams(window.location.search).get('scenario'),
  ))
  const [session, setSession] = useState<'signed-out' | 'active' | 'expired'>('signed-out')
  const active = session === 'active'
  const expire = useCallback(() => setSession('expired'), [])

  return (
    <AppLayout onSignOut={active ? () => setSession('signed-out') : undefined}>
      <Routes>
        <Route index element={active ? <Navigate to="/maquinas" replace /> : (
          <AccessView signIn={source.signIn} onSuccess={() => setSession('active')} expired={session === 'expired'} />
        )} />
        <Route path="maquinas" element={active ? <MachinesView source={source} onExpire={expire} /> : (
          <Navigate to="/" replace />
        )} />
        <Route path="maquinas/:chassis/*" element={active ? <MachineWorkspace source={source} onExpire={expire} /> : (
          <Navigate to="/" replace />
        )} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppLayout>
  )
}
