import type { ReactNode } from 'react'
import type { MatchResponse, MatchResponseStatus } from '@/api/matches/model'
import { MatchResponseStatus as Status } from '@/api/matches/model'
import type { PickupResponse } from '@/api/pickups/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import type { FoundItemResponse } from '@/api/found-items/model'
import { useGetLostReportById } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import { useGetFoundItemById, useGetFoundItemPhotoUrl } from '@/api/found-items/found-item-controller/found-item-controller'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'

const dateFmt = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

function formatDate(value: string | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return dateFmt.format(d)
}

function firstLine(text: string | undefined): string | undefined {
  return text?.split(/\r?\n/)[0]?.trim() || undefined
}

function foundLabel(item: FoundItemResponse | undefined): string {
  return item?.attributes?.category?.trim() || firstLine(item?.intakeText) || 'Found item'
}

function lostLabel(report: LostReportResponse | undefined): string {
  return report?.attributes?.category?.trim() || firstLine(report?.description) || 'Lost report'
}

const statusPillCls: Record<MatchResponseStatus, string> = {
  [Status.PENDING]: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
  [Status.CONFIRMED]: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  [Status.REJECTED]: 'bg-red-500/15 text-red-700 dark:text-red-300',
}

function StatusPill({ status }: { status: MatchResponseStatus | undefined }) {
  if (!status) return null
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${statusPillCls[status]}`}
    >
      {status}
    </span>
  )
}

function ScoreChip({ score }: { score: number | undefined }) {
  if (score == null) return null
  return (
    <span
      className="rounded-full bg-accent-bg px-2 py-0.5 text-[10px] font-medium tabular-nums text-accent"
      title="Combined match score"
    >
      {Math.round(score * 100)}%
    </span>
  )
}

function Chip({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded bg-border/40 px-1.5 py-0.5 text-[11px] font-medium text-text-h">
      {children}
    </span>
  )
}

function NoPhotoTile({ label }: { label: string }) {
  return (
    <div
      role="img"
      aria-label={`No photo for ${label}`}
      className="flex h-full w-full flex-col items-center justify-center gap-1 bg-border/40 text-text"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-7 w-7 opacity-40"
        aria-hidden="true"
      >
        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
        <path d="m3.3 7 8.7 5 8.7-5M12 22V12" />
      </svg>
      <span className="text-[10px] font-medium uppercase tracking-wide opacity-60">
        No photo
      </span>
    </div>
  )
}

const dateTimeFullFmt = new Intl.DateTimeFormat(undefined, {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

function formatPickupWhen(value: string | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return dateTimeFullFmt.format(d)
}

// Full-width banner mirroring the mockup: a single bar that reads either
// "Pickup scheduled at …" or "No Pickup scheduled yet".
function PickupBanner({ pickup }: { pickup: PickupResponse | undefined }) {
  const when = formatPickupWhen(pickup?.pickupAt)
  const scheduled = !!pickup
  return (
    <div
      className={`flex items-center gap-2 rounded-md px-3 py-2.5 text-sm font-medium ${
        scheduled
          ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
          : 'bg-border/40 text-text'
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
        className="h-4 w-4 shrink-0"
        aria-hidden="true"
      >
        <rect x="3" y="4" width="18" height="18" rx="2" />
        <path d="M16 2v4M8 2v4M3 10h18" />
        {scheduled && <path d="m9 16 2 2 4-4" />}
      </svg>
      <span className="min-w-0 truncate">
        {scheduled
          ? `Pickup scheduled at ${when || 'a set time'}`
          : 'No Pickup scheduled yet'}
      </span>
    </div>
  )
}

function InfoRow({ icon, children, title }: { icon: ReactNode; children: ReactNode; title?: string }) {
  return (
    <span
      className="inline-flex min-w-0 max-w-full items-center gap-1.5 text-xs text-text"
      title={title}
    >
      <span className="shrink-0 text-text">{icon}</span>
      <span className="min-w-0 truncate">{children}</span>
    </span>
  )
}

