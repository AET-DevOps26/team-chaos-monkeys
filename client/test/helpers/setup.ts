import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { server } from './server'
import { setCurrentToken, setRefreshToken } from '@/auth/token-store'

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' })
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  setCurrentToken(null)
  setRefreshToken(null)
})

afterAll(() => {
  server.close()
})
