import { describe, expect, it } from 'vitest'
import { foundItemIntakeSchema } from '@/pages/FoundItemIntake/schema'

describe('foundItemIntakeSchema', () => {
  const validInput = {
    intakeText: 'Black umbrella found near the lobby couch',
    foundAt: '2026-05-26T10:00:00Z',
    photo: null,
  }

  it('accepts a fully-populated valid payload', () => {
    expect(() => foundItemIntakeSchema.parse(validInput)).not.toThrow()
  })

  it('accepts a photo-only intake without notes', () => {
    expect(() =>
      foundItemIntakeSchema.parse({
        foundAt: '2026-05-26T10:00:00Z',
        photo: null,
      }),
    ).not.toThrow()
  })

  it('rejects an empty foundAt', () => {
    const result = foundItemIntakeSchema.safeParse({ ...validInput, foundAt: '' })
    expect(result.success).toBe(false)
    if (!result.success) {
      const foundAtIssue = result.error.issues.find((i) => i.path[0] === 'foundAt')
      expect(foundAtIssue).toBeDefined()
      expect(foundAtIssue?.message).toBe('Required')
    }
  })

  it('rejects intakeText longer than 2000 characters', () => {
    const result = foundItemIntakeSchema.safeParse({ ...validInput, intakeText: 'a'.repeat(2001) })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path[0] === 'intakeText')).toBe(true)
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
