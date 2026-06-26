import { Navigate, useLocation } from 'react-router-dom'
import type { PublicPickupResponse } from '@/api/pickups/model'

type ConfirmationState = { pickup?: PublicPickupResponse }

function formatPickupAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function PickupConfirmation() {
  const location = useLocation()
  const state = location.state as ConfirmationState | null
  const pickup = state?.pickup

  if (!pickup?.id) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="mx-auto w-full max-w-xl p-6 animate-[foundItemFadeIn_300ms_ease-out_both]">
      <div className="mb-2 text-sm font-medium uppercase tracking-wider text-accent">
        ✓ Pickup scheduled
      </div>
      <h1 className="mb-3 text-3xl font-medium text-text-h">
        See you then!
      </h1>
      <p className="mb-8 text-sm text-text">
        A confirmation has been sent to{' '}
        <span className="font-medium text-text-h">{pickup.email}</span>.
      </p>

      <dl className="flex flex-col divide-y divide-border border-y border-border">
        <div className="flex flex-col gap-1 py-4">
          <dt className="text-xs font-medium uppercase tracking-wider text-text">
            Pickup time
          </dt>
          <dd className="text-sm text-text-h">{formatPickupAt(pickup.pickupAt)}</dd>
        </div>
        <div className="flex flex-col gap-1 py-4">
          <dt className="text-xs font-medium uppercase tracking-wider text-text">
            Booking reference
          </dt>
          <dd className="font-mono text-sm text-text-h">#{pickup.id}</dd>
        </div>
        <div className="flex flex-col gap-1 py-4">
          <dt className="text-xs font-medium uppercase tracking-wider text-text">
            Contact email
          </dt>
          <dd className="text-sm text-text-h">{pickup.email}</dd>
        </div>
      </dl>
    </main>
  )
}
