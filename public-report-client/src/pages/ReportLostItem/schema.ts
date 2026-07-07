import { z } from 'zod'
import { createLostReportBody } from '@/api/lost-items/zod'

// `lostAt` can't be picked from the generated body: the spec is a strict
// UTC ISO datetime, but the form holds the date-only string from
// `<input type="date">` — onSubmit converts it to UTC midnight. The other
// fields stay sourced from codegen so a spec change surfaces here as a
// type error.
export const reportLostItemSchema = createLostReportBody
  .pick({ description: true, contactEmail: true })
  .extend({
    lostAt: z.iso.date('Please pick when you lost it'),
    photo: z.instanceof(File).nullable(),
  })

export type ReportLostItemInput = z.infer<typeof reportLostItemSchema>
