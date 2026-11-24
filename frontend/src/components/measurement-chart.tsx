import { useRef, useState } from 'react'
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'

import { ReadingDetails } from '@/components/reading-panel'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet } from '@/components/ui/sheet'
import {
  formatAxisValue,
  formatClockTime,
  formatMeasurementTime,
  formatReadingValue,
  qualityLabel,
  type ChartSample,
  type Measurement,
} from '@/readings'

type MeasurementChartProps = {
  chassis: string
  name: string
  unit: string
  samples: ChartSample[]
  from: number
  to: number
  asOf: number
  showTitle?: boolean
}

const chartValue = (reading: Measurement | null) => {
  if (!reading) return 'Sin datos'
  return reading.quality === 'VALID'
    ? `${formatReadingValue(reading)} ${reading.unit}`
    : qualityLabel(reading.quality)
}

export function MeasurementChart({
  chassis,
  name,
  unit,
  samples,
  from,
  to,
  asOf,
  showTitle = true,
}: MeasurementChartProps) {
  const [reading, setReading] = useState<Measurement>()
  const [panelOpen, setPanelOpen] = useState(false)
  const opensPanel = useRef(false)
  const sampleTrigger = useRef<HTMLButtonElement>(null)
  const data = samples
    .filter(({ timestamp }) => timestamp >= from && timestamp < to)
    .map((sample) => ({
      ...sample,
      value: sample.reading?.quality === 'VALID' ? sample.reading.value : null,
    }))
  const records = data.flatMap((sample) => sample.reading ? [sample.reading] : [])
  const tooltipTrigger = window.matchMedia('(hover: none)').matches ? 'click' : 'hover'
  const config = { value: { label: name, color: 'var(--chart-1)' } } satisfies ChartConfig

  function selectReading(recordId: string) {
    const record = records.find((item) => item.recordId === recordId)
    if (!record) return
    opensPanel.current = true
    setReading(record)
    setPanelOpen(true)
  }

  return (
    <figure className="min-w-0" aria-label={`Tendencia de ${name}`}>
      <figcaption className="mb-2">
        {showTitle && <span className="block text-base font-semibold">{name}</span>}
        <span className="text-xs text-muted-foreground">{unit}</span>
      </figcaption>
      {data.length === 0 ? (
        <p className="py-8 text-sm text-muted-foreground">Sin mediciones en este período.</p>
      ) : (
        <ChartContainer
          config={config}
          className="aspect-auto h-56 w-full [&_.recharts-surface:focus-visible]:outline-2 [&_.recharts-surface:focus-visible]:outline-ring"
        >
          <LineChart accessibilityLayer title={name} data={data} margin={{ top: 8, right: 14, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis
              dataKey="timestamp"
              type="number"
              domain={[from, to]}
              allowDataOverflow
              tickFormatter={formatClockTime}
              tickLine={false}
              axisLine={false}
              minTickGap={18}
            />
            <YAxis
              width={44}
              tickFormatter={formatAxisValue}
              tickLine={false}
              axisLine={false}
              domain={['auto', 'auto']}
            />
            <ChartTooltip
              trigger={tooltipTrigger}
              filterNull={false}
              content={({ active, payload }) => {
                const sample = payload?.[0]?.payload as ChartSample | undefined
                if (!active || !sample) return null
                return (
                  <div role="status" className="grid gap-1 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
                    <span className="text-muted-foreground">
                      {formatMeasurementTime(new Date(sample.timestamp).toISOString(), true)}
                    </span>
                    <span className="font-medium">{chartValue(sample.reading)}</span>
                  </div>
                )
              }}
            />
            <Line
              dataKey="value"
              type="linear"
              stroke="var(--color-value)"
              strokeWidth={2}
              dot={{ r: 3, fill: 'var(--color-value)', strokeWidth: 0 }}
              activeDot={{ r: 5 }}
              connectNulls={false}
              isAnimationActive={false}
            />
          </LineChart>
        </ChartContainer>
      )}
      {records.length > 0 && (
        <Select value="" onValueChange={selectReading}>
          <SelectTrigger
            ref={sampleTrigger}
            aria-label={`Consultar muestras de ${name}`}
            className="mt-2 min-h-11 max-w-full"
          >
            <SelectValue placeholder="Consultar muestras" />
          </SelectTrigger>
          <SelectContent
            position="popper"
            align="start"
            onCloseAutoFocus={(event) => {
              if (opensPanel.current) {
                event.preventDefault()
                opensPanel.current = false
              }
            }}
          >
            <SelectGroup>
              {records.map((record) => (
                <SelectItem key={record.recordId} value={record.recordId} className="min-h-11">
                  {formatMeasurementTime(record.occurredAt, true)} · {chartValue(record)}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      )}
      <Sheet open={panelOpen} onOpenChange={setPanelOpen}>
        {reading && (
          <ReadingDetails
            chassis={chassis}
            name={name}
            reading={reading}
            asOf={asOf}
            onCloseAutoFocus={(event) => {
              event.preventDefault()
              sampleTrigger.current?.focus()
            }}
          />
        )}
      </Sheet>
    </figure>
  )
}
