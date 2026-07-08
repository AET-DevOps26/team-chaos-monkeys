import type { FoundItemResponse } from '@/api/found-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import { firstLine } from '@/lib/format'

function foundLabel(item: FoundItemResponse | undefined): string {
  return item?.attributes?.category?.trim() || firstLine(item?.intakeText) || 'Found item'
}

function lostLabel(report: LostReportResponse | undefined): string {
  return report?.attributes?.category?.trim() || firstLine(report?.description) || 'Lost report'
}

// Lower-cased searchable text for a match, kept in sync with the fields the card renders.
export function matchSearchText(
  lostReport: LostReportResponse | undefined,
  foundItem: FoundItemResponse | undefined,
): string {
  return [
    foundLabel(foundItem),
    lostLabel(lostReport),
    lostReport?.description,
    lostReport?.contactEmail,
    lostReport?.location,
    lostReport?.attributes?.color,
    lostReport?.attributes?.brand,
    ...(lostReport?.attributes?.marks ?? []),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}
