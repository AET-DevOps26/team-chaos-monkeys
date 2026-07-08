export type Granularity = 'perDay' | 'perWeek' | 'perMonth'

// How far back each granularity looks, in days. Buckets are keyed on
// user-entered event dates (lostAt/foundAt), so back-dated or bogus values
// (e.g. a lostAt in the year 1212) would otherwise pollute the timeline. We
// only ever plot a trailing window ending today, which keeps the chart sane
// and drops both ancient and future-dated outliers.
const WINDOW_DAYS: Record<Granularity, number> = {
  perDay: 7,
  perWeek: 30,
  perMonth: 365,
}

const DAY_MS = 24 * 60 * 60 * 1000

export function isWithinWindow(
  bucketStart: string,
  granularity: Granularity,
  now: number = Date.now(),
): boolean {
  const ms = Date.parse(bucketStart)
  if (Number.isNaN(ms)) return false
  return ms >= now - WINDOW_DAYS[granularity] * DAY_MS && ms <= now
}
