import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { keepPreviousData, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { createQueryClient } from '@/queryClient'

function Probe({ queryFn }: { queryFn: () => Promise<string> }) {
  const { data } = useQuery({ queryKey: ['probe'], queryFn })
  return <div>{data ?? 'loading'}</div>
}

describe('createQueryClient', () => {
  it('serves a remount within staleTime from cache without refetching', async () => {
    const client = createQueryClient()
    const queryFn = vi.fn().mockResolvedValue('cached')
    const ui = (
      <QueryClientProvider client={client}>
        <Probe queryFn={queryFn} />
      </QueryClientProvider>
    )

    const first = render(ui)
    await screen.findByText('cached')
    first.unmount()

    render(ui)
    await screen.findByText('cached')
    expect(queryFn).toHaveBeenCalledTimes(1)
  })

  it('disables window-focus refetch and keeps previous data during key changes', () => {
    const defaults = createQueryClient().getDefaultOptions().queries
    expect(defaults?.staleTime).toBe(30_000)
    expect(defaults?.refetchOnWindowFocus).toBe(false)
    expect(defaults?.placeholderData).toBe(keepPreviousData)
  })
})
