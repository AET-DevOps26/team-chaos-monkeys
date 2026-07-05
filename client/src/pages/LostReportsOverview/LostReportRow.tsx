import type { LostReportResponse } from '@/api/lost-items/model'
import { LostReportResponseStatus } from '@/api/lost-items/model'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'
import { formatDate, firstLine } from '@/lib/format'

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
    </tr>
  )
}
