import { describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import { createLostReportSuccess } from '@test/handlers'
import AppRoutes from '@/routes'
import { LostReportResponseStatus } from '@/api/lost-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'

const REPORT: LostReportResponse = {
  id: '11111111-1111-1111-1111-111111111111',
  description: 'Black leather wallet',
  lostAt: '2026-05-20T10:00:00Z',
  status: LostReportResponseStatus.OPEN,
  contactEmail: 'anna@example.com',
}

// A report is scoped to a venue carried in the path as a name slug
// (/report/grand-hotel). The default msw handler serves a "Grand Hotel" venue.
const venueRoute = '/grand-hotel'

async function fillForm(user: ReturnType<typeof renderWithProviders>['user']) {
  await user.type(
    screen.getByLabelText(/description/i),
    'Black leather wallet with three cards inside',
  )
  // datetime-local doesn't play well with userEvent.type; set it directly.
  fireEvent.change(screen.getByLabelText(/when did you lose it/i), {
    target: { value: '2026-05-20T10:00' },
  })
  await user.type(screen.getByLabelText(/contact email/i), 'anna@example.com')
}

describe('<ReportLostItem />', () => {
  it('keeps submit disabled until the form is valid', async () => {
    renderWithProviders(<AppRoutes />, { route: venueRoute })

    expect(screen.getByRole('button', { name: /submit report/i })).toBeDisabled()
  })

  it('blocks submission and warns when the venue link is invalid', async () => {
    const { user } = renderWithProviders(<AppRoutes />, { route: '/unknown-venue' })

    expect(
      await screen.findByText(/report link is invalid/i),
    ).toBeInTheDocument()

    await fillForm(user)
    expect(screen.getByRole('button', { name: /submit report/i })).toBeDisabled()
  })

  it('shows validation errors for an invalid contact email', async () => {
    const { user } = renderWithProviders(<AppRoutes />, { route: venueRoute })

    await user.type(screen.getByLabelText(/contact email/i), 'not-an-email')
    await user.tab()

    expect(
      await screen.findByText(/email/i, { selector: 'span' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /submit report/i })).toBeDisabled()
  })

  it('creates the report as JSON and navigates to the confirmation page', async () => {
    // The create endpoint no longer accepts multipart — sending it as
    // multipart is what produced the 415. Assert the request goes out as JSON.
    let createMethod = ''
    let createContentType = ''
    server.use(
      http.post('*/api/lost-items', ({ request }) => {
        createMethod = request.method
        createContentType = request.headers.get('content-type') ?? ''
        return HttpResponse.json<LostReportResponse>(REPORT)
      }),
    )
    const { user } = renderWithProviders(<AppRoutes />, { route: venueRoute })

    await fillForm(user)

    const submit = screen.getByRole('button', { name: /submit report/i })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByText(/report submitted/i)).toBeInTheDocument()
    expect(screen.getByText(`#${REPORT.id}`)).toBeInTheDocument()
    expect(screen.getByText(REPORT.description!)).toBeInTheDocument()
    expect(createMethod).toBe('POST')
    expect(createContentType).toMatch(/application\/json/)
  })

  it('uploads an attached photo as a separate multipart PUT to the report id', async () => {
    // After the JSON create, the optional photo is sent as its own multipart
    // request keyed by the new report id. We assert on the dispatched request
    // (method/URL/content-type) rather than navigation: a FormData request
    // over axios never settles under msw/node here, so we only verify the
    // photo PUT is issued correctly.
    let photoMethod = ''
    let photoUrl = ''
    let photoContentType = ''
    server.use(
      createLostReportSuccess(REPORT),
      http.put('*/api/lost-items/:id/photo', ({ request }) => {
        photoMethod = request.method
        photoUrl = request.url
        photoContentType = request.headers.get('content-type') ?? ''
        return HttpResponse.json<LostReportResponse>(REPORT)
      }),
    )
    const { user } = renderWithProviders(<AppRoutes />, { route: venueRoute })

    await fillForm(user)
    const file = new File(['binary'], 'wallet.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText(/photo/i), file)

    const submit = screen.getByRole('button', { name: /submit report/i })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    await waitFor(() =>
      expect(photoUrl).toContain(`/api/lost-items/${REPORT.id}/photo`),
    )
    expect(photoMethod).toBe('PUT')
    expect(photoContentType).toMatch(/multipart\/form-data/)
  })
})
