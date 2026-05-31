import { useEffect, useState } from 'react'
import { useGetLostReportPhotoUrl } from '@/api/lost-items/lost-report-controller/lost-report-controller'

const PHOTO_URL_STALE_MS = 4 * 60 * 1000

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

function LostReportPhotoInner({ id, alt }: { id: string; alt: string }) {
  const { data, isLoading, isError, refetch } = useGetLostReportPhotoUrl(id, {
    query: {
      staleTime: PHOTO_URL_STALE_MS,
      retry: 1,
    },
  })

  // Retry the URL fetch at most once per signed URL: if a freshly issued URL
  // fails to load, refetch in case the server can issue a new one; if the
  // replacement also fails, fall through to the placeholder instead of looping.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const [retriedUrl, setRetriedUrl] = useState<string | null>(null)

  const url = data?.url
  useEffect(() => {
    if (url) setFailedUrl((prev) => (prev === url ? prev : null))
  }, [url])

  if (isLoading) {
    return <div className="h-full w-full animate-pulse bg-border/40" aria-label={`Loading ${alt}`} />
  }
  if (isError || !url) return <PhotoPlaceholder label={alt} />
  if (failedUrl === url) return <PhotoPlaceholder label={alt} />

  return (
    <img
      src={url}
      alt={alt}
      loading="lazy"
      className="h-full w-full object-cover"
      onError={() => {
        if (retriedUrl === url) {
          setFailedUrl(url)
          return
        }
        setRetriedUrl(url)
        refetch()
      }}
    />
  )
}

export default function LostReportPhoto({
  id,
  alt,
}: {
  id: string | undefined
  alt: string
}) {
  if (!id) return <PhotoPlaceholder label={alt} />
  return <LostReportPhotoInner id={id} alt={alt} />
}
