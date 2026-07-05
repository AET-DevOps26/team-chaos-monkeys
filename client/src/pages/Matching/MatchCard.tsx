import { useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { MatchResponse, MatchResponseStatus } from '@/api/matches/model'
import { MatchResponseStatus as Status } from '@/api/matches/model'
import type { PickupResponse } from '@/api/pickups/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import type { FoundItemResponse } from '@/api/found-items/model'
import { useGetFoundItemPhotoUrl } from '@/api/found-items/found-item-controller/found-item-controller'
import { useCreatePublicMatchLink } from '@/api/matches/match-controller/match-controller'
import { getGetMatchContactsQueryKey } from '@/api/notifications/notification-controller/notification-controller'
import PhotoThumbnail from '@/components/PhotoThumbnail/PhotoThumbnail'
import calendarIcon from '@/assets/calendar.svg'
import mailIcon from '@/assets/mail.svg'
import locationIcon from '@/assets/location.svg'
import clockIcon from '@/assets/clock.svg'
import { formatDate, firstLine } from '@/lib/format'

function foundLabel(item: FoundItemResponse | undefined): string {
  return item?.attributes?.category?.trim() || firstLine(item?.intakeText) || 'Found item'
}

function lostLabel(report: LostReportResponse | undefined): string {
  return report?.attributes?.category?.trim() || firstLine(report?.description) || 'Lost report'
}

// Lower-cased searchable text for a match, kept here so the free-text filter in
// Matching.tsx searches exactly the fields this card renders.
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
      <img src={calendarIcon} alt="" aria-hidden="true" className="h-4 w-4 shrink-0" />
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

// Matches at or above this score are auto-invited by the backend
// (foundflow.matching.auto-invite-threshold); below it, a staff member who
// judges the match correct can reach out manually.
const AUTO_INVITE_THRESHOLD = 0.85

// The contact footer: shows when a guest has already been reached out to, or —
// for a sub-threshold pending match — a button to reach out manually. Reuses the
// existing public-match-link endpoint, which emails the guest their magic link.
function ReachOutControl({
  match,
  email,
  contactedAt,
}: {
  match: MatchResponse
  email: string | undefined
  contactedAt: string | undefined
}) {
  const queryClient = useQueryClient()
  const [sent, setSent] = useState(false)
  const [failed, setFailed] = useState(false)

  const { mutate: reachOut, isPending } = useCreatePublicMatchLink({
    mutation: {
      onSuccess: () => {
        setFailed(false)
        setSent(true)
        // The invite email is sent asynchronously, so "contacted" firms up on the
        // next refetch; keep local `sent` so the button doesn't flash back first.
        queryClient.invalidateQueries({ queryKey: getGetMatchContactsQueryKey() })
      },
      onError: () => setFailed(true),
    },
  })

  const contacted = contactedAt != null || sent

  if (contacted) {
    const when = formatDate(contactedAt)
    return (
      <div className="flex items-center gap-1.5 text-xs font-medium text-emerald-700 dark:text-emerald-300">
        <img src={mailIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
        <span>Guest reached out{when ? ` · ${when}` : ''}</span>
      </div>
    )
  }

  const eligible =
    match.status === Status.PENDING && (match.combinedScore ?? 0) < AUTO_INVITE_THRESHOLD
  if (!eligible) return null

  if (!email) {
    return (
      <p className="text-xs text-text">No guest email on file — can't reach out.</p>
    )
  }

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={() => {
          if (match.id && !isPending) reachOut({ id: match.id, data: { email } })
        }}
        disabled={isPending}
        className="inline-flex items-center justify-center gap-1.5 rounded-md border border-border px-3 py-2 text-xs font-medium text-text-h transition-colors hover:border-accent hover:text-accent disabled:opacity-50"
      >
        <img src={mailIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
        {isPending ? 'Sending…' : 'Reach out to guest'}
      </button>
      {failed && (
        <p className="text-xs text-red-600 dark:text-red-400">Couldn't send. Try again.</p>
      )}
    </div>
  )
}

export default function MatchCard({
  match,
  lostReport,
  foundItem,
  pickup,
  contactedAt,
}: {
  match: MatchResponse
  lostReport: LostReportResponse | undefined
  foundItem: FoundItemResponse | undefined
  pickup: PickupResponse | undefined
  contactedAt: string | undefined
}) {
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

  return (
    <article
      id={`match-${match.id}`}
      className="flex scroll-mt-20 flex-col gap-3 rounded-lg border border-border bg-bg p-4 shadow-[var(--shadow)] target:border-accent target:ring-2 target:ring-accent/40"
    >
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
            <PhotoThumbnail
              id={match.foundItemId}
              alt={foundName}
              usePhotoUrl={useGetFoundItemPhotoUrl}
            />
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
                  <img src={mailIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
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
                  <img src={locationIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
                }
              >
                {lostLocation}
              </InfoRow>
            )}
            {lostWhen && (
              <InfoRow
                icon={
                  <img src={clockIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
                }
              >
                Lost {lostWhen}
              </InfoRow>
            )}
          </div>
        </div>
      </div>

      <ReachOutControl match={match} email={lostEmail} contactedAt={contactedAt} />

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
