import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useUpdateFoundItemPhoto } from '@/api/found-items/found-item-controller/found-item-controller'
import type { CreateFoundItemRequest, FoundItemResponse } from '@/api/found-items/model'
import { customInstance } from '@/api/mutator/custom-instance'
import { useAuth } from '@/auth/useAuth'
import { useToast } from '@/components/Toast/toast-context'
import { foundItemIntakeSchema, type FoundItemIntakeInput } from './schema'
import uploadIcon from '@/assets/upload.svg'

// The create DTO still carries a venue UUID. For non-admin users the backend
// applies the JWT venue, but this keeps the request shape valid while auth
// state is still hydrating.
const PLACEHOLDER_UUID = '00000000-0000-0000-0000-000000000000'
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const asUuidOrPlaceholder = (value: string | null | undefined): string =>
  value && UUID_RE.test(value) ? value : PLACEHOLDER_UUID

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
    const payload: CreateFoundItemRequest = {
      // Operators only supply free-text notes; the GenAI enrichment job derives
      // attributes and location asynchronously after the item is created.
      intakeText: data.intakeText?.trim() || undefined,
      // datetime-local already yields a zone-less value (YYYY-MM-DDTHH:mm);
      // send it as-is so it binds to the backend LocalDateTime (no trailing Z).
      foundAt: data.foundAt,
      venueId: asUuidOrPlaceholder(user?.venueId),
      reporterId: asUuidOrPlaceholder(user?.sub),
    }

    setSubmitError(null)
    if (!data.photo) {
      setSubmitError('Photo is required.')
      return
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
      await updateFoundItemPhoto.mutateAsync({ id: created.id, data: { photo: data.photo } })
      reset({
        intakeText: '',
        foundAt: nowForDatetimeLocal(),
        photo: null,
      })
      show('Found item logged successfully.', { variant: 'success' })
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

            <div className="flex flex-col gap-1" style={{ animationDelay: '450ms' }}>
              {submitError && (
                <span className="text-xs text-red-500">{submitError}</span>
              )}
              <button
                type="submit"
                disabled={!isValid || isSubmitting}
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
