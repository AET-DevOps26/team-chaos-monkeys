import { keepPreviousData, QueryClient } from '@tanstack/react-query'

// Mutations invalidate their query keys, so staleTime never delays
// post-write freshness; it only stops refetch storms on navigation.
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        placeholderData: keepPreviousData,
      },
    },
  })
}
