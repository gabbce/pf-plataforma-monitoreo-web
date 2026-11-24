import { useEffect, useState } from 'react'

import { WebError } from '@/web-data'

type Loader<T> = (signal: AbortSignal) => Promise<T>
type Completed<T> = { status: 'success'; data: T } | { status: 'error'; error: WebError }
export type QueryState<T> = Completed<T> | { status: 'loading' }

export function useQuery<T>(load: Loader<T>) {
  const [attempt, setAttempt] = useState(0)
  const [completed, setCompleted] = useState<{
    load: Loader<T>
    attempt: number
    result: Completed<T>
  }>()

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller

    function finish(result: Completed<T>) {
      if (!signal.aborted) setCompleted({ load, attempt, result })
    }

    void load(signal).then(
      (data) => finish({ status: 'success', data }),
      (error: unknown) => finish({
        status: 'error',
        error: error instanceof WebError ? error : new WebError(500),
      }),
    )
    return () => controller.abort()
  }, [load, attempt])

  const state: QueryState<T> = completed?.load === load && completed.attempt === attempt
    ? completed.result
    : { status: 'loading' }

  return { state, retry: () => setAttempt((previous) => previous + 1) }
}
