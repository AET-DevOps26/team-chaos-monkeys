import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { createScheduleBody } from '@/api/pickups/zod'
import type { CreatePickupScheduleRequest } from '@/api/pickups/model'
import {
  CreatePickupScheduleRequestDayOfWeek as DayOfWeek,
  CreatePickupScheduleRequestRecurrenceType as Recurrence,
} from '@/api/pickups/model'
import type { ScheduleFormValues } from './schema'

const inputCls =
  'rounded border border-border bg-transparent p-2 text-sm outline-none focus:border-accent'
const labelCls = 'text-xs font-medium text-text-h'

type Props = {
  defaultValues: ScheduleFormValues
  submitLabel: string
  isSubmitting: boolean
  onSubmit: (payload: CreatePickupScheduleRequest) => void
  onCancel?: () => void
}

export default function ScheduleForm({
  defaultValues,
  submitLabel,
  isSubmitting,
  onSubmit,
  onCancel,
}: Props) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isValid },
  } = useForm<ScheduleFormValues>({
    resolver: zodResolver(createScheduleBody),
    mode: 'onChange',
    defaultValues,
  })

  const recurrence = watch('recurrenceType')
  const isWeekly = recurrence === Recurrence.WEEKLY

  const submit = (values: ScheduleFormValues) => {
    // venueId is omitted: the backend derives it from the staff JWT.
    // ponytail: multi-venue admins must supply a venueId (backend 400s otherwise);
    // a venue picker is deferred until a multi-venue admin actually needs one.
    onSubmit({
      recurrenceType: values.recurrenceType,
      startDate: values.startDate,
      endDate: values.endDate || undefined,
      dayOfWeek: isWeekly ? values.dayOfWeek : undefined,
      startTime: values.startTime,
      endTime: values.endTime,
      slotLengthInMinutes: values.slotLengthInMinutes,
    })
  }

  return (
    <form
      onSubmit={handleSubmit(submit)}
      className="flex flex-col gap-3 rounded-lg border border-border p-4"
      noValidate
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor="recurrenceType" className={labelCls}>Recurrence</label>
          <select id="recurrenceType" className={inputCls} {...register('recurrenceType')}>
            <option value={Recurrence.WEEKLY}>Weekly</option>
            <option value={Recurrence.ONCE}>Once</option>
          </select>
        </div>

        {isWeekly && (
          <div className="flex flex-col gap-1">
            <label htmlFor="dayOfWeek" className={labelCls}>Day of week</label>
            <select id="dayOfWeek" className={inputCls} {...register('dayOfWeek')}>
              {Object.values(DayOfWeek).map((d) => (
                <option key={d} value={d}>
                  {d.charAt(0) + d.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="flex flex-col gap-1">
          <label htmlFor="startDate" className={labelCls}>Start date</label>
          <input id="startDate" type="date" className={inputCls} {...register('startDate')} />
          {errors.startDate && (
            <span className="text-xs text-red-500">{errors.startDate.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="endDate" className={labelCls}>End date (optional)</label>
          <input
            id="endDate"
            type="date"
            className={inputCls}
            {...register('endDate', { setValueAs: (v) => (v === '' ? undefined : v) })}
          />
          {errors.endDate && (
            <span className="text-xs text-red-500">{errors.endDate.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="startTime" className={labelCls}>Start time</label>
          <input id="startTime" type="time" className={inputCls} {...register('startTime')} />
          {errors.startTime && (
            <span className="text-xs text-red-500">{errors.startTime.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="endTime" className={labelCls}>End time</label>
          <input id="endTime" type="time" className={inputCls} {...register('endTime')} />
          {errors.endTime && (
            <span className="text-xs text-red-500">{errors.endTime.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="slotLengthInMinutes" className={labelCls}>Slot length (minutes)</label>
          <input
            id="slotLengthInMinutes"
            type="number"
            min={1}
            className={inputCls}
            {...register('slotLengthInMinutes', { valueAsNumber: true })}
          />
          {errors.slotLengthInMinutes && (
            <span className="text-xs text-red-500">{errors.slotLengthInMinutes.message}</span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2">
        <button
          type="submit"
          disabled={!isValid || isSubmitting}
          className="rounded bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? 'Saving…' : submitLabel}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="rounded border border-border px-4 py-2 text-sm text-text-h transition-colors hover:border-accent hover:text-accent"
          >
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
