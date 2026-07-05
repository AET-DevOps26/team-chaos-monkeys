import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  lostReportsList,
  lostReportsListError,
} from '@test/handlers'
import LostReportsOverview from '@/pages/LostReportsOverview/LostReportsOverview'
import { LostReportResponseStatus } from '@/api/lost-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'

vi.mock('@/components/PhotoThumbnail/PhotoThumbnail', async () => {
  const React = await import('react')
  return {
    default: ({ src, alt }: { src?: string; alt: string }) =>
      src
        ? React.createElement('img', { src, alt })
        : React.createElement('div', { role: 'img', 'aria-label': alt }),
  }
})

const REPORTS: LostReportResponse[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    photoUrl: '/api/lost-items/11111111-1111-1111-1111-111111111111/photo',
    description: 'Black leather wallet\nwith three cards inside',
    location: 'Main entrance',
    contactEmail: 'anna@example.com',
    lostAt: '2026-05-20T10:00:00Z',
    status: LostReportResponseStatus.OPEN,
    attributes: { category: 'Wallet' },
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    photoUrl: '/api/lost-items/22222222-2222-2222-2222-222222222222/photo',
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

  it('renders a thumbnail for each report inline (no expansion needed)', async () => {
    server.use(lostReportsList(REPORTS))
    renderWithProviders(<LostReportsOverview />)

    // The row label is the first line of the description...
    await screen.findByText('Black leather wallet')
    // ...and the photo thumbnail loads inline, without any click to expand.
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
