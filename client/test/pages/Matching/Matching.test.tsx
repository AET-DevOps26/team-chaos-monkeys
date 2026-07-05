import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  foundItemsList,
  lostReportsList,
  matchesList,
  matchesListError,
  pickupsList,
} from '@test/handlers'
import Matching from '@/pages/Matching/Matching'
import { MatchResponseStatus } from '@/api/matches/model'
import type { MatchResponse } from '@/api/matches/model'
import type { PickupResponse } from '@/api/pickups/model'
import type { FoundItemResponse } from '@/api/found-items/model'
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

const M1 = 'a1111111-1111-1111-1111-111111111111'
const M2 = 'b2222222-2222-2222-2222-222222222222'
const FI1 = 'f1111111-1111-1111-1111-111111111111'
const FI2 = 'f2222222-2222-2222-2222-222222222222'
const LR1 = 'c1111111-1111-1111-1111-111111111111'
const LR2 = 'c2222222-2222-2222-2222-222222222222'

// Array order is intentionally opposite to createdAt order: M2 is listed first
// but has the older timestamp, so a correct newest-first sort renders M1 first.
const MATCHES: MatchResponse[] = [
  {
    id: M2,
    foundItemId: FI2,
    lostReportId: LR2,
    status: MatchResponseStatus.CONFIRMED,
    combinedScore: 0.61,
    createdAt: '2026-06-01T09:00:00Z',
  },
  {
    id: M1,
    foundItemId: FI1,
    lostReportId: LR1,
    status: MatchResponseStatus.PENDING,
    combinedScore: 0.82,
    createdAt: '2026-06-03T09:00:00Z',
  },
]

const FOUND: FoundItemResponse[] = [
  {
    id: FI1,
    photoKey: 'found/fi1.jpg',
    photoUrl: `/api/found-items/${FI1}/photo`,
    attributes: { category: 'Found Wallet' },
  },
  {
    id: FI2,
    photoKey: 'found/fi2.jpg',
    photoUrl: `/api/found-items/${FI2}/photo`,
    attributes: { category: 'Found Umbrella' },
  },
]

const LOST: LostReportResponse[] = [
  {
    id: LR1,
    description: 'Black leather bifold with a brass clasp',
    location: 'Lobby',
    contactEmail: 'reporter@example.test',
    attributes: { category: 'Lost Purse', color: 'Black', brand: 'Fossil', marks: ['Monogram AG'] },
  },
  { id: LR2, attributes: { category: 'Lost Parasol' } },
]

// Pickup scheduled for M1 only.
const PICKUPS: PickupResponse[] = [
  {
    id: 'd1111111-1111-1111-1111-111111111111',
    matchId: M1,
    pickupAt: '2026-06-05T14:30:00Z',
    email: 'guest@example.test',
  },
]

function seedSuccess(matches = MATCHES, pickups = PICKUPS) {
  server.use(
    matchesList(matches),
    pickupsList(pickups),
    foundItemsList(FOUND),
    lostReportsList(LOST),
  )
}

