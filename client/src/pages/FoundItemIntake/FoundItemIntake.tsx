import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useUpdateFoundItemPhoto } from '@/api/found-items/found-item-controller/found-item-controller'
import { useGetAllVenues } from '@/api/operations/venue-controller/venue-controller'
import type { CreateFoundItemRequest, FoundItemResponse } from '@/api/found-items/model'
import { customInstance } from '@/api/mutator/custom-instance'
import { useAuth } from '@/auth/useAuth'
import { useToast } from '@/components/Toast/toast-context'
import { foundItemIntakeSchema, type FoundItemIntakeInput } from './schema'
import uploadIcon from '@/assets/upload.svg'

// The backend enriches the item synchronously during the photo upload; an
// empty attributes block in the response means extraction produced nothing
// and the item won't surface in matching until details are added (#307).
function hasExtractedAttributes(item: FoundItemResponse): boolean {
  const attrs = item.attributes
  if (!attrs) return false
  return !!(
    attrs.category ||
    attrs.description ||
    attrs.brand ||
    attrs.color ||
    attrs.marks?.length
  )
}

function nowForDatetimeLocal() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const inputCls =
  'rounded border border-border bg-transparent p-2 text-sm outline-none focus:border-accent'
const labelCls = 'text-xs font-medium text-text-h'

function UploadPrompt() {
  return (
    <div className="flex flex-col items-center gap-2 text-text-h">
      <span
        aria-hidden="true"
        className="h-10 w-10 bg-current opacity-70"
        style={{
          maskImage: `url("${uploadIcon}")`,
          WebkitMaskImage: `url("${uploadIcon}")`,
          maskSize: 'contain',
          WebkitMaskSize: 'contain',
          maskRepeat: 'no-repeat',
          WebkitMaskRepeat: 'no-repeat',
          maskPosition: 'center',
          WebkitMaskPosition: 'center',
        }}
      />
      <span className="text-sm">Click to upload an image</span>
    </div>
  )
}

