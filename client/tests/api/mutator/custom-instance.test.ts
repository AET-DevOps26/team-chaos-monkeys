import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { customInstance } from './custom-instance'
import { setCurrentToken, onUnauthorized } from '@/auth/token-store'

describe('customInstance', () => {
  it('attaches the Bearer token from the token store on outgoing requests', async () => {
    setCurrentToken('abc-123')
    const seen = vi.fn<(authHeader: string | null) => void>()
    server.use(
      http.get('*/api/echo', ({ request }) => {
        seen(request.headers.get('authorization'))
        return HttpResponse.json({ ok: true })
      }),
    )

    const data = await customInstance<{ ok: boolean }>({ url: '/api/echo', method: 'GET' })
    expect(data).toEqual({ ok: true })
    expect(seen).toHaveBeenCalledWith('Bearer abc-123')
  })

  it('omits the Authorization header when no token is set', async () => {
    const seen = vi.fn<(authHeader: string | null) => void>()
    server.use(
      http.get('*/api/echo', ({ request }) => {
        seen(request.headers.get('authorization'))
        return HttpResponse.json({ ok: true })
      }),
    )

    await customInstance<{ ok: boolean }>({ url: '/api/echo', method: 'GET' })
    expect(seen).toHaveBeenCalledWith(null)
  })

  it('dispatches the unauthorized event on a 401 response', async () => {
    server.use(
      http.get('*/api/needs-auth', () => HttpResponse.json({}, { status: 401 })),
    )
    const handler = vi.fn()
    const off = onUnauthorized(handler)

    await expect(
      customInstance({ url: '/api/needs-auth', method: 'GET' }),
    ).rejects.toMatchObject({ response: { status: 401 } })
    expect(handler).toHaveBeenCalledTimes(1)
    off()
  })
})
