import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import {
  useGetPickups,
  useGetSchedules,
  useCreateSchedule,
  useUpdateSchedule,
  useDeleteSchedule,
  getGetSchedulesQueryKey,
} from '@/api/pickups/pickup-controller/pickup-controller'
import { useGetAllMatches } from '@/api/matches/match-controller/match-controller'
import { useGetAllLostReports } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import { useGetAllFoundItems } from '@/api/found-items/found-item-controller/found-item-controller'
import type { PickupScheduleResponse } from '@/api/pickups/model'
import type { FoundItemResponse } from '@/api/found-items/model'
import type { LostReportResponse } from '@/api/lost-items/model'
import {
  CreatePickupScheduleRequestDayOfWeek as DayOfWeek,
  CreatePickupScheduleRequestRecurrenceType as Recurrence,
} from '@/api/pickups/model'
import { useToast } from '@/components/Toast/toast-context'
import { firstLine } from '@/lib/format'
import calendarIcon from '@/assets/calendar.svg'
import clockIcon from '@/assets/clock.svg'
import mailIcon from '@/assets/mail.svg'
import deleteIcon from '@/assets/delete.svg'
import ScheduleForm from './ScheduleForm'
import { emptyScheduleForm, type ScheduleFormValues } from './schema'

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })
const dayFmt = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' })

function cap(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase()
}

function itemLabel(
  found: FoundItemResponse | undefined,
  lost: LostReportResponse | undefined,
): string {
  // Lead with the guest's own description of what they're collecting — far more
  // identifying for staff than a generic category ("CLOTHING"). Fall back to the
  // staff intake note, then categories.
  return (
    firstLine(lost?.description) ||
    firstLine(found?.intakeText) ||
    lost?.attributes?.category?.trim() ||
    found?.attributes?.category?.trim() ||
    'Pickup'
  )
}

function scheduleParts(s: PickupScheduleResponse) {
  const weekly = s.recurrenceType === Recurrence.WEEKLY
  const window = `${(s.startTime ?? '').slice(0, 5)}–${(s.endTime ?? '').slice(0, 5)}`
  const day = weekly && s.dayOfWeek ? cap(s.dayOfWeek) : null
  const range = s.endDate ? `${s.startDate} → ${s.endDate}` : `from ${s.startDate}`
  return {
    weekly,
    badge: weekly ? 'Weekly' : 'One-off',
    heading: [day, window].filter(Boolean).join(' · '),
    meta: `${s.slotLengthInMinutes}-min slots · ${range}`,
  }
}

function toFormValues(s: PickupScheduleResponse): ScheduleFormValues {
  return {
    recurrenceType: s.recurrenceType ?? Recurrence.WEEKLY,
    startDate: s.startDate ?? '',
    endDate: s.endDate ?? '',
    dayOfWeek: s.dayOfWeek ?? DayOfWeek.MONDAY,
    startTime: (s.startTime ?? '09:00').slice(0, 5),
    endTime: (s.endTime ?? '17:00').slice(0, 5),
    slotLengthInMinutes: s.slotLengthInMinutes ?? 15,
  }
}

const sectionHeaderCls = 'flex items-center gap-3'
const h2Cls = 'text-lg font-semibold text-text-h'
const countBadgeCls =
  'rounded-full bg-accent-bg px-2 py-0.5 text-xs font-medium tabular-nums text-accent'
const cardCls = 'rounded-lg border border-border bg-bg p-4 shadow-[var(--shadow)]'

