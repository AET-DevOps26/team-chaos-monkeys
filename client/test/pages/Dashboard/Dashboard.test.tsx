import { describe, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@test/server'
import { renderWithProviders } from '@test/render'
import { makeFakeJwt } from '@test/jwt'
import Dashboard from '@/pages/Dashboard'

// Recharts' ResponsiveContainer needs real layout (unavailable in jsdom); render
// a plain wrapper so the page mounts without measuring. We assert on the page
// chrome (cards, picker, granularity), not on SVG internals.
vi.mock('recharts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('recharts')>()
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div>{children}</div>
    ),
  }
})

const emptyHistogram = { perDay: [], perWeek: [], perMonth: [] }

function mockHistograms() {
  server.use(
    http.get('*/api/found-items/histogram', () =>
      HttpResponse.json(emptyHistogram),
    ),
    http.get('*/api/lost-items/histogram', () =>
      HttpResponse.json(emptyHistogram),
    ),
    http.get('*/api/matches/histogram', () =>
      HttpResponse.json(emptyHistogram),
    ),
  )
}

describe('Dashboard', () => {
  it('renders venue-scoped KPI cards for staff without a venue picker', async () => {
    server.use(
      http.get('*/api/venues/kpis', () =>
        HttpResponse.json({
          venueId: 'venue-1',
          totalFoundItems: 12,
          totalLostItems: 8,
          totalMatches: 5,
          pendingMatches: 2,
        }),
      ),
    )
    mockHistograms()

    renderWithProviders(<Dashboard />, {
      authToken: makeFakeJwt({ sub: 'staff@foundflow.io', roles: ['STAFF'], venue_id: 'venue-1' }),
    })

    await waitFor(() => expect(screen.getByText('12')).toBeInTheDocument())
    expect(screen.getByText('8')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.queryByText('Venue')).not.toBeInTheDocument()
  })

  it('shows a venue picker for admins and re-scopes KPIs on selection', async () => {
    server.use(
      http.get('*/api/venues', () =>
        HttpResponse.json([
          { id: 'venue-1', name: 'Main Hall' },
          { id: 'venue-2', name: 'East Wing' },
        ]),
      ),
      http.get('*/api/venues/kpis', () =>
        HttpResponse.json({
          venueId: null,
          totalFoundItems: 100,
          totalLostItems: 50,
          totalMatches: 20,
          pendingMatches: 7,
        }),
      ),
      http.get('*/api/venues/kpis/:id', () =>
        HttpResponse.json({
          venueId: 'venue-2',
          totalFoundItems: 3,
          totalLostItems: 4,
          totalMatches: 1,
          pendingMatches: 0,
        }),
      ),
    )
    mockHistograms()

    const { user } = renderWithProviders(<Dashboard />, {
      authToken: makeFakeJwt({ sub: 'admin@foundflow.io', roles: ['ADMIN'] }),
    })

    // Global totals first.
    await waitFor(() => expect(screen.getByText('100')).toBeInTheDocument())

    // Select a specific venue -> by-id endpoint.
    await user.selectOptions(
      screen.getByRole('combobox'),
      'East Wing',
    )
    await waitFor(() => expect(screen.getByText('3')).toBeInTheDocument())
  })

  it('toggles trend granularity', async () => {
    server.use(
      http.get('*/api/venues/kpis', () =>
        HttpResponse.json({
          venueId: 'venue-1',
          totalFoundItems: 1,
          totalLostItems: 1,
          totalMatches: 1,
          pendingMatches: 1,
        }),
      ),
    )
    mockHistograms()

    const { user } = renderWithProviders(<Dashboard />, {
      authToken: makeFakeJwt({ sub: 'staff@foundflow.io', roles: ['STAFF'], venue_id: 'venue-1' }),
    })

    const daily = await screen.findByRole('button', { name: 'Daily' })
    const weekly = screen.getByRole('button', { name: 'Weekly' })
    expect(daily).toHaveAttribute('aria-pressed', 'true')

    await user.click(weekly)
    expect(weekly).toHaveAttribute('aria-pressed', 'true')
    expect(daily).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows an error state with retry when KPIs fail', async () => {
    server.use(
      http.get('*/api/venues/kpis', () => new HttpResponse(null, { status: 500 })),
    )
    mockHistograms()

    renderWithProviders(<Dashboard />, {
      authToken: makeFakeJwt({ sub: 'staff@foundflow.io', roles: ['STAFF'], venue_id: 'venue-1' }),
    })

    await waitFor(() =>
      expect(
        screen.getByText('Couldn’t load dashboard metrics.'),
      ).toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
