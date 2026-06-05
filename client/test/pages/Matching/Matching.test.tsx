import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  foundItemById,
  foundItemPhotoUrl,
  lostReportById,
  lostReportPhotoUrl,
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
  // FI1 has a photo; FI2 does not (exercises both the photo and no-photo tile).
  { id: FI1, photoKey: 'found/fi1.jpg', attributes: { category: 'Found Wallet' } },
  { id: FI2, attributes: { category: 'Found Umbrella' } },
]

const LOST: LostReportResponse[] = [
  {
    id: LR1,
    description: 'Black leather bifold with a brass clasp',
    location: 'Lobby',
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
    foundItemById(FOUND),
    lostReportById(LOST),
    foundItemPhotoUrl(),
    lostReportPhotoUrl(),
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
    expect(within(card).getByText(/reported: lobby/i)).toBeInTheDocument()
  })

  it('shows the found photo when present and a "No photo" tile when absent', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    const withPhoto = (await screen.findByText('Found Wallet')).closest(
      'article',
    ) as HTMLElement
    const withoutPhoto = screen
      .getByText('Found Umbrella')
      .closest('article') as HTMLElement

    // FI1 has a photoKey → the photo URL is requested and rendered as an <img>.
    expect(await within(withPhoto).findByRole('img', { name: 'Found Wallet' })).toBeInTheDocument()
    // FI2 has no photoKey → no fetch, a labelled "No photo" tile instead.
    expect(within(withoutPhoto).getByText(/no photo/i)).toBeInTheDocument()
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
      within(withPickup).getByText((t) => t.startsWith('Pickup ·')),
    ).toBeInTheDocument()
    expect(
      within(withoutPickup).getByText(/no pickup scheduled/i),
    ).toBeInTheDocument()
    expect(container.querySelectorAll('article')).toHaveLength(2)
  })

  it('shows the guest email on a scheduled pickup', async () => {
    seedSuccess()
    renderWithProviders(<Matching />)

    const withPickup = (await screen.findByText('Found Wallet')).closest(
      'article',
    ) as HTMLElement
    expect(
      within(withPickup).getByText('guest@example.test'),
    ).toBeInTheDocument()
    expect(
      within(withPickup).queryByRole('link', { name: /guest@example\.test/i }),
    ).not.toBeInTheDocument()
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

  it('filters by status when a tab is selected', async () => {
    seedSuccess()
    const { user } = renderWithProviders(<Matching />)

    await screen.findByText('Found Wallet')
    await user.click(screen.getByRole('tab', { name: /confirmed/i }))

    // Server filters to CONFIRMED (M2) only; the PENDING match drops out.
    expect(await screen.findByText('Found Umbrella')).toBeInTheDocument()
    expect(screen.queryByText('Found Wallet')).not.toBeInTheDocument()
  })

  it('shows the empty state and recovers via "Show all"', async () => {
    server.use(matchesList([]), pickupsList([]))
    const { user } = renderWithProviders(<Matching />)

    // Default tab is "All", so empty here means no matches at all.
    expect(await screen.findByText(/no matches yet/i)).toBeInTheDocument()

    // Switch to a status tab, confirm its empty copy, then recover.
    await user.click(screen.getByRole('tab', { name: /pending/i }))
    expect(
      await screen.findByText(/no matches with this status/i),
    ).toBeInTheDocument()

    seedSuccess()
    await user.click(screen.getByRole('button', { name: /show all/i }))
    expect(await screen.findByText('Found Wallet')).toBeInTheDocument()
  })

  it('shows an error state with a retry button when the request fails', async () => {
    server.use(matchesListError(), pickupsList([]))
    renderWithProviders(<Matching />)

    expect(await screen.findByText(/couldn't load matches/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
  })
})
