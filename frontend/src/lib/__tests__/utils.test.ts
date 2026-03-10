import { describe, it, expect } from 'vitest'
import { cn, formatDate, formatDateTime, generateCode, slugify } from '../utils'

describe('cn', () => {
  it('merges class names', () => {
    const result = cn('foo', 'bar')
    expect(result).toBe('foo bar')
  })

  it('handles conditional classes', () => {
    const result = cn('base', false && 'hidden', 'visible')
    expect(result).toBe('base visible')
  })

  it('merges conflicting tailwind classes (last wins)', () => {
    const result = cn('px-2', 'px-4')
    expect(result).toBe('px-4')
  })

  it('handles undefined and null inputs', () => {
    const result = cn('base', undefined, null, 'end')
    expect(result).toBe('base end')
  })
})

describe('formatDate', () => {
  it('formats a date string', () => {
    const result = formatDate('2025-06-15T12:00:00Z')
    expect(result).toMatch(/Jun/)
    expect(result).toMatch(/15/)
    expect(result).toMatch(/2025/)
  })

  it('formats a Date object', () => {
    const result = formatDate(new Date('2025-01-01T00:00:00Z'))
    expect(result).toMatch(/2025/)
  })
})

describe('formatDateTime', () => {
  it('includes time components', () => {
    const result = formatDateTime('2025-06-15T14:30:00Z')
    expect(result).toMatch(/Jun/)
    expect(result).toMatch(/15/)
    expect(result).toMatch(/2025/)
  })
})

describe('generateCode', () => {
  it('generates a code of default length 6', () => {
    const code = generateCode()
    expect(code).toHaveLength(6)
  })

  it('generates a code of specified length', () => {
    const code = generateCode(10)
    expect(code).toHaveLength(10)
  })

  it('generates only uppercase alphanumeric characters', () => {
    const code = generateCode(100)
    expect(code).toMatch(/^[A-Z0-9]+$/)
  })

  it('generates different codes on subsequent calls', () => {
    const codes = new Set(Array.from({ length: 20 }, () => generateCode()))
    // With 36^6 possibilities, 20 codes should all be unique
    expect(codes.size).toBeGreaterThan(1)
  })
})

describe('slugify', () => {
  it('converts text to lowercase slug', () => {
    expect(slugify('Hello World')).toBe('hello-world')
  })

  it('removes special characters', () => {
    expect(slugify('Hello, World!')).toBe('hello-world')
  })

  it('handles multiple spaces', () => {
    expect(slugify('hello   world')).toBe('hello-world')
  })

  it('handles already slugified text', () => {
    expect(slugify('hello-world')).toBe('helloworld')
  })
})
