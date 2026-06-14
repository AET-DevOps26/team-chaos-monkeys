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

// A report is always scoped to a venue carried in the path (/report/<venueId>).
const VENUE_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6'
const venueRoute = `/${VENUE_ID}`

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
    const { user } = renderWithProviders(<AppRoutes />, { route: '/not-a-uuid' })

    expect(screen.getByText(/report link is invalid/i)).toBeInTheDocument()

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

  it('submits a valid report and navigates to the confirmation page', async () => {
    server.use(createLostReportSuccess(REPORT))
    const { user } = renderWithProviders(<AppRoutes />, { route: venueRoute })

    await fillForm(user)

    const submit = screen.getByRole('button', { name: /submit report/i })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByText(/report submitted/i)).toBeInTheDocument()
    expect(screen.getByText(`#${REPORT.id}`)).toBeInTheDocument()
    expect(screen.getByText(REPORT.description!)).toBeInTheDocument()
  })

  it('sends the attached photo as a multipart part', async () => {
    let contentType = ''
    let rawBody = ''
    server.use(
      // Inspect the raw multipart body: the undici FormData parser can't
      // read the JSON `request` Blob part, so assert on the wire bytes.
      http.post('*/api/lost-items', async ({ request }) => {
        contentType = request.headers.get('content-type') ?? ''
        rawBody = await request.text()
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

    expect(await screen.findByText(/report submitted/i)).toBeInTheDocument()
    // Photo reaches the wire as its own multipart part alongside the JSON
    // `request` part. (jsdom/undici degrades the filename to "blob" and
    // doesn't inline the Blob bytes, so assert on the part headers only.)
    expect(contentType).toMatch(/multipart\/form-data/)
    expect(rawBody).toMatch(/name="request"[\s\S]*Content-Type: application\/json/)
    expect(rawBody).toMatch(/name="photo"[\s\S]*Content-Type: image\/png/)
  })
})
