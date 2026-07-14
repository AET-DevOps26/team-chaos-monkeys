let counter = 0

/**
 * Client-only unique id for React list keys.
 * crypto.randomUUID is unavailable outside secure contexts (plain-HTTP Azure deploys),
 * and these ids never leave the browser, so a counter is enough.
 */
export function uid(): string {
  return `${Date.now().toString(36)}-${counter++}`
}
