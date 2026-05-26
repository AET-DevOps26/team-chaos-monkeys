import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '@/test/render'
import { server } from '@/test/server'
import { loginInvalidCredentials, loginSuccess } from '@/test/handlers'
import Login from './Login'

// Mints a JWT-shaped string so AuthContext.decodeJwt produces a non-null user.
const FAKE_JWT =
  'eyJhbGciOiJub25lIn0.' +
  btoa(JSON.stringify({ sub: 'user-1', roles: ['staff'] }))
    .replace(/=+$/, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_') +
  '.'

function renderLogin() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<div>home</div>} />
    </Routes>,
    { route: '/login' },
  )
}

describe('<Login />', () => {
  it('submits credentials and navigates to the post-login destination', async () => {
    server.use(loginSuccess(FAKE_JWT))
    const { user } = renderLogin()

    await user.type(screen.getByLabelText(/email/i), 'staff@example.com')
    await user.type(screen.getByLabelText(/password/i), 'hunter22')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(screen.getByText('home')).toBeInTheDocument())
  })

  it('shows an invalid-credentials message on 401', async () => {
    server.use(loginInvalidCredentials())
    const { user } = renderLogin()

    await user.type(screen.getByLabelText(/email/i), 'staff@example.com')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument()
  })

  it('keeps the submit button disabled while the form is invalid', async () => {
    const { user } = renderLogin()
    const submit = screen.getByRole('button', { name: /sign in/i })
    expect(submit).toBeDisabled()

    await user.type(screen.getByLabelText(/email/i), 'not-an-email')
    expect(submit).toBeDisabled()

    await user.clear(screen.getByLabelText(/email/i))
    await user.type(screen.getByLabelText(/email/i), 'staff@example.com')
    await user.type(screen.getByLabelText(/password/i), 'hunter22')
    expect(submit).toBeEnabled()
  })
})
