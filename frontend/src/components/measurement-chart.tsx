import { CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'

import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import {
  formatAxisValue,
  formatClockTime,
  formatMeasurementTime,
  formatReadingValue,
  qualityLabel,
  type ChartSample,
} from '@/readings'

type MeasurementChartProps = {
  name: string
  unit: string
  samples: ChartSample[]
  from: number
  to: number
}

export function MeasurementChart({ name, unit, samples, from, to }: MeasurementChartProps) {
  const data = samples
    .filter(({ timestamp }) => timestamp >= from && timestamp < to)
    .map((sample) => ({
      ...sample,
      value: sample.reading?.quality === 'VALID' ? sample.reading.value : null,
    }))
  const config = { value: { label: name, color: 'var(--chart-1)' } } satisfies ChartConfig
  const tooltipTrigger = window.matchMedia('(hover: none)').matches ? 'click' : 'hover'

  return (
    <figure className="min-w-0" aria-label={`Tendencia de ${name}`}>
      <figcaption className="mb-2">
        <span className="block text-sm font-semibold">{name}</span>
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
              content={({ active, label }) => {
                if (!active || label === undefined) return null
                const sample = data.find(({ timestamp }) => timestamp === Number(label))
                if (!sample) return null
                const { reading, timestamp } = sample

                return (
                  <div role="status" className="grid gap-1 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
                    <span className="text-muted-foreground">{formatMeasurementTime(new Date(timestamp).toISOString())}</span>
                    <span className="font-medium">
                      {reading?.quality === 'VALID'
                        ? `${formatReadingValue(reading)} ${reading.unit}`
                        : reading ? qualityLabel(reading.quality) : 'Sin datos'}
                    </span>
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
    </figure>
  )
}
