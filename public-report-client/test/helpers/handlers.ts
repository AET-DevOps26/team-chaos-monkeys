import { http, HttpResponse } from 'msw'
import type { LostReportResponse } from '@/api/lost-items/model'

export const createLostReportSuccess = (report: LostReportResponse) =>
  http.post('*/api/lost-items', () => HttpResponse.json<LostReportResponse>(report))

export const createLostReportError = () =>
  http.post('*/api/lost-items', () =>
    HttpResponse.json({ message: 'boom' }, { status: 500 }),
  )

export const handlers = []
