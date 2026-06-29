const shortDateFmt = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

/** Format an ISO date string as e.g. "Jun 29, 2026"; empty string for missing/invalid input. */
export function formatDate(value: string | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return shortDateFmt.format(d)
}

/** First non-empty line of a multi-line text, trimmed; undefined if empty/missing. */
export function firstLine(text: string | undefined): string | undefined {
  return text?.split(/\r?\n/)[0]?.trim() || undefined
}