export default function FoundItemIntake() {
  const defaultFoundAt = useMemo(nowForDatetimeLocal, [])
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isValid },
  } = useForm<FoundItemIntakeInput>({
    resolver: zodResolver(foundItemIntakeSchema),
    mode: 'onChange',
    defaultValues: {
      intakeText: '',
      foundAt: defaultFoundAt,
      photo: null,
    },
  })

  const photo = watch('photo')
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isCreatingItem, setIsCreatingItem] = useState(false)

  const { user } = useAuth()
  const isAdmin = !!user?.roles.includes('ADMIN')
  const [venueId, setVenueId] = useState('')
  // Staff are bound to a venue via their JWT (the backend derives it and ignores
  // any request venueId). Admins have no venue claim and must pick a real one —
  // sending a placeholder lands the item in a non-existent venue that can never
  // match (#352).
  const venuesQuery = useGetAllVenues({ query: { enabled: isAdmin } })
  const venues = venuesQuery.data ?? []
  const { show } = useToast()
  const updateFoundItemPhoto = useUpdateFoundItemPhoto()

  useEffect(() => {
    if (!photo) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(photo)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [photo])

  const onSubmit = async (data: FoundItemIntakeInput) => {
    setSubmitError(null)
    if (!data.photo) {
      setSubmitError('Photo is required.')
      return
    }
    const effectiveVenueId = isAdmin ? venueId : user?.venueId ?? ''
    if (!effectiveVenueId) {
      setSubmitError('Select a venue.')
      return
    }
    const payload: CreateFoundItemRequest = {
      // Operators only supply free-text notes; the GenAI enrichment derives
      // attributes and location during the subsequent photo upload.
      intakeText: data.intakeText?.trim() || undefined,
      // datetime-local already yields a zone-less value (YYYY-MM-DDTHH:mm);
      // send it as-is so it binds to the backend LocalDateTime (no trailing Z).
      foundAt: data.foundAt,
      venueId: effectiveVenueId,
      reporterId: user?.sub ?? '',
    }

    setIsCreatingItem(true)
    try {
      const created = await customInstance<FoundItemResponse>({
        url: '/api/found-items',
        method: 'POST',
        data: payload,
      })
      if (!created.id) {
        throw new Error('Failed to create found item.')
      }
      const updated = await updateFoundItemPhoto.mutateAsync({
        id: created.id,
        data: { photo: data.photo },
      })
      reset({
        intakeText: '',
        foundAt: nowForDatetimeLocal(),
        photo: null,
      })
      if (hasExtractedAttributes(updated)) {
        show('Found item logged successfully.', { variant: 'success' })
      } else {
        show(
          'Item saved, but automatic attribute extraction was unavailable — add notes or attributes so it can be matched.',
          { variant: 'warning' },
        )
      }
    } catch (err) {
      show(err instanceof Error ? err.message : 'Failed to log found item.', {
        variant: 'error',
      })
    } finally {
      setIsCreatingItem(false)
    }
  }

  const hasPhoto = !!previewUrl
  const isSubmitting = isCreatingItem || updateFoundItemPhoto.isPending

  return (
    <main className="mx-auto flex min-h-[calc(100vh-3.5rem)] w-full max-w-2xl flex-col p-4">
      <form onSubmit={handleSubmit(onSubmit)} className="flex min-h-0 flex-1 flex-col gap-3" noValidate>
        <input
          id="photo"
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0] ?? null
            setValue('photo', file, { shouldValidate: true })
          }}
        />

        {!hasPhoto ? (
          <label
            htmlFor="photo"
            className="group flex h-64 w-full cursor-pointer items-center justify-center rounded border border-dashed border-border hover:border-accent"
          >
            <UploadPrompt />
          </label>
        ) : (
          <div
            key={previewUrl}
            className="flex min-h-0 flex-1 flex-col gap-3 [&>*]:animate-[foundItemFadeIn_350ms_ease-out_both] [&>*]:opacity-0"
          >
            <label
              htmlFor="photo"
              style={{ animationDelay: '0ms' }}
              className="group relative flex h-64 cursor-pointer items-center justify-center overflow-hidden rounded border border-dashed border-border hover:border-accent"
            >
              <img
                src={previewUrl!}
                alt="Selected preview"
                className="h-full w-full object-contain"
              />
              <span className="absolute inset-0 hidden items-center justify-center bg-black/40 text-xs text-white group-hover:flex">
                Replace image
              </span>
            </label>

            <div className="flex flex-col gap-1" style={{ animationDelay: '150ms' }}>
              <label htmlFor="intakeText" className={labelCls}>Notes</label>
              <textarea
                id="intakeText"
                rows={3}
                className={inputCls}
                placeholder="Anything that helps identify or locate the item — colour, brand, where it was found…"
                {...register('intakeText')}
              />
              {errors.intakeText && (
                <span className="text-xs text-red-500">{errors.intakeText.message}</span>
              )}
            </div>

            <div className="flex flex-col gap-1" style={{ animationDelay: '300ms' }}>
              <label htmlFor="foundAt" className={labelCls}>Found at</label>
              <input id="foundAt" type="datetime-local" className={inputCls} {...register('foundAt')} />
              {errors.foundAt && (
                <span className="text-xs text-red-500">{errors.foundAt.message}</span>
              )}
            </div>

            {isAdmin && (
              <div className="flex flex-col gap-1" style={{ animationDelay: '450ms' }}>
                <label htmlFor="venueId" className={labelCls}>Venue</label>
                <select
                  id="venueId"
                  className={inputCls}
                  value={venueId}
                  onChange={(e) => setVenueId(e.target.value)}
                >
                  <option value="" disabled>Select a venue…</option>
                  {venues.map((venue) => (
                    <option key={venue.id} value={venue.id ?? ''}>
                      {venue.name ?? venue.id}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="flex flex-col gap-1" style={{ animationDelay: '600ms' }}>
              {submitError && (
                <span className="text-xs text-red-500">{submitError}</span>
              )}
              <button
                type="submit"
                disabled={!isValid || isSubmitting || (isAdmin && !venueId)}
                className="w-full rounded bg-accent px-4 py-2.5 text-sm font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isSubmitting ? 'Logging…' : 'Log found item'}
              </button>
            </div>
          </div>
        )}
      </form>
    </main>
  )
}
