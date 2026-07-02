import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'
import { setCurrentToken } from '@/auth/token-store'
import { server } from '@test/server'

beforeEach(() => {
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
  it('renders a placeholder when no URL is given', () => {
    render(<PhotoThumbnail src={undefined} alt="Wallet" />)

    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull()
  })

  it('renders the photo when a URL is available', async () => {
    let authorization: string | null = null
    server.use(
      http.get('*/api/found-items/x/photo', ({ request }) => {
        authorization = request.headers.get('authorization')
        return new HttpResponse(null, {
          headers: { 'Content-Type': 'image/jpeg' },
        })
      }),
    )
    setCurrentToken('staff-token')

    render(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
    const img = screen.getByRole('img', { name: 'Wallet' })
    expect(img.tagName).toBe('IMG')
    expect(img).toHaveAttribute('src', 'blob:photo')
    expect(authorization).toBe('Bearer staff-token')
  })

  it('falls back to the placeholder when the image fails to load', async () => {
    server.use(
      http.get('*/api/found-items/x/photo', () =>
        new HttpResponse(null, {
          headers: { 'Content-Type': 'image/jpeg' },
        }),
      ),
    )
    render(<PhotoThumbnail src="/api/found-items/x/photo" alt="Wallet" />)

    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'Wallet' }).tagName).toBe('IMG'),
    )
    fireEvent.error(screen.getByRole('img', { name: 'Wallet' }))

    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
  })
})
