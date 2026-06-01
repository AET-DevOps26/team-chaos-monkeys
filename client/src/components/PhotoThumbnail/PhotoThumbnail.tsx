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

function PhotoPlaceholder({ label }: { label: string }) {
  return (
    <div
      role="img"
      aria-label={label}
      className="flex h-full w-full items-center justify-center bg-border/40"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-8 w-8 text-text opacity-40"
        aria-hidden="true"
      >
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <circle cx="9" cy="11" r="1.5" />
        <path d="M21 17l-5-5-9 9" />
      </svg>
    </div>
  )
}

function PhotoThumbnailInner({
  id,
  alt,
  usePhotoUrl,
}: {
  id: string
  alt: string
  usePhotoUrl: UsePhotoUrl
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
  if (isError || !url || failed) return <PhotoPlaceholder label={alt} />

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
}: {
  id: string | undefined
  alt: string
  usePhotoUrl: UsePhotoUrl
}) {
  if (!id) return <PhotoPlaceholder label={alt} />
  return <PhotoThumbnailInner id={id} alt={alt} usePhotoUrl={usePhotoUrl} />
}
