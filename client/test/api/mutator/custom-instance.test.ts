import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@test/server'
import { refreshSuccess } from '@test/handlers'
import { customInstance } from '@/api/mutator/custom-instance'
import { getRefreshToken, onUnauthorized, setCurrentToken, setRefreshToken } from '@/auth/token-store'

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

  it('refreshes once on a 401 and replays the original request with the new token', async () => {
    setRefreshToken('stored-refresh')
    server.use(
      refreshSuccess('new-access-token'),
      http.get('*/api/needs-auth', ({ request }) => {
        if (request.headers.get('authorization') === 'Bearer new-access-token') {
          return HttpResponse.json({ ok: true })
        }
        return HttpResponse.json({}, { status: 401 })
      }),
    )

    const data = await customInstance<{ ok: boolean }>({ url: '/api/needs-auth', method: 'GET' })
    expect(data).toEqual({ ok: true })
  })

  it('collapses a burst of concurrent 401s into a single refresh', async () => {
    setRefreshToken('stored-refresh')
    const refreshCalls = vi.fn()
    server.use(
      http.post('*/api/auth/refresh', () => {
        refreshCalls()
        return HttpResponse.json({
          accessToken: 'new-access-token',
          refreshToken: 'rotated-refresh',
          tokenType: 'Bearer',
          expiresIn: 3600,
        })
      }),
      http.get('*/api/needs-auth', ({ request }) =>
        request.headers.get('authorization') === 'Bearer new-access-token'
          ? HttpResponse.json({ ok: true })
          : HttpResponse.json({}, { status: 401 }),
      ),
    )

    const results = await Promise.all([
      customInstance<{ ok: boolean }>({ url: '/api/needs-auth', method: 'GET' }),
      customInstance<{ ok: boolean }>({ url: '/api/needs-auth', method: 'GET' }),
      customInstance<{ ok: boolean }>({ url: '/api/needs-auth', method: 'GET' }),
    ])

    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }])
    expect(refreshCalls).toHaveBeenCalledTimes(1)
  })

  it('dispatches unauthorized when the refresh itself fails on a 401', async () => {
    setRefreshToken('stored-refresh')
    server.use(
      http.post('*/api/auth/refresh', () => HttpResponse.json({}, { status: 401 })),
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

  it('does not tear down the session when the refresh fails transiently (5xx)', async () => {
    setRefreshToken('stored-refresh')
    server.use(
      http.post('*/api/auth/refresh', () => HttpResponse.json({}, { status: 503 })),
      http.get('*/api/needs-auth', () => HttpResponse.json({}, { status: 401 })),
    )
    const handler = vi.fn()
    const off = onUnauthorized(handler)

    await expect(
      customInstance({ url: '/api/needs-auth', method: 'GET' }),
    ).rejects.toMatchObject({ response: { status: 401 } })
    // Transient auth-service failure: the refresh token may still be valid, so
    // it is kept and no logout is triggered.
    expect(handler).not.toHaveBeenCalled()
    expect(getRefreshToken()).toBe('stored-refresh')
    off()
  })

  it('does not refresh or dispatch unauthorized on a 401 from the login endpoint', async () => {
    server.use(
      http.post('*/api/auth/login', () => HttpResponse.json({}, { status: 401 })),
    )
    const handler = vi.fn()
    const off = onUnauthorized(handler)

    await expect(
      customInstance({ url: '/api/auth/login', method: 'POST', data: {} }),
    ).rejects.toMatchObject({ response: { status: 401 } })
    expect(handler).not.toHaveBeenCalled()
    off()
  })
})
