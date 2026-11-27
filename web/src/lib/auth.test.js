import { describe, expect, it } from 'vitest'
import { isValidCode, isValidEmail, isValidPassword, normalizeEmail, profileFromUser } from './auth.js'

describe('auth helpers', () => {
  it('normalizes and validates email', () => {
    expect(normalizeEmail('  User@Example.COM ')).toBe('user@example.com')
    expect(isValidEmail('user@example.com')).toBe(true)
    expect(isValidEmail('user@example')).toBe(false)
    expect(isValidEmail('')).toBe(false)
  })

  it('validates password and code', () => {
    expect(isValidPassword('12345')).toBe(false)
    expect(isValidPassword('123456')).toBe(true)
    expect(isValidCode('12345')).toBe(false)
    expect(isValidCode(' 123456 ')).toBe(true)
  })

  it('falls back to email when profile metadata is empty', () => {
    expect(profileFromUser({ email: 'user@example.com', user_metadata: {} })).toEqual({
      name: 'user',
      code: '',
      email: 'user@example.com'
    })
    expect(profileFromUser({ email: 'user@example.com', user_metadata: { display_name: 'Аня', participant_code: 'a1' } })).toEqual({
      name: 'Аня',
      code: 'a1',
      email: 'user@example.com'
    })
  })
})
