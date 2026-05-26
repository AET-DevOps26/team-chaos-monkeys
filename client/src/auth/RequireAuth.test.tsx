import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '@/test/render'
import { makeFakeJwt } from '@/test/jwt'
import RequireAuth from './RequireAuth'

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
})
