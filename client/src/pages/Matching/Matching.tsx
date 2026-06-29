import { useMemo, useState } from 'react'
import { useGetAllMatches } from '@/api/matches/match-controller/match-controller'
import { GetAllMatchesStatus } from '@/api/matches/model'
import type { GetAllMatchesStatus as Status } from '@/api/matches/model'
import { useGetPickups } from '@/api/pickups/pickup-controller/pickup-controller'
import { useGetAllLostReports } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import { useGetAllFoundItems } from '@/api/found-items/found-item-controller/found-item-controller'
import { filterPillClass } from '@/components/filterPill'
import MatchCard, { MatchCardSkeleton, matchSearchText } from './MatchCard'
import searchIcon from '@/assets/search-icon.svg'

type Filter = Status | 'ALL'

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: GetAllMatchesStatus.PENDING, label: 'Pending' },
  { value: GetAllMatchesStatus.CONFIRMED, label: 'Confirmed' },
  { value: GetAllMatchesStatus.REJECTED, label: 'Rejected' },
]

// The matches list endpoint has no pagination or server-side ordering, so we
// sort newest-first and show a bounded recent window client-side.
const RECENT_LIMIT = 50

const gridCls = 'grid grid-cols-1 gap-5 lg:grid-cols-2'

export default function Matching() {
  const [filter, setFilter] = useState<Filter>('ALL')
  const [query, setQuery] = useState('')

  const params = filter === 'ALL' ? undefined : { status: filter }
  const { data: matches, isLoading, isError, refetch, isFetching } =
    useGetAllMatches(params)

  // Single fetch of pickups + the lost/found detail lists, joined to matches by
  // id. The server already scopes every list to the staff member's venue via the
  // JWT, so one list fetch each replaces the per-card by-id lookups.
  const { data: pickups } = useGetPickups(undefined)
  const { data: lostReports } = useGetAllLostReports(undefined)
  const { data: foundItems } = useGetAllFoundItems(undefined)

  const pickupByMatchId = useMemo(
    () => new Map((pickups ?? []).map((p) => [p.matchId, p])),
    [pickups],
  )
  const lostById = useMemo(
    () => new Map((lostReports ?? []).map((r) => [r.id, r])),
    [lostReports],
  )
  const foundById = useMemo(
    () => new Map((foundItems ?? []).map((f) => [f.id, f])),
    [foundItems],
  )

  const recentMatches = useMemo(() => {
    if (!matches) return []
    return [...matches]
      .sort(
        (a, b) =>
          new Date(b.createdAt ?? 0).getTime() -
          new Date(a.createdAt ?? 0).getTime(),
      )
      .slice(0, RECENT_LIMIT)
  }, [matches])

  // Free-text filter, applied here so the empty state can tell "no matches yet"
  // apart from "nothing matches your search".
  const q = query.trim().toLowerCase()
  const visibleMatches = useMemo(() => {
    if (!q) return recentMatches
    return recentMatches.filter((m) =>
      matchSearchText(lostById.get(m.lostReportId ?? ''), foundById.get(m.foundItemId ?? '')).includes(q),
    )
  }, [recentMatches, q, lostById, foundById])

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 p-6">
      <header className="flex flex-col gap-3">
        <h1 className="sr-only">Matches</h1>
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
                className={filterPillClass(active)}
              >
                {f.label}
              </button>
            )
          })}
        </div>

        {/* Search bar */}
        <div className="relative">
          <img
            src={searchIcon}
            alt=""
            aria-hidden="true"
            className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 opacity-60"
          />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by item, description, email, location…"
            aria-label="Search matches"
            className="h-11 w-full rounded-full border border-border bg-border/30 pl-11 pr-4 text-sm text-text-h placeholder:text-text focus:border-accent focus:outline-none"
          />
        </div>
      </header>

      {isLoading ? (
        <div className={gridCls}>
          {Array.from({ length: 6 }).map((_, i) => (
            <MatchCardSkeleton key={i} />
          ))}
        </div>
      ) : isError ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
          <span>Couldn't load matches.</span>
          <button
            type="button"
            onClick={() => refetch()}
            className="rounded border border-border px-3 py-1 text-text-h transition-colors hover:border-accent hover:text-accent"
          >
            Retry
          </button>
        </div>
      ) : visibleMatches.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
          {q ? (
            <>
              <span>No matches for “{query.trim()}”.</span>
              <button
                type="button"
                onClick={() => setQuery('')}
                className="rounded border border-border px-3 py-1 text-text-h transition-colors hover:border-accent hover:text-accent"
              >
                Clear search
              </button>
            </>
          ) : (
            <>
              <span>No matches{filter !== 'ALL' ? ' with this status' : ' yet'}.</span>
              {filter !== 'ALL' && (
                <button
                  type="button"
                  onClick={() => setFilter('ALL')}
                  className="rounded border border-border px-3 py-1 text-text-h transition-colors hover:border-accent hover:text-accent"
                >
                  Show all
                </button>
              )}
            </>
          )}
        </div>
      ) : (
        <div className={gridCls} aria-busy={isFetching}>
          {visibleMatches.map((match) => (
            <MatchCard
              key={match.id}
              match={match}
              lostReport={lostById.get(match.lostReportId ?? '')}
              foundItem={foundById.get(match.foundItemId ?? '')}
              pickup={pickupByMatchId.get(match.id)}
            />
          ))}
        </div>
      )}
    </main>
  )
}
