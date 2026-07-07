import { useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import {
  useGetAllVenues,
  useGetVenueKpis,
  useGetVenueKpisById,
} from '@/api/operations/venue-controller/venue-controller'
import { useGetFoundItemHistogram } from '@/api/found-items/found-item-controller/found-item-controller'
import { useGetLostReportHistogram } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import { useGetMatchHistogram } from '@/api/matches/match-controller/match-controller'
import { filterPillClass } from '@/components/filterPill'
import StatCard from './StatCard'
import TrendChart, { type Granularity } from './TrendChart'
import VenuePicker from './VenuePicker'
import ItemSearchPanel from './ItemSearchPanel'

const GRANULARITIES: { label: string; value: Granularity }[] = [
  { label: 'Daily', value: 'perDay' },
  { label: 'Weekly', value: 'perWeek' },
  { label: 'Monthly', value: 'perMonth' },
]

export default function Dashboard() {
  const { user } = useAuth()
  const isAdmin = !!user?.roles.includes('ADMIN')

  const [selectedVenueId, setSelectedVenueId] = useState<string | null>(null)
  const [granularity, setGranularity] = useState<Granularity>('perDay')

  const venuesQuery = useGetAllVenues({ query: { enabled: isAdmin } })

  // The KPI aggregator scopes by JWT for staff, but the raw histogram endpoints
  // do not — so we must pass the venue explicitly for staff (null = all venues).
  const scopeVenueId = isAdmin ? selectedVenueId : (user?.venueId ?? null)
  const histParams = scopeVenueId ? { venueId: scopeVenueId } : undefined

  // Cards: admins viewing a specific venue use the by-id endpoint; everyone else
  // uses the JWT-scoped aggregate (staff = own venue, admin "All" = global).
  const useById = isAdmin && !!selectedVenueId
  const kpisAll = useGetVenueKpis({ query: { enabled: !useById } })
  const kpisById = useGetVenueKpisById(selectedVenueId ?? '', {
    query: { enabled: useById },
  })
  const kpi = useById ? kpisById.data : kpisAll.data
  const kpiLoading = useById ? kpisById.isLoading : kpisAll.isLoading
  const kpiError = useById ? kpisById.isError : kpisAll.isError
  const kpiRefetch = useById ? kpisById.refetch : kpisAll.refetch

  const found = useGetFoundItemHistogram(histParams)
  const lost = useGetLostReportHistogram(histParams)
  const matches = useGetMatchHistogram(histParams)
  const chartLoading = found.isLoading || lost.isLoading || matches.isLoading

  const series = [
    { key: 'found', label: 'Found items', color: '#06b6d4', data: found.data },
    { key: 'lost', label: 'Lost reports', color: '#ec4899', data: lost.data },
    { key: 'matches', label: 'Matches', color: '#7d33ff', data: matches.data },
  ]

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 p-6">
      {isAdmin && (
        <div className="flex flex-wrap items-center justify-end gap-4">
          <VenuePicker
            venues={venuesQuery.data ?? []}
            value={selectedVenueId}
            onChange={setSelectedVenueId}
          />
        </div>
      )}

      {kpiError ? (
        <div className="flex flex-col items-start gap-3 rounded-lg border border-border bg-surface p-6">
          <p className="text-text-h">Couldn’t load dashboard metrics.</p>
          <button
            onClick={() => kpiRefetch()}
            className="rounded-md bg-accent-bg px-4 py-2 text-sm font-medium text-accent"
          >
            Retry
          </button>
        </div>
      ) : (
        <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            label="Found items"
            value={kpi?.totalFoundItems}
            isLoading={kpiLoading}
          />
          <StatCard
            label="Lost reports"
            value={kpi?.totalLostItems}
            isLoading={kpiLoading}
          />
          <StatCard
            label="Matches"
            value={kpi?.totalMatches}
            isLoading={kpiLoading}
          />
          <StatCard
            label="Pending matches"
            value={kpi?.pendingMatches}
            isLoading={kpiLoading}
            accent
          />
        </section>
      )}

      <section className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-lg font-semibold text-text-h">Activity over time</h2>
          <div className="flex gap-1" role="group" aria-label="Granularity">
            {GRANULARITIES.map(({ label, value }) => (
              <button
                key={value}
                onClick={() => setGranularity(value)}
                aria-pressed={granularity === value}
                className={filterPillClass(granularity === value)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <TrendChart
          granularity={granularity}
          series={series}
          isLoading={chartLoading}
        />
      </section>

      <ItemSearchPanel />
    </main>
  )
}
