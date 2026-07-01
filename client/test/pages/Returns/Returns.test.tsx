import { describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, within } from '@testing-library/react'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import {
  pickupsList,
  pickupsListError,
  schedulesList,
  schedulesListError,
  scheduleCreate,
  scheduleDelete,
  matchesList,
  lostReportsList,
  foundItemsList,
} from '@test/handlers'
import Returns from '@/pages/Returns/Returns'
import type { PickupResponse, PickupScheduleResponse } from '@/api/pickups/model'
import type { MatchResponse } from '@/api/matches/model'
import type { FoundItemResponse } from '@/api/found-items/model'

const M1 = 'a2222222-2222-2222-2222-222222222222'
const FI1 = 'f1111111-1111-1111-1111-111111111111'

const SCHEDULE: PickupScheduleResponse = {
  id: 's1111111-1111-1111-1111-111111111111',
  recurrenceType: 'WEEKLY',
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  dayOfWeek: 'MONDAY',
  startTime: '09:00:00',
  endTime: '17:00:00',
  slotLengthInMinutes: 15,
}

// One past pickup (must be filtered out) and one far-future one (must show).
const PICKUPS: PickupResponse[] = [
  {
    id: 'p0000000-0000-0000-0000-000000000000',
    pickupAt: '2020-01-01T10:00:00',
    matchId: M1,
    email: 'past@example.test',
  },
  {
    id: 'p1111111-1111-1111-1111-111111111111',
    pickupAt: '2099-06-05T14:30:00',
    matchId: M1,
    email: 'guest@example.test',
  },
]

const MATCHES: MatchResponse[] = [
  { id: M1, foundItemId: FI1, lostReportId: 'l1111111-1111-1111-1111-111111111111' },
]

const FOUND: FoundItemResponse[] = [
  { id: FI1, photoKey: 'found/fi1.jpg', attributes: { category: 'Blue Umbrella' } },
]

// Returns joins pickups → match → found/lost, so all four lists must be served.
function joins(matches = MATCHES, found = FOUND) {
  return [matchesList(matches), lostReportsList([]), foundItemsList(found)]
}

function seed(pickups = PICKUPS, schedules = [SCHEDULE]) {
  server.use(pickupsList(pickups), schedulesList(schedules), ...joins())
}

describe('<Returns />', () => {
  it('lists only upcoming pickups, enriched with the item to prepare, linking to the match', async () => {
    seed()
    renderWithProviders(<Returns />)

    // The joined found-item label is shown instead of a raw id.
    const label = await screen.findByText('Blue Umbrella')
    expect(screen.getByText('guest@example.test')).toBeInTheDocument()
    // The past pickup is filtered out.
    expect(screen.queryByText('past@example.test')).not.toBeInTheDocument()

    // The whole row links to the specific match card.
    expect(label.closest('a')).toHaveAttribute('href', `/matches#match-${M1}`)
  })

  it('renders the configured schedule windows', async () => {
    seed()
    renderWithProviders(<Returns />)

    const row = (await screen.findByText(/15-min slots/)).closest('li') as HTMLElement
    expect(within(row).getByText('Weekly')).toBeInTheDocument()
    expect(within(row).getByText(/Monday/)).toBeInTheDocument()
    expect(within(row).getByText(/09:00/)).toBeInTheDocument()
  })

  it('creates a schedule and sends a payload built from the generated client', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      pickupsList([]),
      schedulesList([]),
      ...joins([], []),
      scheduleCreate((b) => {
        body = b as Record<string, unknown>
      }),
    )
    const { user } = renderWithProviders(<Returns />)

    await screen.findByText(/No schedules yet/i)

    // Only the start date is empty in the defaults; fill it to make the form valid.
    fireEvent.change(screen.getByLabelText('Start date'), {
      target: { value: '2099-01-15' },
    })
    await user.click(screen.getByRole('button', { name: 'Add schedule' }))

    expect(await screen.findByText('Schedule created.')).toBeInTheDocument()
    expect(body).toMatchObject({
      recurrenceType: 'WEEKLY',
      startDate: '2099-01-15',
      dayOfWeek: 'MONDAY',
      startTime: '09:00',
      endTime: '17:00',
      slotLengthInMinutes: 15,
    })
    // Optional fields are omitted, not sent empty.
    expect(body).not.toHaveProperty('endDate')
    expect(body).not.toHaveProperty('venueId')
  })

  it('deletes a schedule', async () => {
    const onDelete = vi.fn()
    server.use(pickupsList([]), schedulesList([SCHEDULE]), ...joins([], []), scheduleDelete(onDelete))
    const { user } = renderWithProviders(<Returns />)

    const row = (await screen.findByText(/15-min slots/)).closest('li') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /delete schedule/i }))

    expect(await screen.findByText('Schedule deleted.')).toBeInTheDocument()
    expect(onDelete).toHaveBeenCalledWith(SCHEDULE.id)
  })

  it('shows empty and error states', async () => {
    server.use(pickupsList([]), schedulesList([]), ...joins([], []))
    const { unmount } = renderWithProviders(<Returns />)
    expect(await screen.findByText(/No upcoming pickups booked/i)).toBeInTheDocument()
    expect(screen.getByText(/No schedules yet/i)).toBeInTheDocument()
    unmount()

    server.use(pickupsListError(), schedulesListError(), ...joins([], []))
    renderWithProviders(<Returns />)
    expect(await screen.findByText(/Couldn't load pickups/i)).toBeInTheDocument()
    expect(screen.getByText(/Couldn't load schedules/i)).toBeInTheDocument()
  })
})
