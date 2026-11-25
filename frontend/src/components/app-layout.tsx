import { useEffect, useRef, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router'

import logo from '../../../brand/canavesio-logo.svg'
import { Button } from '@/components/ui/button'

export function AppLayout({
  children,
  onSignOut,
  signingOut = false,
}: {
  children: ReactNode
  onSignOut?: () => void
  signingOut?: boolean
}) {
  const { pathname } = useLocation()
  const main = useRef<HTMLElement>(null)

  useEffect(() => {
    window.scrollTo(0, 0)
    main.current?.focus({ preventScroll: true })
  }, [pathname])

  return (
    <>
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-10 focus:bg-background focus:p-2"
        onClick={(event) => {
          event.preventDefault()
          main.current?.focus()
        }}
      >
        Saltar al contenido
      </a>
      <header className="border-b-2 border-brand">
        <div className="mx-auto flex min-h-16 max-w-[1440px] items-center gap-2 px-4 md:min-h-20 md:gap-6 md:px-6 lg:px-11">
          <img src={logo} alt="Canavesio" className="h-auto w-29 shrink-0 md:w-42.5 lg:w-55" />
          <p className="hidden font-semibold md:block lg:text-xl">
            <span className="lg:hidden">Monitoreo</span>
            <span className="hidden lg:inline">Plataforma de monitoreo</span>
          </p>
          {onSignOut && (
            <nav aria-label="Navegación principal" className="ml-auto flex items-center gap-1 md:gap-4">
              <Button variant="link" className="min-h-11 px-1.5 md:px-2" asChild>
                <Link to="/maquinas">Máquinas</Link>
              </Button>
              <Button
                variant="link"
                className="min-h-11 px-1.5 md:px-2"
                onClick={onSignOut}
                disabled={signingOut}
              >
                {signingOut ? 'Cerrando…' : (
                  <>
                    <span className="md:hidden">Salir</span>
                    <span className="hidden md:inline">Cerrar sesión</span>
                  </>
                )}
              </Button>
            </nav>
          )}
        </div>
      </header>
      <main
        id="main-content"
        ref={main}
        tabIndex={-1}
        className="mx-auto max-w-[1440px] px-4 py-5 focus:outline-none md:px-6 md:py-8 lg:px-11 lg:py-11"
      >
        {children}
      </main>
    </>
  )
}

export function MachineBreadcrumb({ chassis, history = false }: { chassis: string; history?: boolean }) {
  return (
    <nav aria-label="Ubicación" className="text-sm">
      <ol className="flex flex-wrap items-center gap-2">
        <li><Link className="text-primary hover:underline" to="/maquinas">Máquinas</Link></li>
        <li aria-hidden="true">/</li>
        {history ? (
          <>
            <li><Link className="text-primary hover:underline" to={`/maquinas/${encodeURIComponent(chassis)}`}>{chassis}</Link></li>
            <li aria-hidden="true">/</li>
            <li aria-current="page" className="text-muted-foreground">Histórico</li>
          </>
        ) : (
          <li aria-current="page" className="text-muted-foreground">{chassis}</li>
        )}
      </ol>
    </nav>
  )
}
