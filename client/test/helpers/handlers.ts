import { http, HttpResponse } from 'msw'
import type { TokenResponse } from '@/api/auth/model'
import type { FoundItemResponse, PhotoUrlResponse } from '@/api/found-items/model'

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

export const refreshSuccess = (accessToken = 'refreshed-access-token', refreshToken = 'rotated-refresh-token') =>
  http.post('*/api/auth/refresh', () =>
    HttpResponse.json<TokenResponse>({
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: 3600,
    }),
  )

export const refreshFailure = () =>
  http.post('*/api/auth/refresh', () =>
    HttpResponse.json({ message: 'Invalid refresh token' }, { status: 401 }),
  )

export const logoutSuccess = () =>
  http.post('*/api/auth/logout', () => new HttpResponse(null, { status: 204 }))

export const foundItemsList = (items: FoundItemResponse[]) =>
  http.get('*/api/found-items', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status')
    const filtered = status ? items.filter((i) => i.status === status) : items
    return HttpResponse.json<FoundItemResponse[]>(filtered)
  })

export const foundItemsListError = () =>
  http.get('*/api/found-items', () =>
    HttpResponse.json({ message: 'boom' }, { status: 500 }),
  )

export const foundItemPhotoUrl = (url = 'https://example.test/photo.jpg') =>
  http.get('*/api/found-items/:id/photo', () =>
    HttpResponse.json<PhotoUrlResponse>({ url }),
  )

export const foundItemDeleteSuccess = () =>
  http.delete('*/api/found-items/:id', () => new HttpResponse(null, { status: 204 }))

export const handlers = [loginSuccess(), logoutSuccess()]
