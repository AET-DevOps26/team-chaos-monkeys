import type { z } from 'zod'
import { createScheduleBody } from '@/api/pickups/zod'
import {
  CreatePickupScheduleRequestDayOfWeek as DayOfWeek,
  CreatePickupScheduleRequestRecurrenceType as Recurrence,
} from '@/api/pickups/model'

export type ScheduleFormValues = z.infer<typeof createScheduleBody>

export const emptyScheduleForm: ScheduleFormValues = {
  recurrenceType: Recurrence.WEEKLY,
  startDate: '',
  endDate: '',
  dayOfWeek: DayOfWeek.MONDAY,
  startTime: '09:00',
  endTime: '17:00',
  slotLengthInMinutes: 15,
}
