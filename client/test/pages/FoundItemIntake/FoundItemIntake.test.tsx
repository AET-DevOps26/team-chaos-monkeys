import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import { makeFakeJwt } from '@test/jwt'
import FoundItemIntake from '@/pages/FoundItemIntake/FoundItemIntake'
import type { CreateFoundItemRequest, FoundItemResponse, ItemAttributesDto } from '@/api/found-items/model'
import type { VenueResponse } from '@/api/operations/model'

const ITEM_ID = 'f1111111-1111-1111-1111-111111111111'
const STAFF_VENUE_ID = 'a0000000-0000-0000-0000-000000000001'
const ADMIN_VENUE_ID = 'b0000000-0000-0000-0000-000000000002'

const foundItemCreate = (onBody?: (body: CreateFoundItemRequest) => void) =>
  http.post('*/api/found-items', async ({ request }) => {
    onBody?.((await request.json()) as CreateFoundItemRequest)
    return HttpResponse.json<FoundItemResponse>({ id: ITEM_ID }, { status: 201 })
  })

const foundItemPhotoUpdate = (attributes?: ItemAttributesDto) =>
  http.put('*/api/found-items/:id/photo', () =>
    HttpResponse.json<FoundItemResponse>({ id: ITEM_ID, attributes }),
  )

const venuesList = () =>
  http.get('*/api/venues', () =>
    HttpResponse.json<VenueResponse[]>([{ id: ADMIN_VENUE_ID, name: 'Grand Plaza' }]),
  )

beforeEach(() => {
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:photo'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  })
})

const staffToken = () =>
  makeFakeJwt({ sub: 'user-1', roles: ['STAFF'], venue_id: STAFF_VENUE_ID })
const adminToken = () => makeFakeJwt({ sub: 'admin-1', roles: ['ADMIN'] })

async function fillAndSubmit() {
  const { user } = renderWithProviders(<FoundItemIntake />, {
    authToken: staffToken(),
  })
  const file = new File(['jpeg-bytes'], 'photo.jpg', { type: 'image/jpeg' })
  await user.upload(screen.getByLabelText(/click to upload an image/i), file)
  await user.click(await screen.findByRole('button', { name: /log found item/i }))
}

describe('<FoundItemIntake />', () => {
  it('shows a success toast when extraction produced attributes', async () => {
    server.use(
      foundItemCreate(),
      foundItemPhotoUpdate({ category: 'wallet', color: 'red' }),
    )

    await fillAndSubmit()

    expect(await screen.findByText('Found item logged successfully.')).toBeInTheDocument()
  })

  it('warns when the item was saved without extracted attributes', async () => {
    server.use(foundItemCreate(), foundItemPhotoUpdate(undefined))

    await fillAndSubmit()

    expect(
      await screen.findByText(/attribute extraction was unavailable/i),
    ).toBeInTheDocument()
    expect(screen.queryByText('Found item logged successfully.')).not.toBeInTheDocument()
  })

  it('warns when extraction returned only empty attribute fields', async () => {
    server.use(foundItemCreate(), foundItemPhotoUpdate({ marks: [] }))

    await fillAndSubmit()

    expect(
      await screen.findByText(/attribute extraction was unavailable/i),
    ).toBeInTheDocument()
  })

  it('sends the JWT venue for a staff user (no venue picker)', async () => {
    let body: CreateFoundItemRequest | undefined
    server.use(
      foundItemCreate((b) => (body = b)),
      foundItemPhotoUpdate({ category: 'wallet' }),
    )

    await fillAndSubmit()

    expect(await screen.findByText('Found item logged successfully.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Venue')).not.toBeInTheDocument()
    expect(body?.venueId).toBe(STAFF_VENUE_ID)
  })

  it('requires an admin to pick a real venue before submitting', async () => {
    let body: CreateFoundItemRequest | undefined
    server.use(
      venuesList(),
      foundItemCreate((b) => (body = b)),
      foundItemPhotoUpdate({ category: 'wallet' }),
    )

    const { user } = renderWithProviders(<FoundItemIntake />, { authToken: adminToken() })
    const file = new File(['jpeg-bytes'], 'photo.jpg', { type: 'image/jpeg' })
    await user.upload(screen.getByLabelText(/click to upload an image/i), file)

    // Submit is blocked until a venue is chosen — no more all-zeros placeholder.
    expect(await screen.findByRole('button', { name: /log found item/i })).toBeDisabled()

    await screen.findByRole('option', { name: 'Grand Plaza' })
    await user.selectOptions(await screen.findByLabelText('Venue'), ADMIN_VENUE_ID)
    await user.click(screen.getByRole('button', { name: /log found item/i }))

    expect(await screen.findByText('Found item logged successfully.')).toBeInTheDocument()
    expect(body?.venueId).toBe(ADMIN_VENUE_ID)
  })
})
