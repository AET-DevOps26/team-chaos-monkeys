import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  foundItemDeleteSuccess,
  foundItemPhotoUrl,
  foundItemsList,
  foundItemsListError,
} from '@test/handlers'
import FoundItemsOverview from '@/pages/FoundItemsOverview/FoundItemsOverview'
import { FoundItemResponseStatus } from '@/api/found-items/model'
import type { FoundItemResponse } from '@/api/found-items/model'

const ITEMS: FoundItemResponse[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    description: 'Black wallet',
    foundAt: '2026-05-20T10:00:00Z',
    status: FoundItemResponseStatus.STORED,
    attributes: { category: 'Wallet' },
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    description: 'Blue umbrella',
    foundAt: '2026-05-21T10:00:00Z',
    status: FoundItemResponseStatus.RETURNED,
    attributes: { category: 'Umbrella' },
  },
]

describe('<FoundItemsOverview />', () => {
  it('renders the stored items returned by the API', async () => {
    server.use(foundItemsList(ITEMS), foundItemPhotoUrl())
    renderWithProviders(<FoundItemsOverview />)

    expect(await screen.findByText('Wallet')).toBeInTheDocument()
    // RETURNED item is filtered out by the default STORED tab.
    expect(screen.queryByText('Umbrella')).not.toBeInTheDocument()
  })

  it('switches the listing when a different status filter is selected', async () => {
    server.use(foundItemsList(ITEMS), foundItemPhotoUrl())
    const { user } = renderWithProviders(<FoundItemsOverview />)

    await screen.findByText('Wallet')
    await user.click(screen.getByRole('tab', { name: /returned/i }))

    expect(await screen.findByText('Umbrella')).toBeInTheDocument()
    expect(screen.queryByText('Wallet')).not.toBeInTheDocument()
  })

  it('shows the empty state and recovers via "Show all"', async () => {
    server.use(foundItemsList([]), foundItemPhotoUrl())
    const { user } = renderWithProviders(<FoundItemsOverview />)

    expect(
      await screen.findByText(/no found items match this filter/i),
    ).toBeInTheDocument()

    server.use(foundItemsList(ITEMS), foundItemPhotoUrl())
    await user.click(screen.getByRole('button', { name: /show all/i }))

    expect(await screen.findByText('Wallet')).toBeInTheDocument()
    expect(await screen.findByText('Umbrella')).toBeInTheDocument()
  })

  it('shows an error state with a retry button when the request fails', async () => {
    server.use(foundItemsListError())
    renderWithProviders(<FoundItemsOverview />)

    expect(await screen.findByText(/couldn't load found items/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
  })

  it('requires a second click to confirm delete, and a cancel X aborts the flow', async () => {
    server.use(
      foundItemsList(ITEMS),
      foundItemPhotoUrl(),
      foundItemDeleteSuccess(),
    )
    const { user } = renderWithProviders(<FoundItemsOverview />)

    const wallet = await screen.findByText('Wallet')
    const card = wallet.closest('article') as HTMLElement
    expect(card).not.toBeNull()

    // First click arms the confirmation; the item must still be present.
    await user.click(within(card).getByRole('button', { name: /^delete wallet$/i }))
    expect(within(card).getByRole('status')).toHaveTextContent(/are you sure/i)
    expect(within(card).getByText('Wallet')).toBeInTheDocument()

    // Cancel X disarms without deleting.
    await user.click(within(card).getByRole('button', { name: /cancel delete wallet/i }))
    expect(within(card).queryByRole('status')).not.toBeInTheDocument()
    expect(within(card).getByText('Wallet')).toBeInTheDocument()

    // Re-arming and confirming triggers deletion + refetch (server now returns empty).
    await user.click(within(card).getByRole('button', { name: /^delete wallet$/i }))
    server.use(foundItemsList([]))
    await user.click(within(card).getByRole('button', { name: /confirm delete wallet/i }))

    await waitFor(() =>
      expect(screen.queryByText('Wallet')).not.toBeInTheDocument(),
    )
  })
})