describe('<Matching />', () => {
  it('renders enriched lost↔found labels for each match', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    expect(await screen.findByText('Found Wallet')).toBeInTheDocument()
    expect(screen.getByText('Lost Purse')).toBeInTheDocument()
    expect(screen.getByText('Found Umbrella')).toBeInTheDocument()
    expect(screen.getByText('Lost Parasol')).toBeInTheDocument()
  })

  it('renders the lost claim description and attribute chips', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    const card = (await screen.findByText('Lost Purse')).closest(
      'article',
    ) as HTMLElement
    expect(
      within(card).getByText('Black leather bifold with a brass clasp'),
    ).toBeInTheDocument()
    expect(within(card).getByText('Black')).toBeInTheDocument()
    expect(within(card).getByText('Fossil')).toBeInTheDocument()
    expect(within(card).getByText('Monogram AG')).toBeInTheDocument()
    expect(within(card).getByText('Lobby')).toBeInTheDocument()
  })

  it('renders the found photo for each match', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    const withPhoto = (await screen.findByText('Found Wallet')).closest(
      'article',
    ) as HTMLElement
    // The found item's proxy photo URL is rendered directly as an <img>.
    expect(await within(withPhoto).findByRole('img', { name: 'Found Wallet' })).toBeInTheDocument()
  })

  it('shows the combined score as a percentage', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    expect(await screen.findByText('82%')).toBeInTheDocument()
    expect(screen.getByText('61%')).toBeInTheDocument()
  })

  it('badges a scheduled pickup and flags matches without one', async () => {
    seedSuccess()
    const { container } = renderWithProviders(<Matching />)

    const withPickup = (await screen.findByText('Found Wallet')).closest(
      'article',
    ) as HTMLElement
    const withoutPickup = screen
      .getByText('Found Umbrella')
      .closest('article') as HTMLElement

    expect(
      within(withPickup).getByText(/pickup scheduled at/i),
    ).toBeInTheDocument()
    expect(
      within(withoutPickup).getByText(/no pickup scheduled yet/i),
    ).toBeInTheDocument()
    expect(container.querySelectorAll('article')).toHaveLength(2)
  })

  it('shows the lost reporter email as a mailto link on the card', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    const card = (await screen.findByText('Lost Purse')).closest(
      'article',
    ) as HTMLElement
    const link = within(card).getByRole('link', {
      name: 'reporter@example.test',
    })
    expect(link).toHaveAttribute('href', 'mailto:reporter@example.test')
  })

  it('orders matches newest-first regardless of API order', async () => {
    seedSuccess()
    const { container } = renderWithProviders(<Matching />)

    await screen.findByText('Found Wallet')
    const articles = container.querySelectorAll('article')
    // M1 (newer) must come before M2 (older).
    expect(articles[0]).toHaveTextContent('Found Wallet')
    expect(articles[1]).toHaveTextContent('Found Umbrella')
  })

  it('filters by status via the status tabs', async () => {
    seedSuccess()
    const { user } = renderWithProviders(<Matching />)

    await screen.findByText('Found Wallet')
    await user.click(screen.getByRole('tab', { name: /confirmed/i }))

    // Server filters to CONFIRMED (M2) only; the PENDING match drops out.
    expect(await screen.findByText('Found Umbrella')).toBeInTheDocument()
    expect(screen.queryByText('Found Wallet')).not.toBeInTheDocument()
  })

  it('filters cards client-side by the search query', async () => {
    seedSuccess()
    const { user } = renderWithProviders(<Matching />)

    await screen.findByText('Found Wallet')
    await user.type(screen.getByRole('searchbox', { name: /search matches/i }), 'umbrella')

    expect(await screen.findByText('Found Umbrella')).toBeInTheDocument()
    expect(screen.queryByText('Found Wallet')).not.toBeInTheDocument()
  })

  it('shows a no-results state for a search that matches nothing', async () => {
    seedSuccess()
    const { user } = renderWithProviders(<Matching />)

    await screen.findByText('Found Wallet')
    await user.type(screen.getByRole('searchbox', { name: /search matches/i }), 'zzzznope')

    expect(await screen.findByText(/no matches for/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /clear search/i }))
    expect(await screen.findByText('Found Wallet')).toBeInTheDocument()
  })

  it('shows the empty state and recovers via "Show all"', async () => {
    server.use(
      matchesList([]),
      pickupsList([]),
      foundItemsList(FOUND),
      lostReportsList(LOST),
    )
    const { user } = renderWithProviders(<Matching />)

    // Default filter is "All", so empty here means no matches at all.
    expect(await screen.findByText(/no matches yet/i)).toBeInTheDocument()

    // Switch to a status filter, confirm its empty copy, then recover.
    await user.click(screen.getByRole('tab', { name: /pending/i }))
    expect(
      await screen.findByText(/no matches with this status/i),
    ).toBeInTheDocument()

    seedSuccess()
    await user.click(screen.getByRole('button', { name: /show all/i }))
    expect(await screen.findByText('Found Wallet')).toBeInTheDocument()
  })

  it('shows an error state with a retry button when the request fails', async () => {
    server.use(
      matchesListError(),
      pickupsList([]),
      foundItemsList([]),
      lostReportsList([]),
    )
    renderWithProviders(<Matching />)

    expect(await screen.findByText(/couldn't load matches/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
  })
})
