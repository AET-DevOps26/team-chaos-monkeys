import { z } from 'zod'
import { createLostReportWithPhotoBody } from '@/api/lost-items/zod'

// `lostAt` can't be picked from the generated body: the spec is strict
// UTC ISO (`...Z`), but the `datetime-local` input produces a naive
// local string. The other fields stay sourced from codegen so a spec
// change surfaces here as a type error.
export const reportLostItemSchema = createLostReportWithPhotoBody
  .pick({ description: true, contactEmail: true })
  .extend({
    lostAt: z
      .string()
      .min(1, 'Please pick when you lost it')
      .refine((v) => !Number.isNaN(Date.parse(v)), 'Invalid date'),
    photo: z.instanceof(File).nullable(),
  })

export type ReportLostItemInput = z.infer<typeof reportLostItemSchema>
