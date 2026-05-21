import { useEffect, useMemo, useState } from 'react'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useCreateFoundItem } from '@/api/found-items/found-item-controller/found-item-controller'
import type { CreateFoundItemRequest } from '@/api/found-items/model'
import { useAuth } from '@/auth/useAuth'
import { foundItemIntakeSchema, type FoundItemIntakeInput } from './schema'

// The backend requires UUID values
// The auth-service `sub` is an email, not a UUID, hence the guard.
// TODO: wire real venue/reporter UUIDs. until the backend change items are written with the
// nil UUID, collapsing all records under one fake venue/reporter.
// tracked in issue #120
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
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-10 w-10 opacity-70"
        aria-hidden="true"
      >
        <path d="M12 16V4" />
        <path d="M7 9l5-5 5 5" />
        <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
      </svg>
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
    control,
    reset,
    formState: { errors, isValid },
  } = useForm<FoundItemIntakeInput>({
    resolver: zodResolver(foundItemIntakeSchema),
    mode: 'onChange',
    defaultValues: {
      description: '',
      foundAt: defaultFoundAt,
      locationHint: '',
      category: '',
      brand: '',
      color: '',
      marks: [],
      photo: null,
    },
  })

  const { fields: markFields, append: appendMark, remove: removeMark } = useFieldArray({
    control,
    name: 'marks',
  })

  const photo = watch('photo')
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [showSuccess, setShowSuccess] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const { user } = useAuth()
  const createFoundItem = useCreateFoundItem()

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
    const marks = data.marks.map((m) => m.value.trim()).filter(Boolean)
    const attributes = {
      category: data.category || undefined,
      brand: data.brand || undefined,
      color: data.color || undefined,
      marks: marks.length > 0 ? marks : undefined,
    }
    const hasAttributes = Object.values(attributes).some((v) => v !== undefined)

    // TODO: upload data.photo to object storage and pass the returned key as photoKey
    const payload: CreateFoundItemRequest = {
      description: data.description || undefined,
      // datetime-local already yields a zone-less value (YYYY-MM-DDTHH:mm);
      // send it as-is so it binds to the backend LocalDateTime (no trailing Z).
      foundAt: data.foundAt,
      locationHint: data.locationHint || undefined,
      venueId: asUuidOrPlaceholder(user?.venueId),
      reporterId: asUuidOrPlaceholder(user?.sub),
      attributes: hasAttributes ? attributes : undefined,
    }

    setSubmitError(null)
    try {
      await createFoundItem.mutateAsync({ data: payload })
      reset({
        description: '',
        foundAt: nowForDatetimeLocal(),
        locationHint: '',
        category: '',
        brand: '',
        color: '',
        marks: [],
        photo: null,
      })
      setShowSuccess(true)
      window.setTimeout(() => setShowSuccess(false), 3000)
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to log found item.')
    }
  }

  const hasPhoto = !!previewUrl

  return (
    <main className="mx-auto flex h-screen w-full max-w-4xl flex-col p-4">
      <form onSubmit={handleSubmit(onSubmit)} className="flex min-h-0 flex-1 flex-col gap-3" noValidate>
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-medium text-text-h">Log a found item</h1>
          <div className="flex items-center gap-3">
            {submitError && (
              <span className="text-xs text-red-500">{submitError}</span>
            )}
            {showSuccess && (
              <span className="animate-[foundItemFadeIn_300ms_ease-out_both] text-xs text-accent">
                Found item logged successfully.
              </span>
            )}
            {hasPhoto && (
              <button
                type="submit"
                disabled={!isValid || createFoundItem.isPending}
                className="rounded bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
              >
                {createFoundItem.isPending ? 'Logging…' : 'Log found item'}
              </button>
            )}
          </div>
        </div>

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
            className="group flex h-64 w-full cursor-pointer items-center justify-center rounded border border-dashed border-border hover:border-accent md:w-[calc(50%-0.375rem)]"
          >
            <UploadPrompt />
          </label>
        ) : (
          <div
            key={previewUrl}
            className="grid min-h-0 flex-1 grid-cols-1 gap-3 md:grid-cols-2 [&>*>*]:animate-[foundItemFadeIn_350ms_ease-out_both] [&>*>*]:opacity-0"
          >
            <div className="flex min-h-0 flex-col gap-3">
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

              <div className="flex flex-col gap-1" style={{ animationDelay: '200ms' }}>
                <label htmlFor="description" className={labelCls}>Description</label>
                <textarea
                  id="description"
                  rows={2}
                  className={inputCls}
                  placeholder="e.g. Black leather wallet, found on a lobby couch"
                  {...register('description')}
                />
                {errors.description && (
                  <span className="text-xs text-red-500">{errors.description.message}</span>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3" style={{ animationDelay: '400ms' }}>
                <div className="flex flex-col gap-1">
                  <label htmlFor="foundAt" className={labelCls}>Found at</label>
                  <input id="foundAt" type="datetime-local" className={inputCls} {...register('foundAt')} />
                  {errors.foundAt && (
                    <span className="text-xs text-red-500">{errors.foundAt.message}</span>
                  )}
                </div>
                <div className="flex flex-col gap-1">
                  <label htmlFor="locationHint" className={labelCls}>Location hint</label>
                  <input
                    id="locationHint"
                    type="text"
                    className={inputCls}
                    placeholder="Lobby couch"
                    {...register('locationHint')}
                  />
                </div>
              </div>
            </div>

            <div className="flex min-h-0 flex-col gap-3">
              <div className="grid grid-cols-3 gap-2" style={{ animationDelay: '100ms' }}>
                <div className="flex flex-col gap-1">
                  <label htmlFor="category" className={labelCls}>Category</label>
                  <input id="category" type="text" className={inputCls} placeholder="wallet" {...register('category')} />
                </div>
                <div className="flex flex-col gap-1">
                  <label htmlFor="brand" className={labelCls}>Brand</label>
                  <input id="brand" type="text" className={inputCls} {...register('brand')} />
                </div>
                <div className="flex flex-col gap-1">
                  <label htmlFor="color" className={labelCls}>Color</label>
                  <input id="color" type="text" className={inputCls} {...register('color')} />
                </div>
              </div>

              <div className="flex min-h-0 flex-1 flex-col gap-1" style={{ animationDelay: '300ms' }}>
                <span className={labelCls}>Marks</span>
                <div className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto pr-1">
                  {markFields.length === 0 && (
                    <span className="text-xs text-text-h/60">No marks added.</span>
                  )}
                  {markFields.map((field, index) => (
                    <div key={field.id} className="flex gap-2">
                      <input
                        type="text"
                        className={`${inputCls} flex-1`}
                        placeholder="e.g. monogrammed AG"
                        {...register(`marks.${index}.value`)}
                      />
                      <button
                        type="button"
                        onClick={() => removeMark(index)}
                        className="rounded border border-border px-2 text-xs text-text-h hover:border-accent"
                        aria-label={`Remove mark ${index + 1}`}
                      >
                        ×
                      </button>
                    </div>
                  ))}
                  <button
                    type="button"
                    onClick={() => appendMark({ value: '' })}
                    className="mt-1 self-start rounded border border-border px-2 py-1 text-xs text-text-h hover:border-accent"
                  >
                    + Add mark
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </form>
    </main>
  )
}
