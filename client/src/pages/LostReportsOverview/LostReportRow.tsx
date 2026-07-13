import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { LostReportResponse } from '@/api/lost-items/model'
import { LostReportResponseStatus } from '@/api/lost-items/model'
import {
  getGetAllLostReportsQueryKey,
  useDeleteLostReport,
} from '@/api/lost-items/lost-report-controller/lost-report-controller'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'
import { useToast } from '@/components/Toast/toast-context'
import { formatDate, firstLine } from '@/lib/format'

// Inlined so the glyph inherits the button's text color (currentColor). The shared
// delete.svg/close.svg assets bake in a near-white fill for use on dark backdrops,
// which is invisible on the light table background when loaded via <img>.
function TrashIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 -960 960 960" fill="currentColor" aria-hidden="true" className={className}>
      <path d="M280-120q-33 0-56.5-23.5T200-200v-520h-40v-80h200v-40h240v40h200v80h-40v520q0 33-23.5 56.5T680-120H280Zm400-600H280v520h400v-520ZM360-280h80v-360h-80v360Zm160 0h80v-360h-80v360ZM280-720v520-520Z" />
    </svg>
  )
}

function CloseIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 -960 960 960" fill="currentColor" aria-hidden="true" className={className}>
      <path d="m256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z" />
    </svg>
  )
}

function summaryLabel(report: LostReportResponse): string {
  return (
    firstLine(report.description) ||
    report.attributes?.category?.trim() ||
    'Lost report'
  )
}

const statusPillCls: Record<LostReportResponseStatus, string> = {
  [LostReportResponseStatus.OPEN]: 'bg-accent-bg text-accent',
  [LostReportResponseStatus.MATCHED]: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  [LostReportResponseStatus.COLLECTED]: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  [LostReportResponseStatus.CLOSED]: 'bg-text/15 text-text',
}

function StatusPill({ status }: { status: LostReportResponseStatus | undefined }) {
  if (!status) return null
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${statusPillCls[status]}`}
    >
      {status}
    </span>
  )
}

const cellCls = 'px-3 py-2.5 text-sm text-text align-middle'
const thumbCls =
  'h-12 w-12 shrink-0 overflow-hidden rounded border border-border'

function DeleteControl({ report, label }: { report: LostReportResponse; label: string }) {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [confirming, setConfirming] = useState(false)
  const { mutate: deleteReport, isPending } = useDeleteLostReport({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetAllLostReportsQueryKey() })
        show('Lost report deleted.', { variant: 'success' })
      },
      onError: () => show('Failed to delete lost report.', { variant: 'error' }),
    },
  })

  if (!report.id) return null

  if (confirming) {
    return (
      <div className="flex items-center justify-end gap-1">
        <button
          type="button"
          onClick={() => deleteReport({ id: report.id! })}
          disabled={isPending}
          aria-label={`Confirm delete ${label}`}
          title="Click to confirm"
          className="rounded bg-red-500 px-2 py-1 text-[11px] font-medium text-white transition-colors hover:bg-red-600 disabled:opacity-50"
        >
          {isPending ? 'Deleting…' : 'Delete'}
        </button>
        <button
          type="button"
          onClick={() => setConfirming(false)}
          disabled={isPending}
          aria-label={`Cancel delete ${label}`}
          title="Cancel"
          className="rounded p-1 text-text transition-colors hover:text-text-h disabled:opacity-50"
        >
          <CloseIcon className="h-3.5 w-3.5" />
        </button>
      </div>
    )
  }

  return (
    <div className="flex justify-end">
      <button
        type="button"
        onClick={() => setConfirming(true)}
        aria-label={`Delete ${label}`}
        title="Delete"
        className="rounded p-1.5 text-text transition-colors hover:bg-red-500/10 hover:text-red-600 dark:hover:text-red-400"
      >
        <TrashIcon className="h-4 w-4" />
      </button>
    </div>
  )
}

export default function LostReportRow({ report }: { report: LostReportResponse }) {
  const label = summaryLabel(report)

  return (
    <tr className="border-b border-border last:border-b-0 transition-colors hover:bg-accent-bg/40">
      <td className={`${cellCls} w-12`}>
        <div className={thumbCls}>
          <PhotoThumbnail
            src={report.photoUrl}
            alt={label}
            category={report.attributes?.category}
          />
        </div>
      </td>
      <td className={`${cellCls} text-text-h`}>
        <span className="block max-w-[22rem] truncate font-medium" title={label}>
          {label}
        </span>
      </td>
      <td className={cellCls}>{report.location || '—'}</td>
      <td className={`${cellCls} whitespace-nowrap`}>
        {formatDate(report.lostAt) || '—'}
      </td>
      <td className={`${cellCls} max-w-[16rem] truncate`} title={report.contactEmail}>
        {report.contactEmail || '—'}
      </td>
      <td className={`${cellCls} whitespace-nowrap`}>
        <StatusPill status={report.status} />
      </td>
      <td className={`${cellCls} w-px whitespace-nowrap text-right`}>
        <DeleteControl report={report} label={label} />
      </td>
    </tr>
  )
}

export function LostReportRowSkeleton() {
  return (
    <tr className="border-b border-border last:border-b-0" aria-hidden="true">
      <td className={cellCls}>
        <span className={`${thumbCls} block animate-pulse bg-border/40`} />
      </td>
      <td className={cellCls}>
        <span className="block h-4 w-48 animate-pulse rounded bg-border/40" />
      </td>
      <td className={cellCls}>
        <span className="block h-4 w-24 animate-pulse rounded bg-border/40" />
      </td>
      <td className={cellCls}>
        <span className="block h-4 w-20 animate-pulse rounded bg-border/40" />
      </td>
      <td className={cellCls}>
        <span className="block h-4 w-36 animate-pulse rounded bg-border/40" />
      </td>
      <td className={cellCls}>
        <span className="block h-4 w-16 animate-pulse rounded-full bg-border/40" />
      </td>
      <td className={cellCls}>
        <span className="ml-auto block h-4 w-6 animate-pulse rounded bg-border/40" />
      </td>
    </tr>
  )
}
