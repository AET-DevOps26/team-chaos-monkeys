import { useEffect, useState } from 'react'

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
  const [photo, setPhoto] = useState<{ src: string; url: string } | null>(null)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const controller = new AbortController()
    let objectUrl: string | null = null

    axiosInstance
      .get<Blob>(src, { responseType: 'blob', signal: controller.signal })
      .then(({ data }) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(data)
        setPhoto({ src, url: objectUrl })
        setFailedSrc((current) => (current === src ? null : current))
      })
      .catch(() => {
        if (!cancelled) setFailedSrc(src)
      })

    return () => {
      cancelled = true
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [src])

  const objectUrl = photo?.src === src ? photo.url : undefined
  const failed = failedSrc === src
  if (failed || !objectUrl) return <PhotoPlaceholder label={alt} category={category} />

  return (
    <img
      src={objectUrl}
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
