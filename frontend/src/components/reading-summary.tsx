import { ReadingPanel } from '@/components/reading-panel'
import { formatMeasurementTime, formatReadingValue, freshnessLabel, qualityLabel } from '@/readings'
import type { LatestReadings, MeasurementDefinition } from '@/web-data'

export function ReadingSummary({
  definition,
  reading,
  chassis,
  asOf,
  heading: Heading = 'h2',
  showAllStatuses = false,
}: {
  definition: MeasurementDefinition
  reading: LatestReadings['items'][number] | undefined
  chassis: string
  asOf: number
  heading?: 'h2' | 'h3'
  showAllStatuses?: boolean
}) {
  const { name, unit } = definition

  if (!reading) {
    return (
      <div className="grid gap-2">
        <div className="flex flex-wrap items-baseline gap-x-2">
          <Heading className="text-xl font-semibold">{name}</Heading>
          <span className="text-sm text-muted-foreground">{unit}</span>
        </div>
        <p className="text-sm text-muted-foreground">Sin mediciones disponibles.</p>
      </div>
    )
  }

  const { measurement, freshness } = reading
  const qualityText = showAllStatuses || measurement.quality !== 'VALID'
    ? qualityLabel(measurement.quality)
    : null
  const freshnessText = showAllStatuses && freshness === 'CURRENT' ? 'Vigente' : freshnessLabel(freshness)
  return (
    <div className="grid gap-2">
      <Heading className="text-xl font-semibold md:text-2xl">{name}</Heading>
      <div className="flex flex-wrap items-baseline gap-2 tabular-nums">
        <span className="text-4xl font-bold tracking-tight md:text-5xl">{formatReadingValue(measurement)}</span>
        <span className="text-xl font-semibold">{unit}</span>
      </div>
      <p className="text-sm text-muted-foreground">
        Medición:{' '}
        <time dateTime={measurement.occurredAt}>{formatMeasurementTime(measurement.occurredAt)}</time>
      </p>
      {qualityText && <p className="text-sm font-medium">{qualityText}</p>}
      {freshnessText && <p className="text-sm font-medium">{freshnessText}</p>}
      <div><ReadingPanel chassis={chassis} name={name} reading={measurement} asOf={asOf} /></div>
    </div>
  )
}
