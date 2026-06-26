import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { FoundItemResponse } from '@/api/found-items/model'
import {
  FoundItemResponseStatus,
  UpdateFoundItemRequestStatus,
} from '@/api/found-items/model'
import {
  getGetAllFoundItemsQueryKey,
  useUpdateFoundItem,
} from '@/api/found-items/found-item-controller/found-item-controller'

// One colour scheme per status, reused for the pill and the dropdown options.
const statusPillCls: Record<FoundItemResponseStatus, string> = {
  [FoundItemResponseStatus.STORED]: 'bg-accent-bg text-accent',
  [FoundItemResponseStatus.RESERVED]: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  [FoundItemResponseStatus.RETURNED]: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  [FoundItemResponseStatus.DISPOSED]: 'bg-text/15 text-text',
}

const ALL_STATUSES = Object.values(FoundItemResponseStatus)

const pillBase =
  'rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide transition-colors'

/**
 * The status pill, but clickable: tapping it opens a small menu that lets a
 * staff member move the item to another status (Stored → Reserved → Returned …).
 * Saving sends the full item back through the PUT endpoint and refreshes the list.
 */
export default function StatusControl({ item }: { item: FoundItemResponse }) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()

  const { mutate: updateItem, isPending } = useUpdateFoundItem({
    mutation: {
      onSuccess: () => {
        setOpen(false)
        queryClient.invalidateQueries({ queryKey: getGetAllFoundItemsQueryKey() })
      },
    },
  })

  const status = item.status
  if (!status) return null

  const changeStatus = (next: FoundItemResponseStatus) => {
    if (next === status || !item.id || isPending) return
    // The update endpoint wants the whole item, so we resend it with only the
    // status changed. The required fields all come straight from the response.
    updateItem({
      id: item.id,
      data: {
        status: next as unknown as UpdateFoundItemRequestStatus,
        foundAt: item.foundAt ?? new Date().toISOString(),
        venueId: item.venueId ?? '',
        reporterId: item.reporterId ?? '',
        intakeText: item.intakeText,
        location: item.location,
        attributes: item.attributes,
      },
    })
  }

  return (
    // Focus leaving the wrapper (click/tab away) closes the menu; Escape too.
    // Hovering away never closes it.
    <div
      className="relative"
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) setOpen(false)
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') setOpen(false)
      }}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        disabled={isPending}
        aria-haspopup="listbox"
        aria-expanded={open}
        title="Change status"
        className={`${pillBase} ${statusPillCls[status]} cursor-pointer hover:brightness-95 disabled:opacity-50`}
      >
        {isPending ? '…' : status}
      </button>

      {open && (
        <ul
          role="listbox"
          className="absolute right-0 z-20 mt-1 flex flex-col gap-1.5 rounded border border-border bg-bg p-2 shadow-[var(--shadow)]"
        >
          {ALL_STATUSES.map((option) => (
            <li key={option}>
              <button
                type="button"
                role="option"
                aria-selected={option === status}
                onClick={() => changeStatus(option)}
                className={`${pillBase} ${statusPillCls[option]} block w-full px-3 py-1 text-left ${
                  option === status ? 'ring-1 ring-inset ring-accent' : 'hover:brightness-95'
                }`}
              >
                {option}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
