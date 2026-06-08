import type { VenueResponse } from '@/api/operations/model'

type VenuePickerProps = {
  venues: VenueResponse[]
  value: string | null
  onChange: (venueId: string | null) => void
}

export default function VenuePicker({
  venues,
  value,
  onChange,
}: VenuePickerProps) {
  return (
    <label className="flex items-center gap-2 text-sm text-text">
      <span className="font-medium">Venue</span>
      <select
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        className="rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-h"
      >
        <option value="">All venues</option>
        {venues.map((venue) => (
          <option key={venue.id} value={venue.id}>
            {venue.name ?? venue.id}
          </option>
        ))}
      </select>
    </label>
  )
}
