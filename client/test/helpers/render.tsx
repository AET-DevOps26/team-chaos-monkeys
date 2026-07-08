import { useEffect, useState, type ReactElement, type ReactNode } from 'react'
import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/auth/AuthContext'
import { useAuth } from '@/auth/useAuth'
import { ToastProvider } from '@/components/Toast/ToastProvider'

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
    if (seeded) return

    if (token && accessToken === token) {
      setSeeded(true)
      return
    }

    if (token && accessToken !== token) {
      login({
        accessToken: token,
        refreshToken: 'seed-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      })
    }
  }, [token, accessToken, login, seeded])

  return seeded || accessToken === token ? <>{children}</> : null
}

export function renderWithProviders(
  ui: ReactElement,
  { route = '/', authToken = null, queryClient = makeQueryClient(), ...rtl }: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {},
): RenderWithProvidersResult {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <ToastProvider>
            <SeedAuth token={authToken}>{children}</SeedAuth>
          </ToastProvider>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )

  const result = render(ui, { wrapper: Wrapper, ...rtl })
  return { ...result, user: userEvent.setup(), queryClient }
}
