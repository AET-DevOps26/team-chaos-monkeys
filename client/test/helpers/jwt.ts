// Mints an unsigned JWT-shaped string for tests. AuthContext.decodeJwt only
// reads claims; it does not validate the signature, so the trailing segment
// is left empty.
export function makeFakeJwt(claims: Record<string, unknown> = { sub: 'user-1', roles: ['staff'] }): string {
  const payload = btoa(JSON.stringify(claims))
    .replace(/=+$/, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
  return `eyJhbGciOiJub25lIn0.${payload}.`
}
