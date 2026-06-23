import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { DayPicker } from 'react-day-picker'
import {
  useGetPublicSlots,
  useCreatePublicPickup,
} from '@/api/pickups/pickup-controller/pickup-controller'
import type { PickupSlotResponse } from '@/api/pickups/model'

// Group the flat slot list by calendar date so we can render a two-level
// picker: first the user picks a day, then a time within that day. This keeps
// the screen to one row of day pills plus a single day's worth of time
// buttons, instead of every slot across the whole booking window at once.
function groupSlotsByDate(slots: PickupSlotResponse[]): Map<string, PickupSlotResponse[]> {
  const grouped = new Map<string, PickupSlotResponse[]>()
  for (const slot of slots) {
    const date = slot.startsAt.slice(0, 10) // "YYYY-MM-DD"
    const existing = grouped.get(date) ?? []
    grouped.set(date, [...existing, slot])
  }
  return grouped
}

function formatDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
}

// Parse a "YYYY-MM-DD" key into a Date in local time (avoids the UTC shift
// that `new Date("YYYY-MM-DD")` applies).
function dateKeyToDate(key: string): Date {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function dateToKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatTime(isoDateTime: string): string {
  return new Date(isoDateTime).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function Shell({ children }: { children: React.ReactNode }) {
  return <main className="mx-auto w-full max-w-xl p-6">{children}</main>
}

export default function SchedulePickup() {
  const navigate = useNavigate()
  const { token } = useParams<{ token: string }>()

  const slots = useGetPublicSlots(token!)
  const createPickup = useCreatePublicPickup()

  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<PickupSlotResponse | null>(null)
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')

  const slotsByDate = useMemo(() => {
    const available = (slots.data ?? []).filter((s) => s.available)
    return groupSlotsByDate(available)
  }, [slots.data])

  const dates = useMemo(() => [...slotsByDate.keys()], [slotsByDate])

  // First day with availability — anchors the calendar's opening month.
  const firstDate = dates[0] ? dateKeyToDate(dates[0]) : undefined

  // Default to the first day with availability once the slots load.
  const activeDate = selectedDate ?? dates[0] ?? null
  const daySlots = activeDate ? (slotsByDate.get(activeDate) ?? []) : []

  const validateEmail = (value: string) => {
    if (!value) return 'Email is required'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return 'Please enter a valid email'
    return ''
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedSlot) return

    const error = validateEmail(email)
    if (error) {
      setEmailError(error)
      return
    }

    // Slots are UTC `Z`-suffixed; strip it for the backend's zoneless LocalDateTime.
    const pickupAt = selectedSlot.startsAt.replace('Z', '')

    const result = await createPickup.mutateAsync({
      token: token!,
      data: { pickupAt, email },
    })
    navigate('/pickup/confirmation', { state: { pickup: result } })
  }

  if (slots.isLoading) {
    return (
      <Shell>
        <p className="text-sm text-text">Loading available slots…</p>
      </Shell>
    )
  }

  if (slots.isError) {
    return (
      <Shell>
        <p className="rounded border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-500">
          This pickup link is invalid or has expired. Please contact the venue directly.
        </p>
      </Shell>
    )
  }

  if (dates.length === 0) {
    return (
      <Shell>
        <h1 className="mb-3 text-3xl font-medium text-text-h">Schedule pickup</h1>
        <p className="text-sm text-text">
          No pickup slots are currently available. Please contact the venue to arrange a time.
        </p>
      </Shell>
    )
  }

  return (
    <Shell>
      <h1 className="mb-2 text-center text-3xl font-medium text-text-h">Schedule pickup</h1>
      <p className="mb-8 text-center text-sm text-text">
        Choose a time to pick up your found item.
      </p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-8" noValidate>

        {/* Step 1: Pick a day */}
        <section className="flex justify-center">
          <DayPicker
            mode="single"
            required
            selected={activeDate ? dateKeyToDate(activeDate) : undefined}
            onSelect={(day) => {
              if (!day) return
              setSelectedDate(dateToKey(day))
              setSelectedSlot(null)
            }}
            disabled={(day) => !slotsByDate.has(dateToKey(day))}
            startMonth={firstDate}
            className="m-0"
          />
        </section>

        {/* Step 2: Pick a time within the chosen day */}
        <section>
          <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-text">
            Available times{activeDate ? ` · ${formatDate(activeDate)}` : ''}
          </h2>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
            {daySlots.map((slot) => {
              const active = selectedSlot?.startsAt === slot.startsAt
              return (
                <button
                  key={slot.startsAt}
                  type="button"
                  onClick={() => setSelectedSlot(slot)}
                  className={[
                    'rounded-lg border py-2 text-center text-sm transition-colors',
                    active
                      ? 'border-accent bg-accent text-white'
                      : 'border-border bg-transparent text-text-h hover:border-accent',
                  ].join(' ')}
                >
                  {formatTime(slot.startsAt)}
                </button>
              )
            })}
          </div>
        </section>

        {/* Step 3: Confirm email */}
        <section className="flex flex-col gap-1">
          <label htmlFor="email" className="text-sm font-medium text-text-h">
            Confirm your email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            placeholder="you@example.com"
            className="rounded border border-border bg-transparent p-3 outline-none focus:border-accent"
            onChange={(e) => {
              setEmail(e.target.value)
              setEmailError('')
            }}
          />
          {emailError && (
            <span className="text-sm text-red-500">{emailError}</span>
          )}
        </section>

        <div className="flex flex-col gap-3">
          {selectedSlot && (
            <p className="text-sm text-text">
              Selected:{' '}
              <span className="font-medium text-text-h">
                {formatDate(selectedSlot.startsAt.slice(0, 10))},{' '}
                {formatTime(selectedSlot.startsAt)}
                {' – '}
                {formatTime(selectedSlot.endsAt)}
              </span>
            </p>
          )}

          <button
            type="submit"
            disabled={!selectedSlot || createPickup.isPending}
            className="self-start rounded bg-accent px-5 py-2.5 font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
          >
            {createPickup.isPending ? 'Scheduling…' : 'Confirm pickup'}
          </button>

          {createPickup.isError && (
            <p className="text-sm text-red-500">
              {createPickup.error?.message ?? 'Something went wrong. Please try again.'}
            </p>
          )}
        </div>
      </form>
    </Shell>
  )
}
