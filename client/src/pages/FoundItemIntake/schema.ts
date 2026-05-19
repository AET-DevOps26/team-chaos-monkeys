import { z } from 'zod'

export const foundItemIntakeSchema = z.object({
  description: z.string().optional(),
  foundAt: z.string().min(1, 'Required'),
  locationHint: z.string().optional(),
  category: z.string().optional(),
  brand: z.string().optional(),
  color: z.string().optional(),
  marks: z.array(z.object({ value: z.string() })),
  photo: z.instanceof(File).nullable(),
})

export type FoundItemIntakeInput = z.infer<typeof foundItemIntakeSchema>
