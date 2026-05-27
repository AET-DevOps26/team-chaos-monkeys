import { useGetFoundItemPhotoUrl } from '@/api/found-items/found-item-controller/found-item-controller'

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

export default function FoundItemPhoto({
  id,
  alt,
}: {
  id: string | undefined
  alt: string
}) {
  const { data, isLoading, isError, refetch } = useGetFoundItemPhotoUrl(id ?? '', {
    query: {
      enabled: !!id,
      staleTime: PHOTO_URL_STALE_MS,
      retry: 1,
    },
  })

  if (!id) return <PhotoPlaceholder label={alt} />
  if (isLoading) {
    return <div className="h-full w-full animate-pulse bg-border/40" aria-label={`Loading ${alt}`} />
  }
  if (isError || !data?.url) return <PhotoPlaceholder label={alt} />

  return (
    <img
      src={data.url}
      alt={alt}
      loading="lazy"
      className="h-full w-full object-cover"
      onError={() => {
        refetch()
      }}
    />
  )
}
