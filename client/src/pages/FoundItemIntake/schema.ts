import { z } from 'zod'
import { createFoundItemWithPhotoBody } from '@/api/found-items/zod'

// `intakeText` is sourced from the generated body so a contract change (e.g. the
// max length) surfaces here as a type error. `foundAt` and `photo` can't be
// reused: the spec is strict UTC ISO (`...Z`) but the `datetime-local` input
// produces a naive local string, and the photo is a nullable `File` while editing.
export const foundItemIntakeSchema = z.object({
  intakeText: createFoundItemWithPhotoBody.shape.request.shape.intakeText,
  foundAt: z.string().min(1, 'Required'),
  photo: z.instanceof(File).nullable(),
})

export type FoundItemIntakeInput = z.infer<typeof foundItemIntakeSchema>
