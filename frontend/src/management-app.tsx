import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Navigate, useMatch, useNavigate } from 'react-router'

import { AppLayout } from '@/components/app-layout'
import { LoadingStatus, QueryError } from '@/components/query-feedback'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { managementData, signOut } from '@/management-data'
import { useQuery } from '@/use-query'
import { AccessView } from '@/views/access-view'
import { MachineCatalogView } from '@/views/machine-catalog-view'
import { MachineList } from '@/views/machines-view'
import type { Machine, MachineSummary } from '@/web-data'

type Session = 'checking' | 'signed-out' | 'expired' | 'closed' | 'active'
type Catalog = { machines: MachineSummary[] } | { machine: Machine }

export function ManagementApp() {
  const navigate = useNavigate()
  const atAccess = Boolean(useMatch('/'))
  const atList = Boolean(useMatch('/maquinas'))
  const detail = useMatch('/maquinas/:chassis')
  const history = useMatch('/maquinas/:chassis/historico')
  const chassis = detail?.params.chassis ?? history?.params.chassis
  const knownRoute = atAccess || atList || chassis !== undefined
  const [session, setSession] = useState<Session>('checking')
  const [signingOut, setSigningOut] = useState(false)
  const [signOutFailed, setSignOutFailed] = useState(false)
  const signOutController = useRef<AbortController>(null)

  const canQuery = knownRoute && (session === 'checking' || session === 'active')
  const requestedChassis = canQuery ? chassis ?? null : undefined
  const load = useCallback(async (signal: AbortSignal): Promise<Catalog> => (
    requestedChassis == null
      ? { machines: await managementData.machines(signal) }
      : { machine: await managementData.machine(requestedChassis, signal) }
  ), [requestedChassis])
  const { state, retry } = useQuery(canQuery ? load : undefined)
  const denied = state.status === 'error' && state.error.status === 403
  const sessionConfirmed = state.status === 'success' || denied

  useEffect(() => () => signOutController.current?.abort(), [])

  const onExpire = useCallback(() => {
    setSession((previous) => {
      if (previous === 'checking') return 'signed-out'
      if (previous === 'active') return 'expired'
      return previous
    })
    setSignOutFailed(false)
  }, [])

  if (session === 'checking' && sessionConfirmed) setSession('active')

  function onSignIn() {
    setSession('active')
    void navigate('/maquinas', { replace: true })
  }

  async function onSignOut() {
    if (signOutController.current) return

    const controller = new AbortController()
    signOutController.current = controller
    setSignOutFailed(false)
    setSigningOut(true)

    try {
      await signOut(controller.signal)
      if (!controller.signal.aborted) setSession('closed')
    } catch {
      if (!controller.signal.aborted) setSignOutFailed(true)
    } finally {
      if (!controller.signal.aborted) setSigningOut(false)
      signOutController.current = null
    }
  }

  let content: ReactNode
  if (!knownRoute || (!canQuery && !atAccess)) {
    content = <Navigate to="/" replace />
  } else if (!canQuery) {
    content = (
      <AccessView
        signIn={managementData.signIn}
        onSuccess={onSignIn}
        expired={session === 'expired'}
        signedOut={session === 'closed'}
      />
    )
  } else if (signingOut) {
    content = <LoadingStatus>Cerrando sesión…</LoadingStatus>
  } else if (state.status === 'loading') {
    content = (
      <LoadingStatus>
        {atAccess ? 'Comprobando sesión…' : atList ? 'Cargando máquinas…' : 'Cargando máquina…'}
      </LoadingStatus>
    )
  } else if (atAccess && sessionConfirmed) {
    content = <Navigate to="/maquinas" replace />
  } else if (state.status === 'error') {
    content = <QueryError error={state.error} onRetry={retry} onExpire={onExpire} />
  } else if ('machines' in state.data) {
    content = (
      <MachineList
        state={{ status: 'success', data: state.data.machines }}
        onRetry={retry}
        onExpire={onExpire}
        description="Selecciona una máquina para consultar su ficha."
      />
    )
  } else if (history) {
    content = <Navigate to={`/maquinas/${encodeURIComponent(state.data.machine.chassis)}`} replace />
  } else {
    content = <MachineCatalogView machine={state.data.machine} />
  }

  return (
    <AppLayout onSignOut={session === 'active' ? onSignOut : undefined} signingOut={signingOut}>
      {signOutFailed && (
        <Alert variant="destructive" className="mb-6">
          <AlertTitle>No se pudo cerrar la sesión</AlertTitle>
          <AlertDescription>
            <Button variant="outline" className="mt-1 min-h-11" onClick={onSignOut}>Reintentar</Button>
          </AlertDescription>
        </Alert>
      )}
      {content}
    </AppLayout>
  )
}
