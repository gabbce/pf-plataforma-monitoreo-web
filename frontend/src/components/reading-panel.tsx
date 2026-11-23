import { useId } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import {
  formatAge,
  formatMeasurementTime,
  formatReadingValue,
  qualityLabel,
  type Measurement,
} from '@/readings'

type ReadingPanelProps = {
  chassis: string
  name: string
  reading: Measurement
  asOf: number
}

export function ReadingPanel({ chassis, name, reading, asOf }: ReadingPanelProps) {
  const timeHeading = useId()

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="link" className="min-h-11 px-0">
          Ver detalle<span className="sr-only"> de {name}</span>
        </Button>
      </SheetTrigger>
      <SheetContent
        showCloseButton={false}
        className="gap-6 overflow-y-auto px-5 py-6 data-[side=right]:w-full data-[side=right]:sm:max-w-none data-[side=right]:md:w-120 md:px-7"
      >
        <div className="flex items-center justify-between gap-3">
          <SheetTitle className="text-lg font-semibold">Detalle de lectura</SheetTitle>
          <SheetClose asChild>
            <Button variant="outline" className="min-h-11">Cerrar</Button>
          </SheetClose>
        </div>
        <SheetDescription className="sr-only">{name}, chasis {chassis}</SheetDescription>
        <div className="grid gap-1">
          <h2 className="text-2xl font-semibold">{name}</h2>
          <p className="text-sm text-muted-foreground">Chasis {chassis}</p>
        </div>
        <div className="grid gap-4 rounded-lg bg-muted p-5">
          <p className="text-sm text-muted-foreground">Valor registrado</p>
          <p className="flex items-baseline gap-3 tabular-nums">
            <span className="text-5xl font-bold tracking-tight">{formatReadingValue(reading)}</span>
            <span className="text-xl font-medium text-muted-foreground">{reading.unit}</span>
          </p>
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm text-muted-foreground">Calidad</span>
            <Badge className="bg-background text-foreground" variant="secondary">
              {qualityLabel(reading.quality)}
            </Badge>
          </div>
        </div>
        <section className="border-t border-border pt-6" aria-labelledby={timeHeading}>
          <h3 id={timeHeading} className="font-semibold">Registro temporal</h3>
          <dl className="mt-2 divide-y divide-border text-sm">
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-3">
              <dt className="text-muted-foreground">Medición</dt>
              <dd><time dateTime={reading.occurredAt}>{formatMeasurementTime(reading.occurredAt, true)}</time></dd>
            </div>
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-3">
              <dt className="text-muted-foreground">Recepción</dt>
              <dd><time dateTime={reading.receivedAt}>{formatMeasurementTime(reading.receivedAt, true)}</time></dd>
            </div>
            <div className="flex items-baseline justify-between gap-4 py-3">
              <dt className="text-muted-foreground">Antigüedad</dt>
              <dd>{formatAge(reading.occurredAt, asOf)}</dd>
            </div>
          </dl>
        </section>
      </SheetContent>
    </Sheet>
  )
}
