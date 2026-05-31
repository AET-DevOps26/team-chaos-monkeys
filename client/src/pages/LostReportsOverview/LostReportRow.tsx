import { useState } from 'react'
import type { LostReportResponse } from '@/api/lost-items/model'
import { LostReportResponseStatus } from '@/api/lost-items/model'
import LostReportPhoto from './LostReportPhoto'

const COL_COUNT = 5

const dateFmt = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

const dateTimeFmt = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

function formatDate(value: string | undefined, fmt: Intl.DateTimeFormat): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return fmt.format(d)
}

function summaryLabel(report: LostReportResponse): string {
  const firstLine = report.description?.split(/\r?\n/)[0]?.trim()
  if (firstLine) return firstLine
  const category = report.attributes?.category?.trim()
  if (category) return category
  return 'Lost report'
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

function Chevron({ open }: { open: boolean }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`h-4 w-4 shrink-0 text-text transition-transform ${open ? 'rotate-90' : ''}`}
      aria-hidden="true"
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  )
}

function DetailField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-[11px] font-medium uppercase tracking-wide text-text opacity-70">
        {label}
      </dt>
      <dd className="text-sm text-text-h">{children}</dd>
    </div>
  )
}

const cellCls = 'px-3 py-2.5 text-sm text-text align-middle'

export default function LostReportRow({ report }: { report: LostReportResponse }) {
  const [expanded, setExpanded] = useState(false)
  const label = summaryLabel(report)
  const marks = report.attributes?.marks?.filter((m) => m.trim()) ?? []

  const toggle = () => setExpanded((v) => !v)
  const onKeyDown = (e: React.KeyboardEvent<HTMLTableRowElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      toggle()
    }
  }

  return (
    <tbody className="border-b border-border last:border-b-0">
      <tr
        role="button"
        tabIndex={0}
        aria-expanded={expanded}
        onClick={toggle}
        onKeyDown={onKeyDown}
        className="cursor-pointer outline-none transition-colors hover:bg-accent-bg/40 focus-visible:bg-accent-bg/40"
      >
        <td className={`${cellCls} text-text-h`}>
          <div className="flex items-center gap-2">
            <Chevron open={expanded} />
            <span className="block max-w-[22rem] truncate font-medium" title={label}>
              {label}
            </span>
          </div>
        </td>
        <td className={cellCls}>{report.location || '—'}</td>
        <td className={`${cellCls} whitespace-nowrap`}>
          {formatDate(report.lostAt, dateFmt) || '—'}
        </td>
        <td className={`${cellCls} max-w-[16rem] truncate`} title={report.contactEmail}>
          {report.contactEmail || '—'}
        </td>
        <td className={`${cellCls} whitespace-nowrap`}>
          <StatusPill status={report.status} />
        </td>
      </tr>

      {expanded && (
        <tr>
          <td colSpan={COL_COUNT} className="bg-accent-bg/20 px-3 pb-5 pt-1">
            <div className="flex flex-col gap-4 sm:flex-row">
              <div className="aspect-[4/3] w-full max-w-[14rem] shrink-0 overflow-hidden rounded border border-border">
                <LostReportPhoto id={report.id} alt={label} />
              </div>
              <dl className="grid flex-1 grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
                <DetailField label="Description">
                  <span className="whitespace-pre-wrap break-words">
                    {report.description?.trim() || '—'}
                  </span>
                </DetailField>
                <DetailField label="Lost at">
                  {formatDate(report.lostAt, dateTimeFmt) || '—'}
                </DetailField>
                <DetailField label="Location">{report.location || '—'}</DetailField>
                <DetailField label="Reporter">{report.contactEmail || '—'}</DetailField>
                <DetailField label="Category">{report.attributes?.category || '—'}</DetailField>
                <DetailField label="Brand">{report.attributes?.brand || '—'}</DetailField>
                <DetailField label="Color">{report.attributes?.color || '—'}</DetailField>
                <DetailField label="Marks">{marks.length ? marks.join(', ') : '—'}</DetailField>
                <DetailField label="Venue">{report.venueId || '—'}</DetailField>
                <DetailField label="Status">
                  <StatusPill status={report.status} />
                </DetailField>
              </dl>
            </div>
          </td>
        </tr>
      )}
    </tbody>
  )
}

export function LostReportRowSkeleton() {
  return (
    <tbody className="border-b border-border last:border-b-0" aria-hidden="true">
      <tr>
        <td className={cellCls}>
          <div className="flex items-center gap-2">
            <span className="h-4 w-4 shrink-0 rounded bg-border/40" />
            <span className="block h-4 w-48 animate-pulse rounded bg-border/40" />
          </div>
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
    </tbody>
  )
}
