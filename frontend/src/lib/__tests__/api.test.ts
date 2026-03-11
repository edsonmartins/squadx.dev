import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ApiError } from '../api'

const API_URL = 'http://localhost:8080'

// We need to test ApiClient directly, so we re-import after setting up fetch mock
const mockFetch = vi.fn()
global.fetch = mockFetch

// Dynamic import to get a fresh ApiClient instance via the module
// Since ApiClient is not exported, we test through the exported `api` singleton
// and the ApiError class.
// To test ApiClient in isolation we replicate its construction via the module.
let api: typeof import('../api')['api']

beforeEach(async () => {
  mockFetch.mockReset()
  // Re-import to get the singleton; reset token state
  api = (await import('../api')).api
  api.setToken(null)
})

function mockResponse(body: unknown, status = 200, ok = true) {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  }
}

describe('ApiClient', () => {
  describe('get()', () => {
    it('should make GET request with correct URL', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: { id: 1 }, timestamp: '' })
      )

      await api.get('/api/v1/test')

      expect(mockFetch).toHaveBeenCalledWith(
        `${API_URL}/api/v1/test`,
        expect.objectContaining({ method: 'GET' })
      )
    })

    it('should include Authorization header when token is set', async () => {
      api.setToken('my-token')
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: {}, timestamp: '' })
      )

      await api.get('/api/v1/secure')

      const call = mockFetch.mock.calls[0]
      expect(call[1].headers).toEqual(
        expect.objectContaining({ Authorization: 'Bearer my-token' })
      )
    })

    it('should not include Authorization header when no token', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: {}, timestamp: '' })
      )

      await api.get('/api/v1/public')

      const call = mockFetch.mock.calls[0]
      expect(call[1].headers).not.toHaveProperty('Authorization')
    })
  })

  describe('post()', () => {
    it('should send JSON body', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: { id: 1 }, timestamp: '' })
      )

      await api.post('/api/v1/items', { name: 'test' })

      const call = mockFetch.mock.calls[0]
      expect(call[1].method).toBe('POST')
      expect(call[1].body).toBe(JSON.stringify({ name: 'test' }))
    })

    it('should handle void responses (204 No Content)', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: undefined, timestamp: '' })
      )

      const result = await api.post('/api/v1/items/1/action')

      expect(result).toBeUndefined()
    })
  })

  describe('put()', () => {
    it('should send JSON body with PUT method', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: { id: 1, name: 'updated' }, timestamp: '' })
      )

      await api.put('/api/v1/items/1', { name: 'updated' })

      const call = mockFetch.mock.calls[0]
      expect(call[1].method).toBe('PUT')
      expect(call[1].body).toBe(JSON.stringify({ name: 'updated' }))
    })
  })

  describe('patch()', () => {
    it('should send with PATCH method', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: { id: 1 }, timestamp: '' })
      )

      await api.patch('/api/v1/items/1', { status: 'active' })

      const call = mockFetch.mock.calls[0]
      expect(call[1].method).toBe('PATCH')
      expect(call[1].body).toBe(JSON.stringify({ status: 'active' }))
    })
  })

  describe('delete()', () => {
    it('should use DELETE method', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse({ success: true, data: undefined, timestamp: '' })
      )

      await api.delete('/api/v1/items/1')

      const call = mockFetch.mock.calls[0]
      expect(call[1].method).toBe('DELETE')
    })
  })

  describe('error handling', () => {
    it('should throw ApiError on non-ok response with JSON error body', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse(
          { success: false, message: 'Not found', errors: { id: 'Invalid' }, timestamp: '' },
          404,
          false
        )
      )

      try {
        await api.get('/api/v1/missing')
        expect.fail('Expected ApiError to be thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(404)
        expect(apiError.message).toBe('Not found')
        expect(apiError.errors).toEqual({ id: 'Invalid' })
      }
    })

    it('should throw ApiError on non-ok response with no message', async () => {
      mockFetch.mockResolvedValueOnce(
        mockResponse(
          { success: false, timestamp: '' },
          500,
          false
        )
      )

      try {
        await api.get('/api/v1/error')
        expect.fail('Expected ApiError to be thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(500)
        expect(apiError.message).toBe('An error occurred')
      }
    })

    it('should handle network errors', async () => {
      mockFetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))

      await expect(api.get('/api/v1/down')).rejects.toThrow('Failed to fetch')
    })
  })

  describe('setToken / getToken', () => {
    it('should manage token state', () => {
      expect(api.getToken()).toBeNull()

      api.setToken('token-abc')
      expect(api.getToken()).toBe('token-abc')

      api.setToken(null)
      expect(api.getToken()).toBeNull()
    })
  })
})
