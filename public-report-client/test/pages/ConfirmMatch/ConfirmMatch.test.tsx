import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import AppRoutes from '@/routes'
import type { MatchResponse, PublicFoundItemResponse } from '@/api/matches/model'

const TOKEN = 'match-view-token'
const route = `/match/${TOKEN}`

const FOUND_ITEM: PublicFoundItemResponse = {
  id: '22222222-2222-2222-2222-222222222222',
  description: 'Black leather wallet',
  foundAt: '2026-05-21T09:00:00Z',
  locationHint: 'Lobby reception',
  status: 'STORED',
  attributes: { category: 'wallet', brand: 'Fossil', color: 'black', marks: ['monogram AK'] },
}

const match = (status: MatchResponse['status']): MatchResponse => ({
  id: '33333333-3333-3333-3333-333333333333',
  status,
})

function matchHandlers(opts: {
  status?: MatchResponse['status']
  foundItemStatus?: number
  onConfirm?: () => void
  onReject?: () => void
} = {}) {
  const { status = 'PENDING', foundItemStatus = 200, onConfirm, onReject } = opts
  return [
    http.get(`*/api/matches/public/${TOKEN}/found-item`, () =>
      foundItemStatus === 200
        ? HttpResponse.json(FOUND_ITEM)
        : HttpResponse.json({ message: 'gone' }, { status: foundItemStatus }),
    ),
    http.get(`*/api/matches/public/${TOKEN}`, () => HttpResponse.json(match(status))),
    http.put('*/api/matches/public/match-links/:token/confirm', () => {
      onConfirm?.()
      return HttpResponse.json(match('CONFIRMED'))
    }),
    http.put('*/api/matches/public/match-links/:token/reject', () => {
      onReject?.()
      return HttpResponse.json(match('REJECTED'))
    }),
    // SchedulePickup fetches slots after a confirm navigates there.
    http.get('*/api/pickups/public/*', () => HttpResponse.json([])),
  ]
}

describe('<ConfirmMatch />', () => {
  it('renders the found item and both actions for a pending match', async () => {
    server.use(...matchHandlers())
    renderWithProviders(<AppRoutes />, { route })

    expect(await screen.findByText('Black leather wallet')).toBeInTheDocument()
    expect(screen.getByText('Lobby reception')).toBeInTheDocument()
    expect(screen.getByText('monogram AK')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /that's mine/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /not mine/i })).toBeInTheDocument()
  })

  it('rejects the match and shows an acknowledgement', async () => {
    let rejected = false
    server.use(...matchHandlers({ onReject: () => (rejected = true) }))
    const { user } = renderWithProviders(<AppRoutes />, { route })

    await user.click(await screen.findByRole('button', { name: /not mine/i }))

    expect(await screen.findByText(/match rejected/i)).toBeInTheDocument()
    await waitFor(() => expect(rejected).toBe(true))
  })

  it('confirms the match and navigates into the pickup flow', async () => {
    let confirmed = false
    server.use(...matchHandlers({ onConfirm: () => (confirmed = true) }))
    const { user } = renderWithProviders(<AppRoutes />, { route })

    await user.click(await screen.findByRole('button', { name: /that's mine/i }))

    await waitFor(() => expect(confirmed).toBe(true))
    expect(await screen.findByRole('heading', { name: /schedule pickup/i })).toBeInTheDocument()
  })

  it('shows an expired-link error when the token is invalid', async () => {
    server.use(...matchHandlers({ foundItemStatus: 404 }))
    renderWithProviders(<AppRoutes />, { route })

    expect(await screen.findByText(/invalid or has expired/i)).toBeInTheDocument()
  })

  it('offers to schedule pickup when the match is already confirmed', async () => {
    server.use(...matchHandlers({ status: 'CONFIRMED' }))
    renderWithProviders(<AppRoutes />, { route })

    expect(await screen.findByText(/already confirmed/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /schedule pickup/i })).toBeInTheDocument()
  })
})
