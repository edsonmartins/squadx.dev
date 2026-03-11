import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useAuthStore } from '../auth-store'
import type { UserResponse } from '@/lib/api'

// Mock the api module to avoid real HTTP calls
vi.mock('@/lib/api', () => {
  const setToken = vi.fn()
  return {
    api: { setToken },
    authApi: {
      login: vi.fn(),
      register: vi.fn(),
      refresh: vi.fn(),
      me: vi.fn(),
    },
  }
})

const mockUser: UserResponse = {
  id: 1,
  email: 'test@example.com',
  full_name: 'Test User',
  role: 'USER',
  is_active: true,
  email_verified: true,
  created_at: '2025-01-01T00:00:00Z',
}

describe('useAuthStore', () => {
  beforeEach(() => {
    // Reset the store to initial state before each test
    useAuthStore.setState({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: true,
    })
  })

  it('has correct initial state', () => {
    const state = useAuthStore.getState()

    expect(state.user).toBeNull()
    expect(state.accessToken).toBeNull()
    expect(state.refreshToken).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.isLoading).toBe(true)
  })

  it('setUser updates the user in state', () => {
    useAuthStore.getState().setUser(mockUser)

    const state = useAuthStore.getState()
    expect(state.user).toEqual(mockUser)
    expect(state.user?.email).toBe('test@example.com')
    expect(state.user?.full_name).toBe('Test User')
  })

  it('logout clears authentication state', async () => {
    // First, set up an authenticated state
    useAuthStore.setState({
      user: mockUser,
      accessToken: 'some-token',
      refreshToken: 'some-refresh-token',
      isAuthenticated: true,
      isLoading: false,
    })

    // Verify authenticated state
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(useAuthStore.getState().user).toEqual(mockUser)

    // Logout
    useAuthStore.getState().logout()

    // Verify cleared state
    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.accessToken).toBeNull()
    expect(state.refreshToken).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })

  it('login calls authApi and sets authenticated state', async () => {
    const { authApi } = await import('@/lib/api')
    const mockResponse = {
      access_token: 'access-123',
      refresh_token: 'refresh-456',
      token_type: 'Bearer',
      expires_in: 3600,
      user: mockUser,
    }
    vi.mocked(authApi.login).mockResolvedValueOnce(mockResponse)

    await useAuthStore.getState().login('test@example.com', 'password123')

    const state = useAuthStore.getState()
    expect(state.user).toEqual(mockUser)
    expect(state.accessToken).toBe('access-123')
    expect(state.refreshToken).toBe('refresh-456')
    expect(state.isAuthenticated).toBe(true)
    expect(authApi.login).toHaveBeenCalledWith('test@example.com', 'password123')
  })

  describe('register', () => {
    it('calls authApi.register and sets authenticated state on success', async () => {
      const { authApi } = await import('@/lib/api')
      const mockResponse = {
        access_token: 'reg-access-123',
        refresh_token: 'reg-refresh-456',
        token_type: 'Bearer',
        expires_in: 3600,
        user: mockUser,
      }
      vi.mocked(authApi.register).mockResolvedValueOnce(mockResponse)

      await useAuthStore.getState().register('test@example.com', 'password123', 'Test User')

      const state = useAuthStore.getState()
      expect(state.user).toEqual(mockUser)
      expect(state.accessToken).toBe('reg-access-123')
      expect(state.refreshToken).toBe('reg-refresh-456')
      expect(state.isAuthenticated).toBe(true)
      expect(authApi.register).toHaveBeenCalledWith('test@example.com', 'password123', 'Test User')
    })

    it('re-throws error and does not set authenticated state', async () => {
      const { authApi } = await import('@/lib/api')
      vi.mocked(authApi.register).mockRejectedValueOnce(new Error('Email already exists'))

      await expect(
        useAuthStore.getState().register('test@example.com', 'password123', 'Test User')
      ).rejects.toThrow('Email already exists')

      const state = useAuthStore.getState()
      expect(state.user).toBeNull()
      expect(state.isAuthenticated).toBe(false)
      expect(state.accessToken).toBeNull()
    })
  })

  describe('refreshTokens', () => {
    it('calls authApi.refresh and updates tokens on success', async () => {
      const { authApi } = await import('@/lib/api')
      useAuthStore.setState({
        refreshToken: 'old-refresh-token',
        accessToken: 'old-access-token',
        isAuthenticated: true,
      })

      const mockResponse = {
        access_token: 'new-access-token',
        refresh_token: 'new-refresh-token',
        token_type: 'Bearer',
        expires_in: 3600,
        user: mockUser,
      }
      vi.mocked(authApi.refresh).mockResolvedValueOnce(mockResponse)

      await useAuthStore.getState().refreshTokens()

      const state = useAuthStore.getState()
      expect(state.accessToken).toBe('new-access-token')
      expect(state.refreshToken).toBe('new-refresh-token')
      expect(state.user).toEqual(mockUser)
      expect(state.isAuthenticated).toBe(true)
      expect(authApi.refresh).toHaveBeenCalledWith('old-refresh-token')
    })

    it('throws when no refresh token is available', async () => {
      useAuthStore.setState({ refreshToken: null })

      await expect(
        useAuthStore.getState().refreshTokens()
      ).rejects.toThrow('No refresh token available')
    })
  })

  describe('initialize', () => {
    it('with valid token calls getCurrentUser and sets user', async () => {
      const { authApi, api } = await import('@/lib/api')
      useAuthStore.setState({
        accessToken: 'valid-token',
        refreshToken: 'some-refresh',
        isLoading: true,
      })

      vi.mocked(authApi.me).mockResolvedValueOnce(mockUser)

      await useAuthStore.getState().initialize()

      const state = useAuthStore.getState()
      expect(state.user).toEqual(mockUser)
      expect(state.isAuthenticated).toBe(true)
      expect(state.isLoading).toBe(false)
      expect(api.setToken).toHaveBeenCalledWith('valid-token')
      expect(authApi.me).toHaveBeenCalled()
    })
  })
})
