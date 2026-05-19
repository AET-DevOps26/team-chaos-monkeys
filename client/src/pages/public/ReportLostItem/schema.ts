import { z } from 'zod'
import { createLostReportBody } from '@/api/lost-items/zod'

export const reportLostItemSchema = createLostReportBody
  .pick({ description: true, contactEmail: true })
  .extend({ photo: z.instanceof(File).nullable() })

export type ReportLostItemInput = z.infer<typeof reportLostItemSchema>