export default function Returns() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [editing, setEditing] = useState<PickupScheduleResponse | null>(null)
  // Bumped on a successful create so the (remounting) form resets to blank.
  const [formNonce, setFormNonce] = useState(0)

  const { data: pickups, isLoading: pickupsLoading, isError: pickupsError } =
    useGetPickups(undefined)
  const { data: schedules, isLoading: schedulesLoading, isError: schedulesError } =
    useGetSchedules(undefined)
  // Joined so each booked pickup shows the item to prepare, not just a raw id.
  const { data: matches } = useGetAllMatches(undefined)
  const { data: lostReports } = useGetAllLostReports(undefined)
  const { data: foundItems } = useGetAllFoundItems(undefined)

  const foundById = useMemo(
    () => new Map((foundItems ?? []).map((f) => [f.id, f])),
    [foundItems],
  )
  const lostById = useMemo(
    () => new Map((lostReports ?? []).map((r) => [r.id, r])),
    [lostReports],
  )
  const matchById = useMemo(
    () => new Map((matches ?? []).map((m) => [m.id, m])),
    [matches],
  )

  const upcoming = useMemo(() => {
    const now = new Date().getTime()
    return (pickups ?? [])
      .filter((p) => p.pickupAt && new Date(p.pickupAt).getTime() >= now)
      .sort(
        (a, b) =>
          new Date(a.pickupAt ?? 0).getTime() - new Date(b.pickupAt ?? 0).getTime(),
      )
      .map((p) => {
        const match = p.matchId ? matchById.get(p.matchId) : undefined
        const found = match?.foundItemId ? foundById.get(match.foundItemId) : undefined
        const lost = match?.lostReportId ? lostById.get(match.lostReportId) : undefined
        return { pickup: p, label: itemLabel(found, lost) }
      })
  }, [pickups, matchById, foundById, lostById])

  const resetForm = () => {
    setEditing(null)
    setFormNonce((n) => n + 1)
  }

  const onScheduleSaved = (verb: string) => {
    queryClient.invalidateQueries({ queryKey: getGetSchedulesQueryKey() })
    show(`Schedule ${verb}.`, { variant: 'success' })
    resetForm()
  }

  const onScheduleError = (action: string, error: unknown) => {
    const data = (error as { response?: { data?: { error?: string; message?: string } } })
      ?.response?.data
    show(data?.error ?? data?.message ?? `Failed to ${action} schedule.`, {
      variant: 'error',
    })
  }

  const createSchedule = useCreateSchedule({
    mutation: {
      onSuccess: () => onScheduleSaved('created'),
      onError: (e) => onScheduleError('create', e),
    },
  })
  const updateSchedule = useUpdateSchedule({
    mutation: {
      onSuccess: () => onScheduleSaved('updated'),
      onError: (e) => onScheduleError('update', e),
    },
  })
  const deleteSchedule = useDeleteSchedule({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetSchedulesQueryKey() })
        show('Schedule deleted.', { variant: 'success' })
        if (editing) resetForm()
      },
      onError: (e) => onScheduleError('delete', e),
    },
  })

  const isSaving = createSchedule.isPending || updateSchedule.isPending
  const scheduleList = schedules ?? []

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-10 p-6">
      {/* Upcoming booked pickups — staff prep queue. */}
      <section className="flex flex-col gap-4">
        <div className={sectionHeaderCls}>
          <h2 className={h2Cls}>Upcoming pickups</h2>
          {upcoming.length > 0 && <span className={countBadgeCls}>{upcoming.length}</span>}
        </div>

        {pickupsLoading ? (
          <p className="text-sm text-text">Loading pickups…</p>
        ) : pickupsError ? (
          <p className="text-sm text-red-500">Couldn't load pickups.</p>
        ) : upcoming.length === 0 ? (
          <div className={`${cardCls} flex items-center gap-3 text-sm text-text`}>
            <img src={calendarIcon} alt="" aria-hidden="true" className="h-5 w-5 opacity-60" />
            No upcoming pickups booked.
          </div>
        ) : (
          <ul className="flex flex-col gap-3">
            {upcoming.map(({ pickup: p, label }) => {
              const at = p.pickupAt ? new Date(p.pickupAt) : null
              return (
                <li key={p.id}>
                  <Link
                    to={p.matchId ? `/matches#match-${p.matchId}` : '/matches'}
                    className={`group flex items-center gap-4 ${cardCls} transition-colors hover:border-accent`}
                  >
                    <div className="flex w-24 shrink-0 flex-col items-center rounded-md bg-accent-bg px-2 py-2 text-center">
                      <span className="whitespace-nowrap text-sm font-semibold leading-tight text-accent tabular-nums">
                        {at ? timeFmt.format(at) : '—'}
                      </span>
                      <span className="text-[11px] text-accent/80">
                        {at ? dayFmt.format(at) : ''}
                      </span>
                    </div>
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate text-sm font-medium text-text-h">{label}</span>
                      {p.email && (
                        <span className="flex items-center gap-1.5 text-xs text-text">
                          <img src={mailIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
                          <span className="truncate">{p.email}</span>
                        </span>
                      )}
                    </div>
                    <span className="shrink-0 text-xs font-medium text-text opacity-0 transition-opacity group-hover:text-accent group-hover:opacity-100">
                      View match →
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {/* Availability schedule management. */}
      <section className="flex flex-col gap-4">
        <div className={sectionHeaderCls}>
          <h2 className={h2Cls}>Pickup schedule</h2>
          {scheduleList.length > 0 && (
            <span className={countBadgeCls}>{scheduleList.length}</span>
          )}
        </div>
        <p className="-mt-2 text-sm text-text">
          Define the availability windows guests can book pickups into.
        </p>

        {schedulesLoading ? (
          <p className="text-sm text-text">Loading schedules…</p>
        ) : schedulesError ? (
          <p className="text-sm text-red-500">Couldn't load schedules.</p>
        ) : scheduleList.length === 0 ? (
          <div className={`${cardCls} flex items-center gap-3 text-sm text-text`}>
            <img src={clockIcon} alt="" aria-hidden="true" className="h-5 w-5 opacity-60" />
            No schedules yet — add one below.
          </div>
        ) : (
          <ul className="flex flex-col gap-3">
            {scheduleList.map((s) => {
              const parts = scheduleParts(s)
              const active = editing?.id === s.id
              return (
                <li
                  key={s.id}
                  className={`flex items-center gap-3 ${cardCls} ${
                    active ? 'border-accent ring-1 ring-accent/40' : ''
                  }`}
                >
                  <img
                    src={calendarIcon}
                    alt=""
                    aria-hidden="true"
                    className="h-5 w-5 shrink-0 opacity-70"
                  />
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <div className="flex items-center gap-2">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${
                          parts.weekly
                            ? 'bg-accent-bg text-accent'
                            : 'bg-border/50 text-text-h'
                        }`}
                      >
                        {parts.badge}
                      </span>
                      <span className="truncate text-sm font-medium text-text-h">
                        {parts.heading}
                      </span>
                    </div>
                    <span className="text-xs text-text">{parts.meta}</span>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setEditing(s)}
                      className="rounded border border-border px-3 py-1 text-sm text-text-h transition-colors hover:border-accent hover:text-accent"
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      aria-label="Delete schedule"
                      disabled={deleteSchedule.isPending}
                      onClick={() => s.id && deleteSchedule.mutate({ scheduleId: s.id })}
                      className="group/del rounded border border-border p-1.5 text-text transition-colors hover:border-red-500/60 hover:text-red-500 disabled:opacity-50"
                    >
                      <img
                        src={deleteIcon}
                        alt=""
                        aria-hidden="true"
                        className="h-4 w-4 opacity-70 transition-opacity group-hover/del:opacity-100"
                      />
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        <ScheduleForm
          key={`${editing?.id ?? 'new'}-${formNonce}`}
          heading={editing ? 'Edit schedule' : 'New schedule'}
          defaultValues={editing ? toFormValues(editing) : emptyScheduleForm}
          submitLabel={editing ? 'Save changes' : 'Add schedule'}
          isSubmitting={isSaving}
          onCancel={editing ? resetForm : undefined}
          onSubmit={(payload) => {
            if (editing?.id) {
              updateSchedule.mutate({ scheduleId: editing.id, data: payload })
            } else {
              createSchedule.mutate({ data: payload })
            }
          }}
        />
      </section>
    </main>
  )
}
