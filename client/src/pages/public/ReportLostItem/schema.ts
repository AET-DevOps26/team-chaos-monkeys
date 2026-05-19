import { z } from 'zod'
import { createLostReportBody } from '@/api/lost-items/zod'

export const reportLostItemSchema = createLostReportBody
  .pick({ description: true, contactEmail: true })
  .extend({
    lostAt: z
      .string()
      .min(1, 'Please pick when you lost it')
      .refine((v) => !Number.isNaN(Date.parse(v)), 'Invalid date'),
    photo: z.instanceof(File).nullable(),
  })

export type ReportLostItemInput = z.infer<typeof reportLostItemSchema>
