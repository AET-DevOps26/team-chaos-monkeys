import type { FoundItemResponse } from '@/api/found-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import { firstLine } from '@/lib/format'

export function foundLabel(item: FoundItemResponse | undefined): string {
  return item?.attributes?.category?.trim() || firstLine(item?.intakeText) || 'Found item'
}

export function lostLabel(report: LostReportResponse | undefined): string {
  return report?.attributes?.category?.trim() || firstLine(report?.description) || 'Lost report'
}
