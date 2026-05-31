import { describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import PhotoThumbnail, {
  type UsePhotoUrl,
} from '@/components/PhotoThumbnail/PhotoThumbnail'

// A static hook stub: always resolves to the same URL, never errors.
const staticUrl =
  (url: string): UsePhotoUrl =>
  () => ({ data: { url }, isLoading: false, isError: false, refetch: () => {} })

describe('<PhotoThumbnail />', () => {
  it('renders a placeholder and never calls the hook when no id is given', () => {
    const usePhotoUrl = vi.fn(staticUrl('u1'))
    render(<PhotoThumbnail id={undefined} alt="Wallet" usePhotoUrl={usePhotoUrl} />)

    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull() // placeholder, not an <img>
    expect(usePhotoUrl).not.toHaveBeenCalled()
  })

  it('renders the photo once a URL is available', () => {
    render(<PhotoThumbnail id="x" alt="Wallet" usePhotoUrl={staticUrl('u1')} />)

    const img = screen.getByRole('img', { name: 'Wallet' })
    expect(img.tagName).toBe('IMG')
    expect(img).toHaveAttribute('src', 'u1')
  })

  // Regression guard for the refetch loop: a backend that mints a fresh signed
  // URL on every request must not be able to keep the component retrying. The
  // retry budget is per-id, so a persistently-broken photo retries exactly once
  // and then falls back to the placeholder.
  it('retries at most once then gives up, even when each refetch yields a new URL', () => {
    const onRefetch = vi.fn()
    const urls = ['u1', 'u2', 'u3']
    const useFreshUrlEachRefetch: UsePhotoUrl = () => {
      const [idx, setIdx] = useState(0)
      return {
        data: { url: urls[Math.min(idx, urls.length - 1)] },
        isLoading: false,
        isError: false,
        refetch: () => {
          onRefetch()
          setIdx((n) => n + 1)
        },
      }
    }

    render(<PhotoThumbnail id="x" alt="Wallet" usePhotoUrl={useFreshUrlEachRefetch} />)

    // First signed URL fails -> one refetch, the freshly issued URL is shown.
    fireEvent.error(screen.getByRole('img', { name: 'Wallet' }))
    expect(onRefetch).toHaveBeenCalledTimes(1)
    const retryImg = screen.getByRole('img', { name: 'Wallet' })
    expect(retryImg.tagName).toBe('IMG')
    expect(retryImg).toHaveAttribute('src', 'u2')

    // The replacement URL also fails -> give up. Crucially, NO second refetch,
    // even though the URL changed between attempts.
    fireEvent.error(retryImg)
    expect(onRefetch).toHaveBeenCalledTimes(1)
    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByRole('img', { name: 'Wallet' })).toBeInTheDocument()
  })
})
