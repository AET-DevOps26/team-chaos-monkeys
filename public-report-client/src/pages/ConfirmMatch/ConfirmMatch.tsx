import { useNavigate, useParams } from 'react-router-dom'
import {
  useGetPublicFoundItem,
  useGetPublicMatch,
  useConfirmPublicMatch,
  useRejectPublicMatch,
} from '@/api/matches/match-controller/match-controller'
import type { ItemAttributesPayload } from '@/api/matches/model'

function Shell({ children }: { children: React.ReactNode }) {
  return <main className="mx-auto w-full max-w-xl p-6">{children}</main>
}

function formatFoundAt(value?: string): string | null {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
}

// Attribute rows we show if the GenAI extraction populated them.
function attributeRows(attrs?: ItemAttributesPayload): Array<[string, string]> {
  if (!attrs) return []
  const rows: Array<[string, string]> = []
  if (attrs.category) rows.push(['Category', attrs.category])
  if (attrs.brand) rows.push(['Brand', attrs.brand])
  if (attrs.color) rows.push(['Color', attrs.color])
  if (attrs.marks?.length) rows.push(['Distinguishing marks', attrs.marks.join(', ')])
  return rows
}

export default function ConfirmMatch() {
  const navigate = useNavigate()
  const { token } = useParams<{ token: string }>()

  const match = useGetPublicMatch(token!)
  const foundItem = useGetPublicFoundItem(token!)
  const confirm = useConfirmPublicMatch()
  const reject = useRejectPublicMatch()

  const handleConfirm = async () => {
    await confirm.mutateAsync({ token: token! })
    // The same magic-link token schedules the pickup (see SchedulePickup).
    navigate(`/pickup/${token}`)
  }

  const handleReject = async () => {
    await reject.mutateAsync({ token: token! })
  }

  if (match.isLoading || foundItem.isLoading) {
    return (
      <Shell>
        <p className="text-sm text-text">Loading the match…</p>
      </Shell>
    )
  }

  if (match.isError || foundItem.isError) {
    return (
      <Shell>
        <p className="rounded border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-500">
          This match link is invalid or has expired. Please contact the venue directly.
        </p>
      </Shell>
    )
  }

  // Re-opening the link after acting (or after the staff resolved it elsewhere):
  // don't offer the actions again.
  if (reject.isSuccess || match.data?.status === 'REJECTED') {
    return (
      <Shell>
        <h1 className="mb-3 text-3xl font-medium text-text-h">Match rejected</h1>
        <p className="text-sm text-text">
          Thanks — we won&apos;t hold this item for you. We&apos;ll keep looking and email you if
          another match turns up.
        </p>
      </Shell>
    )
  }

  if (match.data?.status === 'CONFIRMED') {
    return (
      <Shell>
        <div className="mb-2 text-sm font-medium uppercase tracking-wider text-accent">
          ✓ Match confirmed
        </div>
        <h1 className="mb-3 text-3xl font-medium text-text-h">You&apos;re all set</h1>
        <p className="mb-6 text-sm text-text">
          You&apos;ve already confirmed this match. Schedule a time to pick up your item.
        </p>
        <button
          type="button"
          onClick={() => navigate(`/pickup/${token}`)}
          className="self-start rounded bg-accent px-5 py-2.5 font-medium text-white"
        >
          Schedule pickup
        </button>
      </Shell>
    )
  }

  const item = foundItem.data
  const rows = attributeRows(item?.attributes)
  const foundAt = formatFoundAt(item?.foundAt)
  const pending = confirm.isPending || reject.isPending

  return (
    <Shell>
      <h1 className="mb-2 text-3xl font-medium text-text-h">Is this your item?</h1>
      <p className="mb-8 text-sm text-text">
        A venue logged a found item that may match your report. Confirm it&apos;s yours to schedule
        a pickup, or reject it if it isn&apos;t.
      </p>

      {item?.photoUrl && (
        <img
          src={item.photoUrl}
          alt={item.description ?? 'Found item'}
          className="mb-6 max-h-80 w-full rounded-lg border border-border object-contain"
        />
      )}

      {item?.description && (
        <p className="mb-6 text-base text-text-h">{item.description}</p>
      )}

      <dl className="mb-8 flex flex-col divide-y divide-border border-y border-border">
        {foundAt && (
          <div className="flex flex-col gap-1 py-4">
            <dt className="text-xs font-medium uppercase tracking-wider text-text">Found on</dt>
            <dd className="text-sm text-text-h">{foundAt}</dd>
          </div>
        )}
        {item?.locationHint && (
          <div className="flex flex-col gap-1 py-4">
            <dt className="text-xs font-medium uppercase tracking-wider text-text">Found near</dt>
            <dd className="text-sm text-text-h">{item.locationHint}</dd>
          </div>
        )}
        {rows.map(([label, value]) => (
          <div key={label} className="flex flex-col gap-1 py-4">
            <dt className="text-xs font-medium uppercase tracking-wider text-text">{label}</dt>
            <dd className="text-sm text-text-h">{value}</dd>
          </div>
        ))}
      </dl>

      <div className="flex flex-col gap-3 sm:flex-row">
        <button
          type="button"
          onClick={handleConfirm}
          disabled={pending}
          className="rounded bg-accent px-5 py-2.5 font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
        >
          {confirm.isPending ? 'Confirming…' : "Yes, that's mine"}
        </button>
        <button
          type="button"
          onClick={handleReject}
          disabled={pending}
          className="rounded border border-border px-5 py-2.5 font-medium text-text-h transition-colors hover:border-accent disabled:cursor-not-allowed disabled:opacity-50"
        >
          {reject.isPending ? 'Rejecting…' : "No, that's not mine"}
        </button>
      </div>

      {(confirm.isError || reject.isError) && (
        <p className="mt-3 text-sm text-red-500">Something went wrong. Please try again.</p>
      )}
    </Shell>
  )
}
