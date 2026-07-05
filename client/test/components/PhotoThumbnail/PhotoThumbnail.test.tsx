import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { axiosInstance } from '@/api/mutator/custom-instance'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'

vi.mock('@/api/mutator/custom-instance', () => ({
  axiosInstance: {
    get: vi.fn(),
  },
}))

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
    render(<PhotoThumbnail src={undefined} alt="Wallet" />)

    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull()
  })

  it('renders the photo when a URL is available', async () => {
    getPhoto.mockResolvedValue({ data: new Blob(['photo'], { type: 'image/jpeg' }) })

    render(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

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

    render(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
    fireEvent.error(screen.getByRole('img', { name: 'Wallet' }))

    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
  })
})
