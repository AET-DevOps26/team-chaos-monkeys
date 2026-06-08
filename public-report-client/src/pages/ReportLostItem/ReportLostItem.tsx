import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { reportLostItemSchema, type ReportLostItemInput } from './schema'
import { useCreateLostReportWithPhoto } from '@/api/lost-items/lost-report-controller/lost-report-controller'
import type { CreateLostReportRequest, CreateLostReportWithPhotoBody } from '@/api/lost-items/model'

// TODO(FRND-122): temporary — the public form has no way to derive a venue yet.
// Replace with a real source (route param from a per-venue QR link, query
// string, or a public venue picker) before multi-venue rollout.
const PLACEHOLDER_VENUE_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6'

export default function ReportLostItem() {
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isValid },
  } = useForm<ReportLostItemInput>({
    resolver: zodResolver(reportLostItemSchema),
    mode: 'onChange',
    defaultValues: { description: '', contactEmail: '', lostAt: '', photo: null },
  })

  const { mutate, isPending, isError, error } = useCreateLostReportWithPhoto()

  const photo = watch('photo')
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!photo) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(photo)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [photo])

  const onSubmit = (data: ReportLostItemInput) => {
    const payload: CreateLostReportRequest = {
      description: data.description,
      contactEmail: data.contactEmail,
      // `datetime-local` already gives ISO-8601 local time
      // (`YYYY-MM-DDTHH:mm`); sent as-is so the user's wall-clock
      // intent is preserved instead of collapsed to UTC.
      lostAt: data.lostAt,
      venueId: PLACEHOLDER_VENUE_ID,
    }
    // `photo` is optional; pass `undefined` (not `null`) when none was
    // picked so the generated FormData builder omits the photo part.
    const body: CreateLostReportWithPhotoBody = {
      request: payload,
      photo: data.photo ?? undefined,
    }
    mutate(
      { data: body },
      {
        onSuccess: (response) => {
          navigate('/confirmation', {
            state: { report: response },
          })
        },
      },
    )
  }

  return (
    <main className="mx-auto w-full max-w-xl p-6">
      <h1 className="mb-6 text-3xl font-medium text-text-h">
        Report a lost item
      </h1>

      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        <div className="flex flex-col gap-1">
          <label htmlFor="description" className="text-sm font-medium text-text-h">
            Description
          </label>
          <textarea
            id="description"
            rows={4}
            className="rounded border border-border bg-transparent p-3 outline-none focus:border-accent"
            placeholder="e.g. Black leather wallet with three cards inside, lost near the main entrance"
            {...register('description')}
          />
          {errors.description && (
            <span className="text-sm text-red-500">{errors.description.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="lostAt" className="text-sm font-medium text-text-h">
            When did you lose it?
          </label>
          <input
            id="lostAt"
            type="datetime-local"
            className="rounded border border-border bg-transparent p-3 outline-none focus:border-accent"
            {...register('lostAt')}
          />
          {errors.lostAt && (
            <span className="text-sm text-red-500">{errors.lostAt.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="contactEmail" className="text-sm font-medium text-text-h">
            Contact email
          </label>
          <input
            id="contactEmail"
            type="email"
            autoComplete="email"
            className="rounded border border-border bg-transparent p-3 outline-none focus:border-accent"
            placeholder="you@example.com"
            {...register('contactEmail')}
          />
          {errors.contactEmail && (
            <span className="text-sm text-red-500">{errors.contactEmail.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <label htmlFor="photo" className="text-sm font-medium text-text-h">
            Photo (optional)
          </label>
          <input
            id="photo"
            type="file"
            accept="image/*"
            className="text-sm"
            onChange={(e) => {
              const file = e.target.files?.[0] ?? null
              setValue('photo', file, { shouldValidate: true })
            }}
          />
          {previewUrl && (
            <img
              src={previewUrl}
              alt="Selected preview"
              className="mt-2 h-auto max-h-60 w-auto max-w-full rounded border border-border object-contain"
            />
          )}
        </div>

        <button
          type="submit"
          disabled={!isValid || isPending}
          className="mt-2 self-start rounded bg-accent px-5 py-2.5 font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isPending ? 'Submitting…' : 'Submit report'}
        </button>

        {isError && (
          <p className="text-sm text-red-500">
            {error?.message ?? 'Something went wrong. Please try again.'}
          </p>
        )}
      </form>
    </main>
  )
}
