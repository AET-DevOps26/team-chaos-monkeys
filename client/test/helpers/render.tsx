import { useEffect, useState, type ReactElement, type ReactNode } from 'react'
import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/auth/AuthContext'
import { useAuth } from '@/auth/useAuth'

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

function SeedAuth({ token, children }: { token: string | null; children: ReactNode }) {
  const { login, accessToken } = useAuth()
  const [seeded, setSeeded] = useState(token === null)
  useEffect(() => {
    if (token && accessToken !== token) {
      login(token)
    }
    if (token) setSeeded(true)
  }, [token, accessToken, login])
  return seeded ? <>{children}</> : null
}

export function renderWithProviders(
  ui: ReactElement,
  { route = '/', authToken = null, queryClient = makeQueryClient(), ...rtl }: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {},
): RenderWithProvidersResult {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <SeedAuth token={authToken}>{children}</SeedAuth>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )

  const result = render(ui, { wrapper: Wrapper, ...rtl })
  return { ...result, user: userEvent.setup(), queryClient }
}
