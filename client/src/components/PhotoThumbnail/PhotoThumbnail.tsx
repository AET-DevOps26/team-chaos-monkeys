import { useState } from 'react'
import type { PhotoUrlResponse } from '@/api/found-items/model'

const PHOTO_URL_STALE_MS = 4 * 60 * 1000

/**
 * Structural shape of an Orval-generated `useGet<X>PhotoUrl` hook, narrowed to
 * the fields this component reads. The payload is the generated
 * `PhotoUrlResponse` model (identical across services, so the lost-items hook
 * is assignable too); only the react-query plumbing — which Orval does not
 * emit a reusable type for — is described by hand. Both
 * `useGetLostReportPhotoUrl` and `useGetFoundItemPhotoUrl` satisfy this.
 */
export type UsePhotoUrl = (
  id: string,
  options: { query: { staleTime: number; retry: number } },
) => {
  data: PhotoUrlResponse | undefined
  isLoading: boolean
  isError: boolean
  refetch: () => unknown
}

import electronicsIcon from '@/assets/category/electronics.svg'
import clothingIcon from '@/assets/category/clothing.svg'
import accessoriesIcon from '@/assets/category/accessories.svg'
import bagsIcon from '@/assets/category/bags.svg'
import documentsIcon from '@/assets/category/documents.svg'
import keysIcon from '@/assets/category/keys.svg'
import jewelryIcon from '@/assets/category/jewelry.svg'
import otherIcon from '@/assets/category/other.svg'
import placeholderIcon from '@/assets/category/placeholder.svg'

// genai Category taxonomy → icon. Lets a lost report with no photo show a
// category glyph instead of the generic camera placeholder; the icon is tinted
// via a CSS mask so it follows the theme. OTHER/unknown fall through to the
// camera placeholder.
const CATEGORY_ICONS: Record<string, string> = {
  ELECTRONICS: electronicsIcon,
  CLOTHING: clothingIcon,
  ACCESSORIES: accessoriesIcon,
  BAGS: bagsIcon,
  DOCUMENTS: documentsIcon,
  KEYS: keysIcon,
  JEWELRY: jewelryIcon,
  OTHER: otherIcon,
}

function PhotoPlaceholder({ label, category }: { label: string; category?: string }) {
  const icon =
    (category && CATEGORY_ICONS[category.toUpperCase()]) || placeholderIcon
  return (
    <div
      role="img"
      aria-label={label}
      className="flex h-full w-full items-center justify-center bg-border/40"
    >
      <span
        aria-hidden="true"
        className="h-9 w-9 bg-text opacity-40"
        style={{
          maskImage: `url("${icon}")`,
          WebkitMaskImage: `url("${icon}")`,
          maskSize: 'contain',
          WebkitMaskSize: 'contain',
          maskRepeat: 'no-repeat',
          WebkitMaskRepeat: 'no-repeat',
          maskPosition: 'center',
          WebkitMaskPosition: 'center',
        }}
      />
    </div>
  )
}

function PhotoThumbnailInner({
  id,
  alt,
  usePhotoUrl,
  category,
}: {
  id: string
  alt: string
  usePhotoUrl: UsePhotoUrl
  category?: string
}) {
  const { data, isLoading, isError, refetch } = usePhotoUrl(id, {
    query: {
      staleTime: PHOTO_URL_STALE_MS,
      retry: 1,
    },
  })

  // Retry the URL fetch at most once: if a freshly issued URL fails to load,
  // refetch in case the server can issue a working one; if the replacement
  // also fails, fall through to the placeholder instead of looping. The retry
  // budget is keyed on `id` (the photo subject) rather than the URL string, so
  // backends that mint a unique signed URL per request can't reset the guard
  // and refetch forever — the refetched URL is a new link to the *same* photo,
  // not a new attempt. The budget and the "gave up" flag are reset during
  // render when the component is pointed at a different `id` (React's
  // documented prop-derived-state pattern — no effect, no ref).
  const [retried, setRetried] = useState(false)
  const [failed, setFailed] = useState(false)
  const [trackedId, setTrackedId] = useState(id)
  if (id !== trackedId) {
    setTrackedId(id)
    setRetried(false)
    setFailed(false)
  }

  const url = data?.url

  if (isLoading) {
    return (
      <div
        className="h-full w-full animate-pulse bg-border/40"
        aria-label={`Loading ${alt}`}
      />
    )
  }
  if (isError || !url || failed)
    return <PhotoPlaceholder label={alt} category={category} />

  return (
    <img
      src={url}
      alt={alt}
      loading="lazy"
      className="h-full w-full object-cover"
      onError={() => {
        if (retried) {
          setFailed(true)
          return
        }
        setRetried(true)
        refetch()
      }}
    />
  )
}

export default function PhotoThumbnail({
  id,
  alt,
  usePhotoUrl,
  category,
}: {
  id: string | undefined
  alt: string
  usePhotoUrl: UsePhotoUrl
  category?: string
}) {
  if (!id) return <PhotoPlaceholder label={alt} category={category} />
  return (
    <PhotoThumbnailInner
      id={id}
      alt={alt}
      usePhotoUrl={usePhotoUrl}
      category={category}
    />
  )
}
