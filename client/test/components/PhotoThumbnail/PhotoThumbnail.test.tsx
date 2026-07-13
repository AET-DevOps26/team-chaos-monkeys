import type { PropsWithChildren, ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { axiosInstance } from '@/api/mutator/custom-instance'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'

vi.mock('@/api/mutator/custom-instance', () => ({
  axiosInstance: {
    get: vi.fn(),
  },
}))

// Fresh client per render (as a wrapper, so rerender keeps the provider) —
// the blob cache never bleeds between assertions.
function renderWithClient(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return render(ui, { wrapper })
}

beforeEach(() => {
  vi.clearAllMocks()
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:photo'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  })
})

describe('<PhotoThumbnail />', () => {
  const getPhoto = vi.mocked(axiosInstance.get)

  it('renders a placeholder when no URL is given', () => {
    renderWithClient(<PhotoThumbnail src={undefined} alt="Wallet" />)

    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull()
  })

  it('renders the photo when a URL is available', async () => {
    getPhoto.mockResolvedValue({ data: new Blob(['photo'], { type: 'image/jpeg' }) })

    renderWithClient(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
    const img = screen.getByRole('img', { name: 'Wallet' })
    expect(img.tagName).toBe('IMG')
    expect(img).toHaveAttribute('src', 'blob:photo')
    expect(getPhoto).toHaveBeenCalledWith('/api/found-items/x/photo', {
      responseType: 'blob',
      signal: expect.any(AbortSignal),
    })
  })

  it('falls back to the placeholder when the image fails to load', async () => {
    getPhoto.mockResolvedValue({ data: new Blob(['photo'], { type: 'image/jpeg' }) })

    renderWithClient(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
    fireEvent.error(screen.getByRole('img', { name: 'Wallet' }))

    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
  })

  it('clears a previous failed image state after the same URL loads successfully later', async () => {
    getPhoto.mockResolvedValue({ data: new Blob(['photo'], { type: 'image/jpeg' }) })

    const { rerender } = renderWithClient(
      <PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />,
    )

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
    fireEvent.error(screen.getByRole('img', { name: 'Wallet' }))
    expect(document.querySelector('img')).toBeNull()

    rerender(<PhotoThumbnail src="/api/found-items/y/photo" alt="Wallet" />)
    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )

    rerender(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
  })
})
