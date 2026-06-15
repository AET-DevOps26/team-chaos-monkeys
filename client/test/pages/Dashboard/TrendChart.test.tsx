import { describe, it, expect } from 'vitest'
import { isWithinWindow } from '@/pages/Dashboard/TrendChart'

// Fixed "now" so the assertions don't drift with the wall clock.
const NOW = Date.parse('2026-05-31T12:00:00Z')

function daysAgo(n: number): string {
  return new Date(NOW - n * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
}

describe('isWithinWindow', () => {
  it('keeps buckets inside the trailing window per granularity', () => {
    expect(isWithinWindow(daysAgo(3), 'perDay', NOW)).toBe(true)
    expect(isWithinWindow(daysAgo(20), 'perWeek', NOW)).toBe(true)
    expect(isWithinWindow(daysAgo(200), 'perMonth', NOW)).toBe(true)
  })

  it('drops buckets older than the window', () => {
    expect(isWithinWindow(daysAgo(10), 'perDay', NOW)).toBe(false)
    expect(isWithinWindow(daysAgo(45), 'perWeek', NOW)).toBe(false)
    expect(isWithinWindow(daysAgo(400), 'perMonth', NOW)).toBe(false)
  })

  it('drops ancient and future-dated outliers regardless of granularity', () => {
    expect(isWithinWindow('1212-01-01', 'perDay', NOW)).toBe(false)
    expect(isWithinWindow('1212-01-01', 'perWeek', NOW)).toBe(false)
    expect(isWithinWindow('1212-01-01', 'perMonth', NOW)).toBe(false)
    expect(isWithinWindow(daysAgo(-5), 'perMonth', NOW)).toBe(false)
  })

  it('treats unparseable bucket dates as outside the window', () => {
    expect(isWithinWindow('not-a-date', 'perDay', NOW)).toBe(false)
  })
})
