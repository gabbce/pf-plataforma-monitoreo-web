export type Measurement = {
  recordId: string
  definitionId: string
  value: number | null
  unit: string
  quality: string
  occurredAt: string
  receivedAt: string
  context: { sourceId: string; appliedConfigVersion: string | null }
}

export type ChartSample = {
  timestamp: number
  reading: Measurement | null
}

const region = 'es-AR'
const timeZone = 'America/Argentina/Buenos_Aires'
const dateParts = {
  timeZone,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hourCycle: 'h23',
  hour: '2-digit',
  minute: '2-digit',
} as const

const dateTime = new Intl.DateTimeFormat(region, dateParts)
const preciseDateTime = new Intl.DateTimeFormat(region, { ...dateParts, second: '2-digit' })
const clockTime = new Intl.DateTimeFormat(region, { timeZone, hourCycle: 'h23', hour: '2-digit', minute: '2-digit' })
const decimal = new Intl.NumberFormat(region)

export function formatClockTime(timestamp: number) {
  return clockTime.format(timestamp)
}

export function formatMeasurementTime(instant: string, precise = false) {
  return (precise ? preciseDateTime : dateTime).format(Date.parse(instant))
}

export function formatAge(occurredAt: string, asOf: number) {
  const seconds = Math.floor((asOf - Date.parse(occurredAt)) / 1000)

  if (seconds < 0) return '—'
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60

  return remainder === 0 ? `${minutes} min` : `${minutes} min ${remainder} s`
}

export function formatReadingValue(reading: Measurement) {
  return reading.quality === 'VALID' && reading.value !== null ? decimal.format(reading.value) : '—'
}

export function formatAxisValue(value: number) {
  return decimal.format(value)
}

export function qualityLabel(quality: string) {
  switch (quality) {
    case 'VALID':
      return 'Válida'
    case 'MISSING':
      return 'Ausente'
    case 'INVALID':
      return 'Inválida'
    case 'NOT_EVALUABLE':
      return 'No evaluable'
    default:
      return 'Sin clasificar'
  }
}

export function compareReadings(left: Measurement, right: Measurement) {
  const timeOrder = Date.parse(left.occurredAt) - Date.parse(right.occurredAt)
  if (timeOrder !== 0) return timeOrder
  if (left.recordId === right.recordId) return 0
  return left.recordId < right.recordId ? -1 : 1
}

export function toChartSamples(readings: Measurement[], gaps: number[]): ChartSample[] {
  return [
    ...readings.map((reading) => ({ timestamp: Date.parse(reading.occurredAt), reading })),
    ...gaps.map((timestamp) => ({ timestamp, reading: null })),
  ].sort((left, right) => left.timestamp - right.timestamp)
}

export function freshnessLabel(freshness: string) {
  switch (freshness) {
    case 'STALE':
      return 'Desactualizada'
    case 'UNKNOWN':
      return 'Vigencia desconocida'
    default:
      return null
  }
}

export function formatDateInput(timestamp: number) {
  const parts = dateTime.formatToParts(timestamp)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((entry) => entry.type === type)?.value

  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`
}

export function parseDateInput(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null

  const guess = Date.parse(`${value}Z`)
  if (!Number.isFinite(guess)) return null

  // Interpret the wall-clock input in the same time zone used for display.
  const difference = guess - Date.parse(`${formatDateInput(guess)}Z`)
  const timestamp = guess + difference
  return formatDateInput(timestamp) === value ? timestamp : null
}
