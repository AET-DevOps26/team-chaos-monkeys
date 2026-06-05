import { useMemo, useState } from 'react'
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

const gridCls = 'grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3'

export default function Matching() {
  const [filter, setFilter] = useState<Filter>('ALL')

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

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-medium text-text-h">Matches</h1>
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
            />
          ))}
        </div>
      )}
    </main>
  )
}
