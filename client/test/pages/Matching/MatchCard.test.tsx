import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
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
  contactStatusKnown?: boolean
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
      contactStatusKnown={opts.contactStatusKnown ?? true}
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

  it('hides the button until the contact status is known (loading/error)', () => {
    server.use(foundItemPhotoUrl())
    renderCard({ match: { combinedScore: 0.7 }, contactStatusKnown: false })

    // An already-contacted match must not show a stale "Reach out" button while
    // the contacts query is still resolving or has failed.
    expect(reachOutButton()).not.toBeInTheDocument()
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

  it('reaches out with the guest email and shows a queued state (not reached-out) until delivery confirms', async () => {
    const onBody = vi.fn()
    server.use(foundItemPhotoUrl(), publicMatchLinkCreate(onBody))
    const { user } = renderCard({ match: { combinedScore: 0.7 } })

    await user.click(reachOutButton()!)

    await waitFor(() => expect(onBody).toHaveBeenCalledWith(MATCH_ID, { email: EMAIL }))
    // The link request returning does not confirm the email was sent, so the card
    // shows a queued state — not "Guest reached out", which awaits a real sentAt.
    expect(await screen.findByText(/awaiting delivery/i)).toBeInTheDocument()
    expect(screen.queryByText(/guest reached out/i)).not.toBeInTheDocument()
    expect(reachOutButton()).not.toBeInTheDocument()
  })

  it('renders "reached out" only from a confirmed sentAt', () => {
    server.use(foundItemPhotoUrl())
    // contactedAt present (sentAt confirmed) is the only path to the indicator.
    renderCard({ match: { combinedScore: 0.7 }, contactedAt: '2026-06-30T09:00:00Z' })

    expect(screen.getByText(/guest reached out · /i)).toBeInTheDocument()
    expect(screen.queryByText(/awaiting delivery/i)).not.toBeInTheDocument()
  })

  it('surfaces a retry hint when the reach-out request fails', async () => {
    server.use(foundItemPhotoUrl(), publicMatchLinkCreateError())
    const { user } = renderCard({ match: { combinedScore: 0.7 } })

    await user.click(reachOutButton()!)

    expect(await screen.findByText(/couldn't send/i)).toBeInTheDocument()
    expect(reachOutButton()).toBeInTheDocument()
  })
})

const returnedButton = () => screen.queryByRole('button', { name: /mark as returned/i })
const confirmButton = () => screen.queryByRole('button', { name: /yes, returned/i })

describe('<MatchCard /> handover control', () => {
  afterEach(() => vi.restoreAllMocks())

  it('offers "mark as returned" only on a confirmed match', () => {
    server.use(foundItemPhotoUrl())
    const { rerender } = renderCard({ match: { status: MatchResponseStatus.PENDING } })
    expect(returnedButton()).not.toBeInTheDocument()

    rerender(
      <MatchCard
        match={{ id: MATCH_ID, foundItemId: 'fi-1', lostReportId: 'lr-1', status: MatchResponseStatus.CONFIRMED }}
        lostReport={lostReport}
        foundItem={foundItem}
        pickup={undefined}
        contactedAt={undefined}
        contactStatusKnown
      />,
    )
    expect(returnedButton()).toBeInTheDocument()
  })

  it('drives found→RETURNED, lost→COLLECTED, match→COMPLETED on confirm', async () => {
    const foundBody = vi.fn()
    const lostBody = vi.fn()
    const matchBody = vi.fn()
    server.use(
      foundItemPhotoUrl(),
      http.put('*/api/found-items/:id', async ({ request, params }) => {
        foundBody(params.id, await request.json())
        return HttpResponse.json({})
      }),
      http.put('*/api/lost-items/:id', async ({ request, params }) => {
        lostBody(params.id, await request.json())
        return HttpResponse.json({})
      }),
      http.put('*/api/matches/:id', async ({ request, params }) => {
        matchBody(params.id, await request.json())
        return HttpResponse.json({})
      }),
    )
    const { user } = renderCard({ match: { status: MatchResponseStatus.CONFIRMED } })

    await user.click(returnedButton()!)
    await user.click(confirmButton()!)

    await waitFor(() => expect(matchBody).toHaveBeenCalled())
    expect(foundBody).toHaveBeenCalledWith('fi-1', expect.objectContaining({ status: 'RETURNED' }))
    expect(lostBody).toHaveBeenCalledWith('lr-1', expect.objectContaining({ status: 'COLLECTED' }))
    expect(matchBody).toHaveBeenCalledWith(MATCH_ID, expect.objectContaining({ status: 'COMPLETED' }))
  })

  it('does nothing when the confirm step is cancelled', async () => {
    const foundBody = vi.fn()
    const lostBody = vi.fn()
    const matchBody = vi.fn()
    server.use(
      foundItemPhotoUrl(),
      http.put('*/api/found-items/:id', async () => {
        foundBody()
        return HttpResponse.json({})
      }),
      http.put('*/api/lost-items/:id', async () => {
        lostBody()
        return HttpResponse.json({})
      }),
      http.put('*/api/matches/:id', async () => {
        matchBody()
        return HttpResponse.json({})
      }),
    )
    const { user } = renderCard({ match: { status: MatchResponseStatus.CONFIRMED } })

    await user.click(returnedButton()!)
    await user.click(screen.getByRole('button', { name: /cancel/i }))

    expect(foundBody).not.toHaveBeenCalled()
    expect(lostBody).not.toHaveBeenCalled()
    expect(matchBody).not.toHaveBeenCalled()
  })
})
