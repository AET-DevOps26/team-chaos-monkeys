import { z } from 'zod'

export const foundItemIntakeSchema = z.object({
  intakeText: z.string().min(0).max(2000).optional(),
  foundAt: z.string().min(1, 'Required'),
  photo: z.instanceof(File).nullable(),
})

export type FoundItemIntakeInput = z.infer<typeof foundItemIntakeSchema>
