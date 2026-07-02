import { http, HttpResponse } from 'msw'
import type { TokenResponse } from '@/api/auth/model'
import type { FoundItemResponse, PhotoUrlResponse } from '@/api/found-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import type { ItemSearchResponse, MatchResponse } from '@/api/matches/model'
import type { PickupResponse } from '@/api/pickups/model'

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
  http.get('*/api/found-items/:id/photo-url', () =>
    HttpResponse.json<PhotoUrlResponse>({ url }),
  )

export const foundItemPhoto = () =>
  http.get('*/api/found-items/:id/photo', () =>
    HttpResponse.text('', {
      headers: { 'Content-Type': 'image/jpeg' },
    }),
  )

export const foundItemDeleteSuccess = () =>
  http.delete('*/api/found-items/:id', () => new HttpResponse(null, { status: 204 }))

export const lostReportsList = (items: LostReportResponse[]) =>
  http.get('*/api/lost-items', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status')
    const filtered = status ? items.filter((i) => i.status === status) : items
    return HttpResponse.json<LostReportResponse[]>(filtered)
  })

export const lostReportsListError = () =>
  http.get('*/api/lost-items', () =>
    HttpResponse.json({ message: 'boom' }, { status: 500 }),
  )

export const lostReportPhotoUrl = (url = 'https://example.test/lost-photo.jpg') =>
  http.get('*/api/lost-items/:id/photo-url', () =>
    HttpResponse.json<PhotoUrlResponse>({ url }),
  )

export const lostReportPhoto = () =>
  http.get('*/api/lost-items/:id/photo', () =>
    HttpResponse.text('', {
      headers: { 'Content-Type': 'image/jpeg' },
    }),
  )

export const foundItemById = (items: FoundItemResponse[]) =>
  http.get('*/api/found-items/:id', ({ params }) => {
    const item = items.find((i) => i.id === params.id)
    return item
      ? HttpResponse.json<FoundItemResponse>(item)
      : HttpResponse.json({ message: 'not found' }, { status: 404 })
  })

export const lostReportById = (items: LostReportResponse[]) =>
  http.get('*/api/lost-reports/:id', ({ params }) => {
    const item = items.find((i) => i.id === params.id)
    return item
      ? HttpResponse.json<LostReportResponse>(item)
      : HttpResponse.json({ message: 'not found' }, { status: 404 })
  })

export const matchesList = (items: MatchResponse[]) =>
  http.get('*/api/matches', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status')
    const filtered = status ? items.filter((m) => m.status === status) : items
    return HttpResponse.json<MatchResponse[]>(filtered)
  })

export const matchesListError = () =>
  http.get('*/api/matches', () =>
    HttpResponse.json({ message: 'boom' }, { status: 500 }),
  )

export const pickupsList = (items: PickupResponse[]) =>
  http.get('*/api/pickups', () => HttpResponse.json<PickupResponse[]>(items))

export const itemSearchSuccess = (response: ItemSearchResponse) =>
  http.post('*/api/matches/search', () =>
    HttpResponse.json<ItemSearchResponse>(response),
  )

export const itemSearchError = () =>
  http.post('*/api/matches/search', () =>
    HttpResponse.json({ message: 'boom' }, { status: 500 }),
  )

export const handlers = [loginSuccess(), logoutSuccess()]
