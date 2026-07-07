import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { renderWithProviders } from '@test/render'
import { server } from '@test/server'
import { makeFakeJwt } from '@test/jwt'
import FoundItemIntake from '@/pages/FoundItemIntake/FoundItemIntake'
import type { FoundItemResponse, ItemAttributesDto } from '@/api/found-items/model'

const ITEM_ID = 'f1111111-1111-1111-1111-111111111111'

const foundItemCreate = () =>
  http.post('*/api/found-items', () =>
    HttpResponse.json<FoundItemResponse>({ id: ITEM_ID }, { status: 201 }),
  )

const foundItemPhotoUpdate = (attributes?: ItemAttributesDto) =>
  http.put('*/api/found-items/:id/photo', () =>
    HttpResponse.json<FoundItemResponse>({ id: ITEM_ID, attributes }),
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

async function fillAndSubmit() {
  const { user } = renderWithProviders(<FoundItemIntake />, {
    authToken: makeFakeJwt(),
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
})
