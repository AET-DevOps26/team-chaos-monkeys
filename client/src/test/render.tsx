import { type ReactElement, type ReactNode } from 'react'
import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/auth/AuthContext'
import { setCurrentToken } from '@/auth/token-store'

type ProviderOptions = {
  route?: string
  authToken?: string | null
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

export function renderWithProviders(
  ui: ReactElement,
  { route = '/', authToken = null, queryClient = makeQueryClient(), ...rtl }: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {},
): RenderWithProvidersResult {
  if (authToken) setCurrentToken(authToken)

  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>{children}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )

  const result = render(ui, { wrapper: Wrapper, ...rtl })
  return { ...result, user: userEvent.setup(), queryClient }
}
