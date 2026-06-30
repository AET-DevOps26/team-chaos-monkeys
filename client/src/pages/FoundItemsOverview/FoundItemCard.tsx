import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { FoundItemResponse } from '@/api/found-items/model'
import {
  getGetAllFoundItemsQueryKey,
  useDeleteFoundItem,
  useGetFoundItemPhotoUrl,
} from '@/api/found-items/found-item-controller/found-item-controller'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'
import { useToast } from '@/components/Toast/toast-context'
import StatusControl from './StatusControl'
import FoundItemDetails from './FoundItemDetails'
import chevronIcon from '@/assets/chevron-down.svg'
import closeIcon from '@/assets/close.svg'
import deleteIcon from '@/assets/delete.svg'
import { formatDate, firstLine } from '@/lib/format'

function primaryLabel(item: FoundItemResponse): string {
  return (
    item.attributes?.description?.trim() ||
    item.attributes?.category?.trim() ||
    firstLine(item.intakeText) ||
    'Found item'
  )
}

export default function FoundItemCard({ item }: { item: FoundItemResponse }) {
  const label = primaryLabel(item)
  const date = formatDate(item.foundAt)
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [confirming, setConfirming] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const { mutate: deleteItem, isPending: isDeleting } = useDeleteFoundItem({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({
          queryKey: getGetAllFoundItemsQueryKey(),
        })
        show('Item deleted.', { variant: 'success' })
      },
      onError: () => {
        show('Failed to delete item.', { variant: 'error' })
      },
    },
  })

  const onDeleteClick = () => {
    if (!item.id || isDeleting) return
    if (!confirming) {
      setConfirming(true)
      return
    }
    deleteItem({ id: item.id })
  }

  const onMouseLeave = () => {
    if (!isDeleting) setConfirming(false)
  }

  const onCancelClick = () => {
    if (!isDeleting) setConfirming(false)
  }

  return (
    <article className="flex flex-col gap-2">
      <div
        className="group relative aspect-[4/3] overflow-hidden rounded border border-border"
        onMouseLeave={onMouseLeave}
      >
        <div className="absolute inset-0 transition-all duration-300 ease-out group-hover:scale-[1.02] group-hover:blur-sm group-hover:brightness-75">
          <PhotoThumbnail
            id={item.id}
            alt={label}
            usePhotoUrl={useGetFoundItemPhotoUrl}
          />
        </div>
        {confirming && (
          <button
            type="button"
            onClick={onCancelClick}
            disabled={isDeleting}
            aria-label={`Cancel delete ${label}`}
            title="Cancel"
            className="absolute right-2 top-2 z-10 animate-[foundItemFadeIn_180ms_ease-out_both] rounded-full bg-bg/85 p-1 text-text-h shadow-[var(--shadow)] backdrop-blur transition-colors hover:bg-bg disabled:cursor-not-allowed disabled:opacity-50"
          >
            <img src={closeIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
          </button>
        )}
        <div className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-200 ease-out group-hover:pointer-events-auto group-hover:opacity-100">
          <button
            type="button"
            onClick={onDeleteClick}
            disabled={isDeleting}
            aria-label={confirming ? `Confirm delete ${label}` : `Delete ${label}`}
            title={confirming ? 'Click again to confirm' : 'Delete'}
            className={`absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 animate-[foundItemFadeIn_220ms_ease-out_both] rounded-full p-2.5 shadow-[var(--shadow)] backdrop-blur transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
              confirming
                ? 'bg-red-500 text-white'
                : 'bg-bg/85 text-text-h hover:bg-red-500 hover:text-white'
            }`}
          >
            <img src={deleteIcon} alt="" aria-hidden="true" className="h-5 w-5" />
          </button>
          {confirming && (
            <span
              role="status"
              className="absolute left-1/2 top-1/2 mt-9 -translate-x-1/2 animate-[foundItemFadeIn_180ms_ease-out_both] whitespace-nowrap rounded bg-bg/85 px-2 py-0.5 text-xs font-medium text-text-h shadow-[var(--shadow)] backdrop-blur"
            >
              Are you sure?
            </span>
          )}
        </div>
      </div>
      <div className="flex items-end justify-between gap-2 px-0.5">
        <div className="flex min-w-0 items-center gap-1.5">
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            aria-expanded={expanded}
            aria-label={expanded ? 'Hide details' : 'Show details'}
            title={expanded ? 'Hide details' : 'Show details'}
            className="shrink-0 rounded p-0.5 transition-opacity hover:opacity-70"
          >
            <img
              src={chevronIcon}
              alt=""
              aria-hidden="true"
              className={`h-4 w-4 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`}
            />
          </button>
          <div className="flex min-w-0 flex-col">
            <span className="truncate text-sm font-medium text-text-h" title={label}>
              {label}
            </span>
            {date && <span className="text-xs text-text">{date}</span>}
          </div>
        </div>
        <div className="shrink-0">
          <StatusControl item={item} />
        </div>
      </div>

      {expanded && <FoundItemDetails item={item} />}
    </article>
  )
}

export function FoundItemCardSkeleton() {
  return (
    <article className="flex flex-col gap-2" aria-hidden="true">
      <div className="aspect-[4/3] animate-pulse rounded border border-border bg-border/40" />
      <div className="flex flex-col gap-1 px-0.5">
        <span className="h-4 w-2/3 animate-pulse rounded bg-border/40" />
        <span className="h-3 w-1/3 animate-pulse rounded bg-border/40" />
      </div>
    </article>
  )
}
