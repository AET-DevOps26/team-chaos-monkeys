import { Navigate, useLocation } from 'react-router-dom'
import type { LostReportResponse } from '@/api/lost-items/model/lostReportResponse'

type ConfirmationState = { report?: LostReportResponse }

function formatLostAt(value: string | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

export default function ReportConfirmation() {
  const location = useLocation()
  const state = location.state as ConfirmationState | null
  const report = state?.report

  if (!report?.id) {
    return <Navigate to="/report" replace />
  }

  return (
    <main className="mx-auto w-full max-w-xl p-6 animate-[foundItemFadeIn_300ms_ease-out_both]">
      <div className="mb-2 text-sm font-medium uppercase tracking-wider text-accent">
        ✓ Report submitted
      </div>
      <h1 className="mb-3 text-3xl font-medium text-text-h">
        Thanks, we&apos;re on it.
      </h1>
      <p className="mb-8! text-sm text-text">
        Reference{' '}
        <span className="font-mono text-text-h">#{report.id}</span>
        <br />
        keep this in case you need to follow up.
      </p>

      <dl className="flex flex-col divide-y divide-border border-y border-border">
        <div className="flex flex-col gap-1 py-4">
          <dt className="text-xs font-medium uppercase tracking-wider text-text">
            Description
          </dt>
          <dd className="whitespace-pre-wrap text-sm text-text-h">
            {report.description ?? '—'}
          </dd>
        </div>
        <div className="flex flex-col gap-1 py-4">
          <dt className="text-xs font-medium uppercase tracking-wider text-text">
            When
          </dt>
          <dd className="text-sm text-text-h">{formatLostAt(report.lostAt)}</dd>
        </div>
        <div className="flex flex-col gap-1 py-4">
          <dt className="text-xs font-medium uppercase tracking-wider text-text">
            Contact email
          </dt>
          <dd className="text-sm text-text-h">{report.contactEmail ?? '—'}</dd>
        </div>
      </dl>

      <p className="mt-8! text-sm text-text">
        We&apos;ll search for matching found items and email you at{' '}
        <span className="font-medium text-text-h">
          {report.contactEmail ?? 'the address you provided'}
        </span>{' '}
        if we find one.
      </p>
    </main>
  )
}
