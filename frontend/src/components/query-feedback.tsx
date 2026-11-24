import { useEffect } from 'react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { WebError } from '@/web-data'

export function LoadingStatus({ children }: { children: string }) {
  return (
    <p role="status" className="flex items-center gap-3 py-4 text-sm text-muted-foreground">
      <span
        aria-hidden="true"
        className="size-4 rounded-full border-2 border-current border-r-transparent motion-safe:animate-spin"
      />
      {children}
    </p>
  )
}

export function EmptyStatus({ children }: { children: string }) {
  return <p role="status" className="py-4 text-sm text-muted-foreground">{children}</p>
}

function ExpiredSession({ onExpire }: { onExpire: () => void }) {
  useEffect(onExpire, [onExpire])
  return <LoadingStatus>Volviendo al acceso…</LoadingStatus>
}

export function QueryError({
  error,
  onRetry,
  onExpire,
}: {
  error: WebError
  onRetry: () => void
  onExpire: () => void
}) {
  if (error.status === 401) return <ExpiredSession onExpire={onExpire} />

  if (error.status === 403) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Acceso denegado</AlertTitle>
        <AlertDescription>No tienes acceso a la información solicitada.</AlertDescription>
      </Alert>
    )
  }

  return (
    <Alert variant="destructive">
      <AlertTitle>No se pudo completar la consulta</AlertTitle>
      <AlertDescription>
        <Button variant="outline" className="mt-1 min-h-11" onClick={onRetry}>Reintentar</Button>
      </AlertDescription>
    </Alert>
  )
}
