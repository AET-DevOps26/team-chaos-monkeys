import { useState } from 'react'
import { useGetAllLostReports } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import { GetAllLostReportsStatus } from '@/api/lost-items/model'
import type { GetAllLostReportsStatus as Status } from '@/api/lost-items/model'
import LostReportRow, { LostReportRowSkeleton } from './LostReportRow'

type Filter = Status | 'ALL'

const FILTERS: { value: Filter; label: string }[] = [
  { value: GetAllLostReportsStatus.OPEN, label: 'Open' },
  { value: GetAllLostReportsStatus.MATCHED, label: 'Matched' },
  { value: GetAllLostReportsStatus.COLLECTED, label: 'Collected' },
  { value: GetAllLostReportsStatus.CLOSED, label: 'Closed' },
  { value: 'ALL', label: 'All' },
]

const pillBase =
  'rounded-full px-3 py-1 text-xs font-medium transition-colors border'
const pillActive = 'border-accent bg-accent-bg text-accent'
const pillIdle = 'border-border text-text-h hover:border-accent'

const headCellCls =
  'px-3 py-2 text-left text-[11px] font-medium uppercase tracking-wide text-text'

export default function LostReportsOverview() {
  const [filter, setFilter] = useState<Filter>(GetAllLostReportsStatus.OPEN)

  const params = filter === 'ALL' ? undefined : { status: filter }
  const { data, isLoading, isError, refetch, isFetching } = useGetAllLostReports(params)

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-6">
      <header className="flex flex-wrap items-center justify-end gap-3">
        <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-2">
          {FILTERS.map((f) => {
            const active = filter === f.value
            return (
              <button
                key={f.value}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setFilter(f.value)}
                className={`${pillBase} ${active ? pillActive : pillIdle}`}
              >
                {f.label}
              </button>
            )
          })}
        </div>
      </header>

      {isError ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
          <span>Couldn't load lost reports.</span>
          <button
            type="button"
            onClick={() => refetch()}
            className="rounded border border-border px-3 py-1 text-text-h transition-colors hover:border-accent hover:text-accent"
          >
            Retry
          </button>
        </div>
      ) : !isLoading && (!data || data.length === 0) ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
          <span>No lost reports match this filter.</span>
          {filter !== 'ALL' && (
            <button
              type="button"
              onClick={() => setFilter('ALL')}
              className="rounded border border-border px-3 py-1 text-text-h transition-colors hover:border-accent hover:text-accent"
            >
              Show all
            </button>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto rounded border border-border">
          <table className="w-full border-collapse" aria-busy={isFetching}>
            <thead className="border-b border-border bg-accent-bg/20">
              <tr>
                <th scope="col" className={headCellCls}>
                  <span className="sr-only">Photo</span>
                </th>
                <th scope="col" className={headCellCls}>Item</th>
                <th scope="col" className={headCellCls}>Location</th>
                <th scope="col" className={headCellCls}>Lost on</th>
                <th scope="col" className={headCellCls}>Reporter</th>
                <th scope="col" className={headCellCls}>Status</th>
              </tr>
            </thead>
            <tbody>
              {isLoading
                ? Array.from({ length: 8 }).map((_, i) => <LostReportRowSkeleton key={i} />)
                : data?.map((report) => (
                    <LostReportRow key={report.id} report={report} />
                  ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
