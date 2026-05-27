import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '@test/render'
import { makeFakeJwt } from '@test/jwt'
import RequireAuth from '@/auth/RequireAuth'
import Navbar from '@/components/Navbar/Navbar'

const FAKE_JWT = makeFakeJwt({ sub: 'staff@example.com', roles: ['staff'] })

function harness() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <RequireAuth>
            <>
              <Navbar />
              <div>home page</div>
            </>
          </RequireAuth>
        }
      />
      <Route path="/login" element={<div>login page</div>} />
    </Routes>
  )
}

describe('<Navbar />', () => {
  it('renders all four nav labels', () => {
    renderWithProviders(harness(), { route: '/', authToken: FAKE_JWT })
    expect(screen.getByText(/new intake/i)).toBeInTheDocument()
    expect(screen.getByText(/found items/i)).toBeInTheDocument()
    expect(screen.getByText(/lost items/i)).toBeInTheDocument()
    expect(screen.getByText(/dashboard/i)).toBeInTheDocument()
  })

  it('renders New Intake and Found Items as links; remaining entries are non-navigating placeholders', () => {
    renderWithProviders(harness(), { route: '/', authToken: FAKE_JWT })

    expect(screen.getByRole('link', { name: /new intake/i })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: /found items/i })).toHaveAttribute(
      'href',
      '/found-items',
    )

    expect(screen.queryByRole('link', { name: /lost items/i })).toBeNull()
    expect(screen.queryByRole('link', { name: /dashboard/i })).toBeNull()
  })

  it('marks the active route on New Intake via aria-current', () => {
    renderWithProviders(harness(), { route: '/', authToken: FAKE_JWT })
    expect(screen.getByRole('link', { name: /new intake/i })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('exposes placeholders as aria-disabled', () => {
    renderWithProviders(harness(), { route: '/', authToken: FAKE_JWT })
    for (const label of [/lost items/i, /dashboard/i]) {
      expect(screen.getByText(label)).toHaveAttribute('aria-disabled', 'true')
    }
  })

  it('logs the user out and redirects to /login when Log out is clicked', async () => {
    const { user } = renderWithProviders(harness(), {
      route: '/',
      authToken: FAKE_JWT,
    })

    await user.click(screen.getByRole('button', { name: /log out/i }))

    await waitFor(() => expect(screen.getByText('login page')).toBeInTheDocument())
  })
})
