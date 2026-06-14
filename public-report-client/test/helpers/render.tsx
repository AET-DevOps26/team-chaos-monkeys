import { type ReactElement, type ReactNode } from 'react'
import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { MemoryRouter, type InitialEntry } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

type ProviderOptions = {
  // A path string, or a full location object (e.g. to seed router state).
  route?: InitialEntry
  queryClient?: QueryClient
}

export type RenderWithProvidersResult = RenderResult & {
  user: UserEvent
  queryClient: QueryClient
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  })
}

// The public app is unauthenticated — no AuthProvider, unlike the admin client.
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', queryClient = makeQueryClient(), ...rtl }: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {},
): RenderWithProvidersResult {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
    </QueryClientProvider>
  )

  const result = render(ui, { wrapper: Wrapper, ...rtl })
  return { ...result, user: userEvent.setup(), queryClient }
}
