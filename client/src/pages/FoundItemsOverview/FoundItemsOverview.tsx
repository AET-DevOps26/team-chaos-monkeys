import { useState } from 'react'
import { useGetAllFoundItems } from '@/api/found-items/found-item-controller/found-item-controller'
import { GetAllFoundItemsStatus } from '@/api/found-items/model'
import type { GetAllFoundItemsStatus as Status } from '@/api/found-items/model'
import FoundItemCard, { FoundItemCardSkeleton } from './FoundItemCard'

type Filter = Status | 'ALL'

const FILTERS: { value: Filter; label: string }[] = [
  { value: GetAllFoundItemsStatus.STORED, label: 'Stored' },
  { value: GetAllFoundItemsStatus.RESERVED, label: 'Reserved' },
  { value: GetAllFoundItemsStatus.RETURNED, label: 'Returned' },
  { value: GetAllFoundItemsStatus.DISPOSED, label: 'Disposed' },
  { value: 'ALL', label: 'All' },
]

const pillBase =
  'rounded-full px-3 py-1 text-xs font-medium transition-colors border'
const pillActive = 'border-accent bg-accent-bg text-accent'
const pillIdle = 'border-border text-text-h hover:border-accent'

const gridCls =
  'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'

export default function FoundItemsOverview() {
  const [filter, setFilter] = useState<Filter>(GetAllFoundItemsStatus.STORED)

  const params = filter === 'ALL' ? undefined : { status: filter }
  const { data, isLoading, isError, refetch, isFetching } = useGetAllFoundItems(params)

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-medium text-text-h">Found items</h1>
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

      {isLoading ? (
        <div className={gridCls}>
          {Array.from({ length: 8 }).map((_, i) => (
            <FoundItemCardSkeleton key={i} />
          ))}
        </div>
      ) : isError ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
          <span>Couldn't load found items.</span>
          <button
            type="button"
            onClick={() => refetch()}
            className="rounded border border-border px-3 py-1 text-text-h transition-colors hover:border-accent hover:text-accent"
          >
            Retry
          </button>
        </div>
      ) : !data || data.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
          <span>No found items match this filter.</span>
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
        <div className={gridCls} aria-busy={isFetching}>
          {data.map((item) => (
            <FoundItemCard key={item.id} item={item} />
          ))}
        </div>
      )}
    </main>
  )
}
