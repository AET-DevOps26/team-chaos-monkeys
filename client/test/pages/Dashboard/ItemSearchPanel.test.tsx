import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import { itemSearchSuccess, itemSearchError } from '@test/handlers'
import { makeFakeJwt } from '@test/jwt'
import type { ItemSearchResponse } from '@/api/matches/model'
import ItemSearchPanel from '@/pages/Dashboard/ItemSearchPanel'

const FAKE_JWT = makeFakeJwt()

function renderPanel() {
  return renderWithProviders(<ItemSearchPanel />, {
    route: '/',
    authToken: FAKE_JWT,
  })
}

async function ask(query: string) {
  const { user } = renderPanel()
  await user.type(screen.getByLabelText(/search query/i), query)
  await user.click(screen.getByRole('button', { name: /^search$/i }))
  return user
}

describe('<ItemSearchPanel />', () => {
  it('renders a grounded answer and shows only cited result cards', async () => {
    const response: ItemSearchResponse = {
      answer: 'A black leather wallet was handed in at the lobby.',
      grounded: true,
      citations: ['found-1'],
      results: [
        { id: 'found-1', itemType: 'FOUND', category: 'Wallet', text: 'Black leather wallet', distance: 0.1 },
        { id: 'found-2', itemType: 'FOUND', category: 'Bag', text: 'Blue backpack', distance: 0.6 },
      ],
    }
    server.use(itemSearchSuccess(response))
    await ask('black wallet')

    expect(
      await screen.findByText(/black leather wallet was handed in/i),
    ).toBeInTheDocument()

    // Only the cited item (found-1) is rendered; the uncited neighbour is dropped.
    const cards = screen.getAllByTestId('search-result-card')
    expect(cards).toHaveLength(1)
    expect(within(cards[0]).getByText('Wallet')).toBeInTheDocument()
    expect(screen.queryByText(/blue backpack/i)).not.toBeInTheDocument()
  })

  it('shows a "summary unavailable" note with cited results in degraded mode', async () => {
    const response: ItemSearchResponse = {
      answer: undefined, // genai synthesis unavailable -> degraded
      grounded: true,
      citations: ['lost-1'],
      results: [
        { id: 'lost-1', itemType: 'LOST', category: 'Phone', text: 'Lost phone', distance: 0.3 },
      ],
    }
    server.use(itemSearchSuccess(response))
    await ask('phone')

    expect(await screen.findByText(/summary unavailable/i)).toBeInTheDocument()
    expect(screen.getByTestId('search-result-card')).toBeInTheDocument()
  })

  it('hides result cards when the answer is ungrounded', async () => {
    const response: ItemSearchResponse = {
      answer: 'I could not find a confident match.',
      grounded: false,
      citations: [],
      results: [
        { id: 'lost-9', itemType: 'LOST', category: 'Umbrella', text: 'Blue umbrella', distance: 0.78 },
      ],
    }
    server.use(itemSearchSuccess(response))
    await ask('spaceship')

    expect(await screen.findByText(/no matching items found/i)).toBeInTheDocument()
    expect(screen.queryByTestId('search-result-card')).not.toBeInTheDocument()
  })

  it('shows a "no matching items" message for an empty result set', async () => {
    const response: ItemSearchResponse = {
      answer: undefined,
      grounded: false,
      citations: [],
      results: [],
    }
    server.use(itemSearchSuccess(response))
    await ask('unicorn')

    expect(await screen.findByText(/no matching items found/i)).toBeInTheDocument()
    expect(screen.queryByTestId('search-result-card')).not.toBeInTheDocument()
  })

  it('shows an error message when the search request fails', async () => {
    server.use(itemSearchError())
    await ask('anything')

    expect(await screen.findByText(/search failed/i)).toBeInTheDocument()
  })
})
