import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  useGetPickups,
  useGetSchedules,
  useCreateSchedule,
  useUpdateSchedule,
  useDeleteSchedule,
  getGetSchedulesQueryKey,
} from '@/api/pickups/pickup-controller/pickup-controller'
import type { PickupScheduleResponse } from '@/api/pickups/model'
import {
  CreatePickupScheduleRequestDayOfWeek as DayOfWeek,
  CreatePickupScheduleRequestRecurrenceType as Recurrence,
} from '@/api/pickups/model'
import { useToast } from '@/components/Toast/toast-context'
import { formatDateTime } from '@/lib/format'
import ScheduleForm from './ScheduleForm'
import { emptyScheduleForm, type ScheduleFormValues } from './schema'

const titleCls = 'text-xl font-semibold text-text-h'
const cardCls = 'rounded-lg border border-border p-4'

function title(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase()
}

function describeSchedule(s: PickupScheduleResponse): string {
  const when =
    s.recurrenceType === Recurrence.WEEKLY
      ? `Weekly${s.dayOfWeek ? ` on ${title(s.dayOfWeek)}` : ''}`
      : 'Once'
  const window = `${(s.startTime ?? '').slice(0, 5)}–${(s.endTime ?? '').slice(0, 5)}`
  const range = s.endDate ? `${s.startDate} → ${s.endDate}` : `from ${s.startDate}`
  return `${when}, ${window} · ${s.slotLengthInMinutes}-min slots · ${range}`
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

  const upcoming = useMemo(() => {
    const now = new Date().getTime()
    return (pickups ?? [])
      .filter((p) => p.pickupAt && new Date(p.pickupAt).getTime() >= now)
      .sort(
        (a, b) =>
          new Date(a.pickupAt ?? 0).getTime() - new Date(b.pickupAt ?? 0).getTime(),
      )
  }, [pickups])

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
    const message =
      (error as { response?: { data?: { error?: string; message?: string } } })?.response
        ?.data?.error ??
      (error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
      `Failed to ${action} schedule.`
    show(message, { variant: 'error' })
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

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-10 p-6">
      <section className="flex flex-col gap-4">
        <h1 className={titleCls}>Upcoming pickups</h1>
        {pickupsLoading ? (
          <p className="text-sm text-text">Loading pickups…</p>
        ) : pickupsError ? (
          <p className="text-sm text-red-500">Couldn't load pickups.</p>
        ) : upcoming.length === 0 ? (
          <p className="text-sm text-text">No upcoming pickups booked.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {upcoming.map((p) => (
              <li
                key={p.id}
                className={`${cardCls} flex flex-wrap items-center justify-between gap-2`}
              >
                <span className="text-sm font-medium text-text-h">
                  {formatDateTime(p.pickupAt)}
                </span>
                <div className="flex flex-wrap items-center gap-4 text-sm text-text">
                  {p.email && (
                    <a className="text-accent hover:underline" href={`mailto:${p.email}`}>
                      {p.email}
                    </a>
                  )}
                  {p.matchId && (
                    <span className="font-mono text-xs">match {p.matchId.slice(0, 8)}</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="flex flex-col gap-4">
        <h1 className={titleCls}>Pickup schedule</h1>
        <p className="text-sm text-text">
          Define the availability windows guests can book pickups into.
        </p>

        {schedulesLoading ? (
          <p className="text-sm text-text">Loading schedules…</p>
        ) : schedulesError ? (
          <p className="text-sm text-red-500">Couldn't load schedules.</p>
        ) : (schedules ?? []).length === 0 ? (
          <p className="text-sm text-text">No schedules yet — add one below.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {(schedules ?? []).map((s) => (
              <li
                key={s.id}
                className={`${cardCls} flex flex-wrap items-center justify-between gap-2`}
              >
                <span className="text-sm text-text-h">{describeSchedule(s)}</span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setEditing(s)}
                    className="rounded border border-border px-3 py-1 text-sm text-text-h transition-colors hover:border-accent hover:text-accent"
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    disabled={deleteSchedule.isPending}
                    onClick={() => s.id && deleteSchedule.mutate({ scheduleId: s.id })}
                    className="rounded border border-border px-3 py-1 text-sm text-red-500 transition-colors hover:border-red-500 disabled:opacity-50"
                  >
                    Delete
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}

        <div className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold text-text-h">
            {editing ? 'Edit schedule' : 'New schedule'}
          </h2>
          <ScheduleForm
            key={`${editing?.id ?? 'new'}-${formNonce}`}
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
        </div>
      </section>
    </main>
  )
}
