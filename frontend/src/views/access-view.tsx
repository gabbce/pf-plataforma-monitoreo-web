import { useEffect, useRef, useState, type FormEvent } from 'react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { WebError, type CatalogDataSource } from '@/web-data'

export function AccessView({
  signIn,
  onSuccess,
  expired = false,
  signedOut = false,
}: {
  signIn: CatalogDataSource['signIn']
  onSuccess: () => void
  expired?: boolean
  signedOut?: boolean
}) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()
  const controller = useRef<AbortController>(null)

  useEffect(() => () => controller.current?.abort(), [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (controller.current) return

    const form = event.currentTarget
    const data = new FormData(form)
    const password = form.elements.namedItem('password')
    const attempt = new AbortController()
    controller.current = attempt
    setPending(true)
    setError(undefined)

    try {
      await signIn(String(data.get('identifier')), String(data.get('password')), attempt.signal)
      if (!attempt.signal.aborted) onSuccess()
    } catch (failure) {
      if (!attempt.signal.aborted) {
        setError(failure instanceof WebError && failure.status === 401
          ? 'Usuario o contraseña incorrectos.'
          : 'No se pudo iniciar sesión. Intenta nuevamente.')
        if (password instanceof HTMLInputElement) password.value = ''
      }
    } finally {
      if (!attempt.signal.aborted) setPending(false)
      controller.current = null
    }
  }

  return (
    <section className="mx-auto grid max-w-95 gap-6 pt-12 md:pt-20 lg:pt-26">
      <div className="grid gap-2">
        <h1 className="text-3xl font-bold tracking-tight">Ingresar</h1>
        <p className="text-sm text-muted-foreground">Consulta las máquinas a las que tienes acceso.</p>
      </div>
      {expired && (
        <Alert role="status"><AlertDescription>La sesión venció. Vuelve a ingresar.</AlertDescription></Alert>
      )}
      {signedOut && (
        <Alert role="status"><AlertDescription>La sesión se cerró.</AlertDescription></Alert>
      )}
      <form onSubmit={submit} autoComplete="off" aria-busy={pending}>
        <FieldGroup className="gap-5">
          <Field>
            <FieldLabel htmlFor="identifier">Usuario</FieldLabel>
            <Input id="identifier" name="identifier" className="min-h-11" required />
          </Field>
          <Field>
            <FieldLabel htmlFor="password">Contraseña</FieldLabel>
            <Input id="password" name="password" type="password" className="min-h-11" required />
          </Field>
          {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
          <Button type="submit" className="min-h-11 w-full" disabled={pending}>
            {pending ? 'Ingresando…' : 'Ingresar'}
          </Button>
        </FieldGroup>
      </form>
    </section>
  )
}
