import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  foundItemPhotoUrl,
  publicMatchLinkCreate,
  publicMatchLinkCreateError,
} from '@test/handlers'
import MatchCard from '@/pages/Matching/MatchCard'
import { MatchResponseStatus } from '@/api/matches/model'
import type { MatchResponse } from '@/api/matches/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import type { FoundItemResponse } from '@/api/found-items/model'

const MATCH_ID = 'm1111111-1111-1111-1111-111111111111'
const EMAIL = 'guest@example.test'

const foundItem: FoundItemResponse = { id: 'fi-1', attributes: { category: 'Found Wallet' } }
const lostReport: LostReportResponse = {
  id: 'lr-1',
  contactEmail: EMAIL,
  attributes: { category: 'Lost Wallet' },
}

function renderCard(opts: {
  match?: Partial<MatchResponse>
  lostReport?: LostReportResponse
  contactedAt?: string
} = {}) {
  const match: MatchResponse = {
    id: MATCH_ID,
    foundItemId: 'fi-1',
    lostReportId: 'lr-1',
    status: MatchResponseStatus.PENDING,
    combinedScore: 0.7,
    createdAt: '2026-06-03T09:00:00Z',
    ...opts.match,
  }
  return renderWithProviders(
    <MatchCard
      match={match}
      lostReport={opts.lostReport ?? lostReport}
      foundItem={foundItem}
      pickup={undefined}
      contactedAt={opts.contactedAt}
    />,
  )
}

const reachOutButton = () => screen.queryByRole('button', { name: /reach out to guest/i })

describe('<MatchCard /> reach-out control', () => {
  it('offers manual reach-out for a sub-threshold, pending, uncontacted match', () => {
    server.use(foundItemPhotoUrl())
    renderCard({ match: { combinedScore: 0.7 } })

    expect(reachOutButton()).toBeInTheDocument()
    expect(screen.queryByText(/guest reached out/i)).not.toBeInTheDocument()
  })

  it('hides the button at or above the auto-invite threshold', () => {
    server.use(foundItemPhotoUrl())
    renderCard({ match: { combinedScore: 0.9 } })

    expect(reachOutButton()).not.toBeInTheDocument()
    expect(screen.queryByText(/guest reached out/i)).not.toBeInTheDocument()
  })

  it('hides the button when the match is not pending', () => {
    server.use(foundItemPhotoUrl())
    renderCard({ match: { combinedScore: 0.7, status: MatchResponseStatus.CONFIRMED } })

    expect(reachOutButton()).not.toBeInTheDocument()
  })

  it('shows "reached out" (not the button) when the guest was already contacted, regardless of score', () => {
    server.use(foundItemPhotoUrl())
    renderCard({ match: { combinedScore: 0.7 }, contactedAt: '2026-06-30T09:00:00Z' })

    expect(reachOutButton()).not.toBeInTheDocument()
    expect(screen.getByText(/guest reached out/i)).toBeInTheDocument()
  })

  it('tells staff when there is no guest email to reach out to', () => {
    server.use(foundItemPhotoUrl())
    renderCard({
      match: { combinedScore: 0.7 },
      lostReport: { id: 'lr-1', attributes: { category: 'Lost Wallet' } },
    })

    expect(reachOutButton()).not.toBeInTheDocument()
    expect(screen.getByText(/no guest email on file/i)).toBeInTheDocument()
  })

  it('reaches out with the guest email and reflects the sent state', async () => {
    const onBody = vi.fn()
    server.use(foundItemPhotoUrl(), publicMatchLinkCreate(onBody))
    const { user } = renderCard({ match: { combinedScore: 0.7 } })

    await user.click(reachOutButton()!)

    await waitFor(() => expect(onBody).toHaveBeenCalledWith(MATCH_ID, { email: EMAIL }))
    expect(await screen.findByText(/guest reached out/i)).toBeInTheDocument()
    expect(reachOutButton()).not.toBeInTheDocument()
  })

  it('surfaces a retry hint when the reach-out request fails', async () => {
    server.use(foundItemPhotoUrl(), publicMatchLinkCreateError())
    const { user } = renderCard({ match: { combinedScore: 0.7 } })

    await user.click(reachOutButton()!)

    expect(await screen.findByText(/couldn't send/i)).toBeInTheDocument()
    expect(reachOutButton()).toBeInTheDocument()
  })
})
