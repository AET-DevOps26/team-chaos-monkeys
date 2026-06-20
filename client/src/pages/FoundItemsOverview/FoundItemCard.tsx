import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { FoundItemResponse } from '@/api/found-items/model'
import { FoundItemResponseStatus } from '@/api/found-items/model'
import {
  getGetAllFoundItemsQueryKey,
  useDeleteFoundItem,
  useGetFoundItemPhotoUrl,
} from '@/api/found-items/found-item-controller/found-item-controller'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'
import { useToast } from '@/components/Toast/toast-context'

const dateFmt = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

function formatFoundAt(foundAt: string | undefined): string {
  if (!foundAt) return ''
  const d = new Date(foundAt)
  if (Number.isNaN(d.getTime())) return ''
  return dateFmt.format(d)
}

function primaryLabel(item: FoundItemResponse): string {
  const category = item.attributes?.category?.trim()
  if (category) return category
  const firstLine = item.intakeText?.split(/\r?\n/)[0]?.trim()
  if (firstLine) return firstLine
  return 'Found item'
}

const statusPillCls: Record<FoundItemResponseStatus, string> = {
  [FoundItemResponseStatus.STORED]: 'bg-accent-bg text-accent',
  [FoundItemResponseStatus.RESERVED]: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  [FoundItemResponseStatus.RETURNED]: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  [FoundItemResponseStatus.DISPOSED]: 'bg-text/15 text-text',
}

function StatusPill({ status }: { status: FoundItemResponseStatus | undefined }) {
  if (!status) return null
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${statusPillCls[status]}`}
    >
      {status}
    </span>
  )
}

export default function FoundItemCard({ item }: { item: FoundItemResponse }) {
  const label = primaryLabel(item)
  const date = formatFoundAt(item.foundAt)
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [confirming, setConfirming] = useState(false)
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
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-3.5 w-3.5"
              aria-hidden="true"
            >
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
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
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-5 w-5"
              aria-hidden="true"
            >
              <path d="M3 6h18" />
              <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
              <path d="M10 11v6" />
              <path d="M14 11v6" />
            </svg>
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
        <div className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-medium text-text-h" title={label}>
            {label}
          </span>
          {date && <span className="text-xs text-text">{date}</span>}
        </div>
        <div className="shrink-0">
          <StatusPill status={item.status} />
        </div>
      </div>
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