export default function MatchCard({
  match,
  pickup,
  query,
}: {
  match: MatchResponse
  pickup: PickupResponse | undefined
  query?: string
}) {
  const { data: lostReport } = useGetLostReportById(match.lostReportId ?? '', {
    query: { enabled: !!match.lostReportId },
  })
  const { data: foundItem } = useGetFoundItemById(match.foundItemId ?? '', {
    query: { enabled: !!match.foundItemId },
  })

  const created = formatDate(match.createdAt)
  const foundName = foundLabel(foundItem)

  const lostName = lostLabel(lostReport)
  const lostDescription = lostReport?.description?.trim()
  const lostColor = lostReport?.attributes?.color?.trim()
  const lostBrand = lostReport?.attributes?.brand?.trim()
  const lostMarks = (lostReport?.attributes?.marks ?? []).filter(Boolean)
  const lostLocation = lostReport?.location?.trim()
  const lostEmail = lostReport?.contactEmail?.trim()
  const lostWhen = formatDate(lostReport?.lostAt)
  // Avoid repeating the description verbatim when it's already the heading.
  const showDescription = lostDescription && lostDescription !== lostName

  // Free-text filter. Once details have loaded, hide cards that don't match the
  // query. We can't filter before the lazy detail fetch resolves, so unmatched
  // cards may flash in briefly — acceptable for a client-side search.
  const q = query?.trim().toLowerCase()
  if (q) {
    const haystack = [
      foundName,
      lostName,
      lostDescription,
      lostEmail,
      lostLocation,
      lostColor,
      lostBrand,
      ...lostMarks,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    if (!haystack.includes(q)) return null
  }

  return (
    <article className="flex flex-col gap-3 rounded-lg border border-border bg-bg p-4 shadow-[var(--shadow)]">
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 flex-col">
          <span className="text-xs font-medium text-text-h">Match</span>
          {created && <span className="text-[11px] text-text">{created}</span>}
        </div>
        <div className="flex shrink-0 items-center gap-1.5">
          <ScoreChip score={match.combinedScore} />
          <StatusPill status={match.status} />
        </div>
      </div>

      <div className="flex gap-4">
        {/* Found item — photographed by staff, so lead with the image. */}
        <div className="flex w-32 shrink-0 flex-col gap-1 sm:w-36">
          <div className="relative aspect-square overflow-hidden rounded-md border border-border">
            {foundItem?.photoKey ? (
              <PhotoThumbnail
                id={match.foundItemId}
                alt={foundName}
                usePhotoUrl={useGetFoundItemPhotoUrl}
              />
            ) : (
              <NoPhotoTile label={foundName} />
            )}
            <span className="absolute left-1.5 top-1.5 rounded bg-bg/85 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-text-h backdrop-blur">
              Found
            </span>
          </div>
          <span className="truncate text-xs font-medium text-text-h" title={foundName}>
            {foundName}
          </span>
        </div>

        {/* Lost report — usually no photo; render the guest's described claim. */}
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <span className="text-[10px] font-semibold uppercase tracking-wide text-text">
            Lost claim
          </span>
          <span className="text-sm font-medium text-text-h">{lostName}</span>
          {showDescription && (
            <p className="line-clamp-2 text-xs text-text">{lostDescription}</p>
          )}
          {(lostColor || lostBrand || lostMarks.length > 0) && (
            <div className="flex flex-wrap gap-1">
              {lostColor && <Chip>{lostColor}</Chip>}
              {lostBrand && <Chip>{lostBrand}</Chip>}
              {lostMarks.map((mark) => (
                <Chip key={mark}>{mark}</Chip>
              ))}
            </div>
          )}
          <div className="mt-0.5 flex flex-col gap-1">
            {lostEmail && (
              <InfoRow
                title={lostEmail}
                icon={
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="h-3.5 w-3.5"
                    aria-hidden="true"
                  >
                    <rect x="2" y="4" width="20" height="16" rx="2" />
                    <path d="m22 7-10 6L2 7" />
                  </svg>
                }
              >
                <a className="hover:text-accent hover:underline" href={`mailto:${lostEmail}`}>
                  {lostEmail}
                </a>
              </InfoRow>
            )}
            {lostLocation && (
              <InfoRow
                icon={
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="h-3.5 w-3.5"
                    aria-hidden="true"
                  >
                    <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z" />
                    <circle cx="12" cy="10" r="3" />
                  </svg>
                }
              >
                {lostLocation}
              </InfoRow>
            )}
            {lostWhen && (
              <InfoRow
                icon={
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="h-3.5 w-3.5"
                    aria-hidden="true"
                  >
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 7v5l3 2" />
                  </svg>
                }
              >
                Lost {lostWhen}
              </InfoRow>
            )}
          </div>
        </div>
      </div>

      <PickupBanner pickup={pickup} />
    </article>
  )
}

export function MatchCardSkeleton() {
  return (
    <article
      className="flex flex-col gap-3 rounded-lg border border-border bg-bg p-4"
      aria-hidden="true"
    >
      <div className="flex items-center justify-between">
        <span className="h-4 w-16 animate-pulse rounded bg-border/40" />
        <span className="h-4 w-20 animate-pulse rounded bg-border/40" />
      </div>
      <div className="flex gap-3">
        <div className="aspect-square w-28 shrink-0 animate-pulse rounded border border-border bg-border/40 sm:w-32" />
        <div className="flex flex-1 flex-col gap-2">
          <span className="h-3 w-16 animate-pulse rounded bg-border/40" />
          <span className="h-4 w-2/3 animate-pulse rounded bg-border/40" />
          <span className="h-3 w-1/2 animate-pulse rounded bg-border/40" />
        </div>
      </div>
      <span className="h-7 w-40 animate-pulse rounded bg-border/40" />
    </article>
  )
}
