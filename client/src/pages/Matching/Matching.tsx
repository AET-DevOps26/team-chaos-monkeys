import { useEffect, useMemo, useRef, useState } from 'react'
import { useGetAllMatches } from '@/api/matches/match-controller/match-controller'
import { GetAllMatchesStatus } from '@/api/matches/model'
import type { GetAllMatchesStatus as Status } from '@/api/matches/model'
import { useGetPickups } from '@/api/pickups/pickup-controller/pickup-controller'
import MatchCard, { MatchCardSkeleton } from './MatchCard'

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

const pillBase =
  'rounded-full px-3 py-1 text-xs font-medium transition-colors border'
const pillActive = 'border-accent bg-accent-bg text-accent'
const pillIdle = 'border-border text-text-h hover:border-accent'

const gridCls = 'grid grid-cols-1 gap-5 lg:grid-cols-2'

export default function Matching() {
  const [filter, setFilter] = useState<Filter>('ALL')
  const [query, setQuery] = useState('')
  const [filterOpen, setFilterOpen] = useState(false)
  const filterRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!filterOpen) return
    const onDown = (e: MouseEvent) => {
      if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
        setFilterOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [filterOpen])

  const params = filter === 'ALL' ? undefined : { status: filter }
  const { data: matches, isLoading, isError, refetch, isFetching } =
    useGetAllMatches(params)

  // Single fetch of pickups, joined to matches by matchId. The server already
  // scopes both lists to the staff member's venue via the JWT.
  const { data: pickups } = useGetPickups(undefined)
  const pickupByMatchId = useMemo(
    () => new Map((pickups ?? []).map((p) => [p.matchId, p])),
    [pickups],
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

  const activeFilterLabel =
    FILTERS.find((f) => f.value === filter)?.label ?? 'All'

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 p-6">
      <header className="flex flex-col gap-3">
        <h1 className="sr-only">Matches</h1>
        <div className="flex items-center gap-3">
          {/* Filter funnel — toggles the status options popover. */}
          <div ref={filterRef} className="relative shrink-0">
            <button
              type="button"
              aria-label="Filter matches"
              aria-expanded={filterOpen}
              aria-haspopup="menu"
              onClick={() => setFilterOpen((o) => !o)}
              className={`relative flex h-11 w-11 items-center justify-center rounded-full border transition-colors ${
                filter !== 'ALL'
                  ? 'border-accent text-accent'
                  : 'border-border text-text-h hover:border-accent hover:text-accent'
              }`}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="h-5 w-5"
                aria-hidden="true"
              >
                <path d="M22 3H2l8 9.46V19l4 2v-8.54L22 3z" />
              </svg>
              {filter !== 'ALL' && (
                <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full bg-accent" />
              )}
            </button>
            {filterOpen && (
              <div
                role="menu"
                aria-label="Filter by status"
                className="absolute left-0 top-full z-10 mt-2 flex flex-col gap-1 rounded-lg border border-border bg-bg p-1.5 shadow-[var(--shadow)]"
              >
                {FILTERS.map((f) => {
                  const active = filter === f.value
                  return (
                    <button
                      key={f.value}
                      type="button"
                      role="menuitemradio"
                      aria-checked={active}
                      onClick={() => {
                        setFilter(f.value)
                        setFilterOpen(false)
                      }}
                      className={`${pillBase} whitespace-nowrap text-left ${active ? pillActive : pillIdle}`}
                    >
                      {f.label}
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          {/* Search bar */}
          <div className="relative flex-1">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-text"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.3-4.3" />
            </svg>
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by item, description, email, location…"
              aria-label="Search matches"
              className="h-11 w-full rounded-full border border-border bg-border/30 pl-11 pr-4 text-sm text-text-h placeholder:text-text focus:border-accent focus:outline-none"
            />
          </div>
        </div>
        {filter !== 'ALL' && (
          <div className="flex items-center gap-2 text-xs text-text">
            <span>Filtered: {activeFilterLabel}</span>
            <button
              type="button"
              onClick={() => setFilter('ALL')}
              className="text-accent hover:underline"
            >
              Clear
            </button>
          </div>
        )}
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
      ) : recentMatches.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-sm text-text">
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
        </div>
      ) : (
        <div className={gridCls} aria-busy={isFetching}>
          {recentMatches.map((match) => (
            <MatchCard
              key={match.id}
              match={match}
              pickup={pickupByMatchId.get(match.id)}
              query={query}
            />
          ))}
        </div>
      )}
    </main>
  )
}
