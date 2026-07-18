import { describe, it, expect, beforeEach, vi } from 'vitest'

const mockFetch = vi.fn()
global.fetch = mockFetch

let preferencesApi: typeof import('../api')['preferencesApi']
let DEFAULT_USER_PREFERENCES: typeof import('../api')['DEFAULT_USER_PREFERENCES']

beforeEach(async () => {
  mockFetch.mockReset()
  const mod = await import('../api')
  preferencesApi = mod.preferencesApi
  DEFAULT_USER_PREFERENCES = mod.DEFAULT_USER_PREFERENCES
  mod.api.setToken(null)
})

function mockResponse(data: unknown, status = 200, ok = true) {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue({ success: ok, data, timestamp: '' }),
  }
}

describe('preferencesApi.get', () => {
  it('parses a well-formed payload', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        email_notifications: false,
        push_notifications: true,
        execution_alerts: false,
        live_session_alerts: true,
        auto_start_live: false,
        default_quality: 'SD',
        max_viewers: 10,
        updated_at: '2026-07-17T00:00:00Z',
      })
    )

    const prefs = await preferencesApi.get()

    expect(prefs.email_notifications).toBe(false)
    expect(prefs.default_quality).toBe('SD')
    expect(prefs.max_viewers).toBe(10)
  })

  it('coerces bad enum / wrong-typed fields to safe defaults (parse, don\'t cast)', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        email_notifications: true,
        push_notifications: true,
        execution_alerts: true,
        live_session_alerts: true,
        auto_start_live: true,
        default_quality: 'ULTRA', // not a valid enum value
        max_viewers: 'many', // wrong type
      })
    )

    const prefs = await preferencesApi.get()

    expect(prefs.default_quality).toBe('HD') // fell back per-field
    expect(prefs.max_viewers).toBe(5)
    expect(prefs.email_notifications).toBe(true) // valid field preserved
  })

  it('falls back to defaults when the payload is not an object', async () => {
    mockFetch.mockResolvedValueOnce(mockResponse(null))

    const prefs = await preferencesApi.get()

    expect(prefs).toEqual(DEFAULT_USER_PREFERENCES)
  })
})

describe('preferencesApi.update', () => {
  it('sends a full-replace PUT with the given body', async () => {
    const body = {
      email_notifications: false,
      push_notifications: false,
      execution_alerts: true,
      live_session_alerts: false,
      auto_start_live: false,
      default_quality: 'AUTO' as const,
      max_viewers: 3,
    }
    mockFetch.mockResolvedValueOnce(mockResponse({ ...body, updated_at: '2026-07-17T00:00:00Z' }))

    await preferencesApi.update(body)

    const call = mockFetch.mock.calls[0]
    expect(call[0]).toContain('/api/v1/me/preferences')
    expect(call[1].method).toBe('PUT')
    expect(JSON.parse(call[1].body)).toEqual(body)
  })
})
