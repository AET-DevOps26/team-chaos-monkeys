import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { axiosInstance } from '@/api/mutator/custom-instance'
import electronicsIcon from '@/assets/category/electronics.svg'
import clothingIcon from '@/assets/category/clothing.svg'
import accessoriesIcon from '@/assets/category/accessories.svg'
import bagsIcon from '@/assets/category/bags.svg'
import documentsIcon from '@/assets/category/documents.svg'
import keysIcon from '@/assets/category/keys.svg'
import jewelryIcon from '@/assets/category/jewelry.svg'
import otherIcon from '@/assets/category/other.svg'
import placeholderIcon from '@/assets/category/placeholder.svg'

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
  src,
  alt,
  category,
}: {
  src: string
  alt: string
  category?: string
}) {
  // Cache the blob in React Query so leaving/returning to the page reuses the
  // downloaded bytes instead of re-fetching on every remount.
  const { data: blob, isError } = useQuery({
    queryKey: ['photo-blob', src],
    queryFn: ({ signal }) =>
      axiosInstance
        .get<Blob>(src, { responseType: 'blob', signal })
        .then(({ data }) => data),
    staleTime: Infinity,
    retry: false,
  })

  // Object URLs can't be shared across mounts (revoking one invalidates it
  // everywhere), so mint a fresh one per mount from the cached blob. This is
  // external-resource sync that needs the effect lifecycle for revocation —
  // the useMemo alternative leaks a URL under StrictMode's double-invoke.
  const [objectUrl, setObjectUrl] = useState<{ src: string; url: string } | null>(null)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  useEffect(() => {
    if (!blob) return
    const url = URL.createObjectURL(blob)
    /* eslint-disable react-hooks/set-state-in-effect */
    setObjectUrl({ src, url })
    setFailedSrc((current) => (current === src ? null : current))
    /* eslint-enable react-hooks/set-state-in-effect */
    return () => URL.revokeObjectURL(url)
  }, [blob, src])

  const url = objectUrl?.src === src ? objectUrl.url : undefined
  const failed = failedSrc === src

  if (isError || failed || !url)
    return <PhotoPlaceholder label={alt} category={category} />

  return (
    <img
      src={url}
      alt={alt}
      loading="lazy"
      className="h-full w-full object-cover"
      onError={() => setFailedSrc(src)}
    />
  )
}

export default function PhotoThumbnail({
  src,
  alt,
  category,
}: {
  src: string | undefined
  alt: string
  category?: string
}) {
  if (!src) return <PhotoPlaceholder label={alt} category={category} />
  return <PhotoThumbnailInner src={src} alt={alt} category={category} />
}
