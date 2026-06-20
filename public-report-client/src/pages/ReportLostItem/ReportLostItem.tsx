import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { reportLostItemSchema, type ReportLostItemInput } from './schema'
import {
  useCreateLostReport,
  useUpdateLostReportPhoto,
} from '@/api/lost-items/lost-report-controller/lost-report-controller'
import type { CreateLostReportRequest } from '@/api/lost-items/model'
import { usePublicVenues, findVenueBySlug } from '@/venues'

// The venue is supplied by the route as a readable name slug
// (/report/grand-hotel), typically via a per-venue QR link. We look the slug
// up against the list of public venues to find the venue id the backend needs.
export default function ReportLostItem() {
  const navigate = useNavigate()
  const { venueName } = useParams<{ venueName: string }>()

  // Load the public venues and match the URL slug to one of them. The matched
  // venue's id is what we submit the report against.
  const venues = usePublicVenues()
  const venue = findVenueBySlug(venues.data, venueName)
  const venueId = venue?.venueId
  const hasValidVenue = !!venueId
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

  // The backend split intake into two endpoints: create the report (JSON),
  // then upload the photo (multipart) against the returned id. Sequence them
  // here so the report is still created even if the photo upload fails.
  const createReport = useCreateLostReport()
  const uploadPhoto = useUpdateLostReportPhoto()

  const isPending = createReport.isPending || uploadPhoto.isPending
  const isError = createReport.isError || uploadPhoto.isError
  const error = createReport.error ?? uploadPhoto.error

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

  const onSubmit = async (data: ReportLostItemInput) => {
    if (!hasValidVenue) return
    const payload: CreateLostReportRequest = {
      description: data.description,
      contactEmail: data.contactEmail,
      // `datetime-local` already gives ISO-8601 local time
      // (`YYYY-MM-DDTHH:mm`); sent as-is so the user's wall-clock
      // intent is preserved instead of collapsed to UTC.
      lostAt: data.lostAt,
      venueId,
    }

    let report
    try {
      report = await createReport.mutateAsync({ data: payload })
    } catch {
      // creation failed; error is surfaced via `isError` below
      return
    }

    // Photo is optional and uploaded in a second request keyed by the new
    // report id. If it fails the report still exists, so surface the error
    // but don't block confirmation.
    let finalReport = report
    if (data.photo && report.id) {
      try {
        finalReport = await uploadPhoto.mutateAsync({
          id: report.id,
          data: { photo: data.photo },
        })
      } catch {
        // keep the created report; the photo error is shown via `isError`
      }
    }

    navigate('/confirmation', { state: { report: finalReport } })
  }

  return (
    <main className="mx-auto w-full max-w-xl p-6">
      <h1 className="mb-6 text-3xl font-medium text-text-h">
        Report a lost item
      </h1>

      {venues.isLoading && (
        <p className="mb-6 text-sm text-text">Loading venue…</p>
      )}

      {!venues.isLoading && !hasValidVenue && (
        <p className="mb-6 rounded border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-500">
          This report link is invalid. Please use the link or QR code provided at your venue.
        </p>
      )}

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
          disabled={!isValid || isPending || !hasValidVenue}
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
