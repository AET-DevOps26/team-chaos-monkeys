import { http, HttpResponse } from 'msw'
import type { TokenResponse } from '@/api/auth/model'

export const loginSuccess = (accessToken = 'test-access-token') =>
  http.post('*/api/auth/login', () =>
    HttpResponse.json<TokenResponse>({
      accessToken,
      refreshToken: 'test-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    }),
  )

export const loginInvalidCredentials = () =>
  http.post('*/api/auth/login', () =>
    HttpResponse.json({ message: 'Invalid credentials' }, { status: 401 }),
  )

export const handlers = [loginSuccess()]
