import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import { refreshSuccess } from '@test/handlers'
import { makeFakeJwt } from '@test/jwt'
import { setRefreshToken } from '@/auth/token-store'
import RequireAuth from '@/auth/RequireAuth'

const FAKE_JWT = makeFakeJwt()

function routes() {
  return (
    <Routes>
      <Route path="/login" element={<div>login page</div>} />
      <Route
        path="/secret"
        element={
          <RequireAuth>
            <div>secret content</div>
          </RequireAuth>
        }
      />
    </Routes>
  )
}

describe('<RequireAuth />', () => {
  it('redirects to /login when unauthenticated', () => {
    renderWithProviders(routes(), { route: '/secret' })
    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('renders children when authenticated', async () => {
    renderWithProviders(routes(), { route: '/secret', authToken: FAKE_JWT })
    expect(await screen.findByText('secret content')).toBeInTheDocument()
  })

  it('waits for boot rehydration instead of flashing the login page', async () => {
    setRefreshToken('persisted-refresh')
    server.use(refreshSuccess(FAKE_JWT))

    renderWithProviders(routes(), { route: '/secret' })

    // While the boot refresh is in flight the guard renders nothing — neither
    // the protected content nor a redirect to /login.
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()

    expect(await screen.findByText('secret content')).toBeInTheDocument()
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
  })
})
