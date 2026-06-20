import { http, HttpResponse } from 'msw'
import type { LostReportResponse } from '@/api/lost-items/model'
import type { PublicVenue } from '@/venues'

// A venue the report page resolves the URL slug against. "Grand Hotel"
// slugifies to "grand-hotel", so the report route is /grand-hotel.
export const VENUE: PublicVenue = {
  venueId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  name: 'Grand Hotel',
}

// Default handler so the public venues lookup resolves in every test.
export const publicVenuesSuccess = () =>
  http.get('*/api/venues/public', () => HttpResponse.json<PublicVenue[]>([VENUE]))

export const createLostReportSuccess = (report: LostReportResponse) =>
  http.post('*/api/lost-items', () => HttpResponse.json<LostReportResponse>(report))

export const createLostReportError = () =>
  http.post('*/api/lost-items', () =>
    HttpResponse.json({ message: 'boom' }, { status: 500 }),
  )

export const updateLostReportPhotoSuccess = (report: LostReportResponse) =>
  http.put('*/api/lost-items/:id/photo', () =>
    HttpResponse.json<LostReportResponse>(report),
  )

export const handlers = [publicVenuesSuccess()]
