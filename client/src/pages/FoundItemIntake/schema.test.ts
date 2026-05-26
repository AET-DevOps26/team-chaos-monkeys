import { describe, expect, it } from 'vitest'
import { foundItemIntakeSchema } from './schema'

describe('foundItemIntakeSchema', () => {
  const validInput = {
    description: 'Black umbrella',
    foundAt: '2026-05-26T10:00:00Z',
    locationHint: 'Lobby',
    category: 'accessories',
    brand: 'Anonymous',
    color: 'black',
    marks: [{ value: 'scratch on handle' }],
    photo: null,
  }

  it('accepts a fully-populated valid payload', () => {
    expect(() => foundItemIntakeSchema.parse(validInput)).not.toThrow()
  })

  it('accepts a minimal payload with only required fields', () => {
    expect(() =>
      foundItemIntakeSchema.parse({
        foundAt: '2026-05-26T10:00:00Z',
        marks: [],
        photo: null,
      }),
    ).not.toThrow()
  })

  it('rejects an empty foundAt', () => {
    const result = foundItemIntakeSchema.safeParse({ ...validInput, foundAt: '' })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(['foundAt'])
      expect(result.error.issues[0].message).toBe('Required')
    }
  })

  it('rejects a non-File photo value', () => {
    const result = foundItemIntakeSchema.safeParse({
      ...validInput,
      photo: 'not-a-file' as unknown as File,
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === 'photo')).toBe(true)
    }
  })
})
