import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const periods = [
  { minutes: 10, label: 'Últimos 10 min' },
  { minutes: 60, label: 'Última hora' },
  { minutes: 1440, label: 'Últimas 24 h' },
] as const

export type PeriodMinutes = (typeof periods)[number]['minutes']

type PeriodSelectorProps = {
  value: PeriodMinutes
  onChange: (period: PeriodMinutes) => void
}

export function PeriodSelector({ value, onChange }: PeriodSelectorProps) {
  function selectPeriod(next: string) {
    const period = periods.find(({ minutes }) => String(minutes) === next)
    if (period) onChange(period.minutes)
  }

  return (
    <Select value={String(value)} onValueChange={selectPeriod}>
      <SelectTrigger aria-label="Período de tendencias" className="min-h-11 w-54 max-w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent position="popper" align="start" side="bottom" sideOffset={4} className="w-(--radix-select-trigger-width)">
        <SelectGroup>
          {periods.map(({ minutes, label }) => (
            <SelectItem
              key={minutes}
              value={String(minutes)}
              className="min-h-11 data-[state=checked]:bg-accent data-[state=checked]:font-semibold data-[state=checked]:text-primary"
            >
              {label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}
