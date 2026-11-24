import assert from 'node:assert/strict'
import { test } from 'node:test'

import { formatDateInput, parseDateInput, type Measurement } from '../src/readings.ts'
import { loadHistory, type HistoryPage, type HistoryQuery } from '../src/web-data.ts'

const cutoff = '2026-01-15T12:11:00Z'
const signal = new AbortController().signal
const query: HistoryQuery = {
  definitionId: 'engine.rpm',
  from: Date.parse('2026-01-15T12:00:00Z'),
  to: Date.parse(cutoff),
}
const zero: Measurement = {
  recordId: 'record-1',
  definitionId: query.definitionId,
  value: 0,
  unit: 'rpm',
  quality: 'VALID',
  occurredAt: '2026-01-15T12:00:00Z',
  receivedAt: '2026-01-15T12:00:05Z',
  context: { sourceId: 'rpm-b', appliedConfigVersion: null },
}

function pageAt(page: number, asOf = cutoff): HistoryPage {
  return {
    asOf,
    page,
    size: 1,
    total: 2,
    items: page === 1 ? [zero] : [{ ...zero, recordId: 'record-2', value: 720 }],
  }
}

test('history uses the first response cutoff for each later page and preserves a valid zero', async () => {
  const requests: Array<number | string | undefined> = []
  const result = await loadHistory(async (chassis, filters, page, asOf, receivedSignal) => {
    assert.equal(chassis, '000123')
    assert.equal(filters, query)
    assert.equal(receivedSignal, signal)
    requests.push(page, asOf)
    return pageAt(page)
  }, '000123', query, signal)

  assert.deepEqual(requests, [1, undefined, 2, cutoff])
  assert.deepEqual(result.items.map(({ value }) => value), [0, 720])
  assert.equal(result.total, 2)
})

test('history rejects a page from a different visibility cutoff', async () => {
  await assert.rejects(
    loadHistory(async (_chassis, _query, page) => (
      pageAt(page, page === 1 ? cutoff : '2026-01-15T12:12:00Z')
    ), '000123', query, signal),
    { status: 500 },
  )
})

test('form input round-trips between Argentina wall time and the UTC query', () => {
  assert.equal(formatDateInput(query.from), '2026-01-15T09:00')
  assert.equal(parseDateInput('2026-01-15T09:00'), query.from)
  assert.equal(parseDateInput('2026-02-30T09:00'), null)
})
