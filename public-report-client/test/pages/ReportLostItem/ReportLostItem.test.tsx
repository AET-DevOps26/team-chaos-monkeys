import { describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
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
    renderWithProviders(<AppRoutes />)

    expect(screen.getByRole('button', { name: /submit report/i })).toBeDisabled()
  })

  it('shows validation errors for an invalid contact email', async () => {
    const { user } = renderWithProviders(<AppRoutes />)

    await user.type(screen.getByLabelText(/contact email/i), 'not-an-email')
    await user.tab()

    expect(
      await screen.findByText(/email/i, { selector: 'span' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /submit report/i })).toBeDisabled()
  })

  it('submits a valid report and navigates to the confirmation page', async () => {
    server.use(createLostReportSuccess(REPORT))
    const { user } = renderWithProviders(<AppRoutes />)

    await fillForm(user)

    const submit = screen.getByRole('button', { name: /submit report/i })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByText(/report submitted/i)).toBeInTheDocument()
    expect(screen.getByText(`#${REPORT.id}`)).toBeInTheDocument()
    expect(screen.getByText(REPORT.description!)).toBeInTheDocument()
  })
})
