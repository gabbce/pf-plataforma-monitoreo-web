import type { Measurement } from '@/readings'

export class WebError extends Error {
  readonly status: 401 | 403 | 500

  constructor(status: 401 | 403 | 500) {
    super(String(status))
    this.status = status
  }
}

export type MachineSummary = {
  chassis: string
  lastAuthenticatedContactAt: string | null
}

export type MeasurementDefinition = {
  definitionId: string
  name: string
  unit: string
}

export type Machine = MachineSummary & { definitions: MeasurementDefinition[] }
export type LatestReadings = {
  asOf: string
  items: { measurement: Measurement; freshness: string }[]
}

export type Interval = { from: number; to: number }
export type HistoryQuery = Interval & { definitionId: string }
export type HistoryPage = {
  asOf: string
  page: number
  size: number
  total: number
  items: Measurement[]
}
export type HistoryResult = {
  pages: HistoryPage[]
  items: Measurement[]
  asOf: string
  total: number
}

export interface WebDataSource {
  signIn: (identifier: string, password: string, signal: AbortSignal) => Promise<void>
  machines: (signal: AbortSignal) => Promise<MachineSummary[]>
  machine: (chassis: string, signal: AbortSignal) => Promise<Machine>
  latest: (chassis: string, signal: AbortSignal) => Promise<LatestReadings>
  history: (
    chassis: string,
    query: HistoryQuery,
    page: number,
    asOf: string | undefined,
    signal: AbortSignal,
  ) => Promise<HistoryPage>
  knownGaps: (definitionId: string) => number[]
}

export async function loadHistory(
  readPage: WebDataSource['history'],
  chassis: string,
  query: HistoryQuery,
  signal: AbortSignal,
  asOf?: string,
): Promise<HistoryResult> {
  const first = await readPage(chassis, query, 1, asOf, signal)
  if (first.size < 1 || first.page !== 1 || (asOf && first.asOf !== asOf)) {
    throw new WebError(500)
  }
  const pageCount = Math.max(1, Math.ceil(first.total / first.size))
  const pages = [first]
  for (let page = 2; page <= pageCount; page++) {
    pages.push(await readPage(chassis, query, page, first.asOf, signal))
  }

  const items = pages.flatMap((page) => page.items)
  if (
    items.length !== first.total ||
    pages.some((page, index) => (
      page.asOf !== first.asOf || page.page !== index + 1 ||
      page.size !== first.size || page.total !== first.total
    ))
  ) {
    throw new WebError(500)
  }
  return { pages, items, asOf: first.asOf, total: first.total }
}

export function recentInterval(asOf: string, minutes: number): Interval {
  const to = Date.parse(asOf)
  return { from: to - minutes * 60_000, to }
}
