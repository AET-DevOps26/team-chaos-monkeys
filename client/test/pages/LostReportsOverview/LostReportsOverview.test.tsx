import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  lostReportPhotoUrl,
  lostReportsList,
  lostReportsListError,
} from '@test/handlers'
import LostReportsOverview from '@/pages/LostReportsOverview/LostReportsOverview'
import { LostReportResponseStatus } from '@/api/lost-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'

const REPORTS: LostReportResponse[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    description: 'Black leather wallet\nwith three cards inside',
    location: 'Main entrance',
    contactEmail: 'anna@example.com',
    lostAt: '2026-05-20T10:00:00Z',
    status: LostReportResponseStatus.OPEN,
    attributes: { category: 'Wallet' },
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    description: 'Blue umbrella',
    location: 'Lobby cafe',
    contactEmail: 'ben@example.com',
    lostAt: '2026-05-21T10:00:00Z',
    status: LostReportResponseStatus.MATCHED,
    attributes: { category: 'Umbrella' },
  },
]

describe('<LostReportsOverview />', () => {
  it('renders the open reports returned by the API', async () => {
    server.use(lostReportsList(REPORTS))
    renderWithProviders(<LostReportsOverview />)

    expect(await screen.findByText('Black leather wallet')).toBeInTheDocument()
    // MATCHED report is filtered out by the default OPEN tab.
    expect(screen.queryByText('Blue umbrella')).not.toBeInTheDocument()
  })

  it('switches the listing when a different status filter is selected', async () => {
    server.use(lostReportsList(REPORTS))
    const { user } = renderWithProviders(<LostReportsOverview />)

    await screen.findByText('Black leather wallet')
    await user.click(screen.getByRole('tab', { name: /matched/i }))

    expect(await screen.findByText('Blue umbrella')).toBeInTheDocument()
    expect(screen.queryByText('Black leather wallet')).not.toBeInTheDocument()
  })

  it('expands a row on click to reveal the full description and lazily loads the photo', async () => {
    server.use(lostReportsList(REPORTS), lostReportPhotoUrl())
    const { user } = renderWithProviders(<LostReportsOverview />)

    const summary = await screen.findByText('Black leather wallet')
    // The remainder of the description and the photo are not present until expand.
    expect(screen.queryByText(/with three cards inside/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Black leather wallet' })).not.toBeInTheDocument()

    await user.click(summary)

    expect(await screen.findByText(/with three cards inside/i)).toBeInTheDocument()
    expect(
      await screen.findByRole('img', { name: 'Black leather wallet' }),
    ).toBeInTheDocument()
  })

  it('shows the empty state and recovers via "Show all"', async () => {
    server.use(lostReportsList([]))
    const { user } = renderWithProviders(<LostReportsOverview />)

    expect(
      await screen.findByText(/no lost reports match this filter/i),
    ).toBeInTheDocument()

    server.use(lostReportsList(REPORTS))
    await user.click(screen.getByRole('button', { name: /show all/i }))

    expect(await screen.findByText('Black leather wallet')).toBeInTheDocument()
    expect(await screen.findByText('Blue umbrella')).toBeInTheDocument()
  })

  it('shows an error state with a retry button when the request fails', async () => {
    server.use(lostReportsListError())
    renderWithProviders(<LostReportsOverview />)

    expect(await screen.findByText(/couldn't load lost reports/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
  })
})
