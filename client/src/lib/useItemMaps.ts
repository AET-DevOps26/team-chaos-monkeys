import { useMemo } from 'react'
import { useGetAllLostReports } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import { useGetAllFoundItems } from '@/api/found-items/found-item-controller/found-item-controller'
import type { FoundItemResponse } from '@/api/found-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'

/**
 * Fetch the venue-scoped lost-report and found-item lists once and index them by
 * id. Both the Matching and Returns pages join matches → item detail this way;
 * the shared query keys mean React Query dedupes the two fetches across pages.
 */
export function useItemMaps() {
  const { data: lostReports } = useGetAllLostReports(undefined)
  const { data: foundItems } = useGetAllFoundItems(undefined)

  const lostById = useMemo(
    () => new Map<string | undefined, LostReportResponse>((lostReports ?? []).map((r) => [r.id, r])),
    [lostReports],
  )
  const foundById = useMemo(
    () => new Map<string | undefined, FoundItemResponse>((foundItems ?? []).map((f) => [f.id, f])),
    [foundItems],
  )

  return { lostById, foundById }
}
