import { ReadingPanel } from '@/components/reading-panel'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatMeasurementTime, formatReadingValue, qualityLabel } from '@/readings'
import type { HistoryResult, MeasurementDefinition } from '@/web-data'

export function HistoryTable({
  definition,
  chassis,
  results,
  page,
  onPageChange,
}: {
  definition: MeasurementDefinition
  chassis: string
  results: HistoryResult
  page: number
  onPageChange: (page: number) => void
}) {
  const visiblePage = Math.min(page, results.pages.length)
  const selectedPage = results.pages[visiblePage - 1]
  const asOf = Date.parse(results.asOf)

  return (
    <section aria-label="Registros históricos" className="grid gap-3">
      <p role="status" className="text-sm text-muted-foreground">
        {results.total} {results.total === 1 ? 'registro' : 'registros'}
      </p>
      <Table>
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead scope="col" className="lg:w-[29%]">Medición</TableHead>
            <TableHead scope="col" className="lg:w-[18%]">Valor</TableHead>
            <TableHead scope="col">Detalle</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {selectedPage.items.map((reading) => (
            <TableRow key={reading.recordId} className="relative h-11">
              <TableCell className="whitespace-normal">
                <time dateTime={reading.occurredAt}>{formatMeasurementTime(reading.occurredAt, true)}</time>
              </TableCell>
              <TableCell className="whitespace-normal">
                <span className="whitespace-nowrap tabular-nums">{formatReadingValue(reading)} {reading.unit}</span>
                {reading.quality !== 'VALID' && (
                  <span className="block text-xs text-muted-foreground">{qualityLabel(reading.quality)}</span>
                )}
              </TableCell>
              <TableCell>
                <ReadingPanel
                  chassis={chassis}
                  name={definition.name}
                  reading={reading}
                  asOf={asOf}
                  trigger={(
                    <Button
                      variant="link"
                      className="min-h-11 px-0 after:absolute after:inset-0 focus-visible:after:ring-2 focus-visible:after:ring-ring focus-visible:after:ring-inset"
                    >
                      Ver<span className="sr-only"> lectura del {formatMeasurementTime(reading.occurredAt, true)}</span>
                    </Button>
                  )}
                />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {results.pages.length > 1 && (
        <nav aria-label="Paginación de registros" className="flex flex-wrap items-center justify-between gap-2">
          <Button
            type="button"
            variant="outline"
            className="min-h-11"
            disabled={visiblePage === 1}
            onClick={() => onPageChange(visiblePage - 1)}
          >
            Anterior
          </Button>
          <p role="status" className="text-center text-sm text-muted-foreground">
            Página {visiblePage} de {results.pages.length}
          </p>
          <Button
            type="button"
            variant="outline"
            className="min-h-11"
            disabled={visiblePage === results.pages.length}
            onClick={() => onPageChange(visiblePage + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
    </section>
  )
}
