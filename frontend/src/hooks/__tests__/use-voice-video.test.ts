import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useVoiceVideo } from '../use-voice-video'

// Mock supabase module
vi.mock('@/lib/supabase', () => ({
  supabase: {
    channel: vi.fn(() => ({
      on: vi.fn().mockReturnThis(),
      subscribe: vi.fn().mockReturnValue({ send: vi.fn() }),
      unsubscribe: vi.fn(),
      untrack: vi.fn(),
      track: vi.fn(),
      send: vi.fn(),
      presenceState: vi.fn().mockReturnValue({}),
    })),
    removeChannel: vi.fn(),
  },
}))

// Mock browser APIs
const mockAudioTrack = {
  kind: 'audio' as const,
  enabled: true,
  stop: vi.fn(),
  id: 'audio-track-1',
}

const mockVideoTrack = {
  kind: 'video' as const,
  enabled: true,
  stop: vi.fn(),
  id: 'video-track-1',
}

const mockMediaStream = {
  getTracks: vi.fn(() => [mockAudioTrack]),
  getAudioTracks: vi.fn(() => [mockAudioTrack]),
  getVideoTracks: vi.fn(() => []),
  addTrack: vi.fn(),
  removeTrack: vi.fn(),
}

// Mock RTCPeerConnection
class MockRTCPeerConnection {
  onicecandidate: ((event: unknown) => void) | null = null
  ontrack: ((event: unknown) => void) | null = null
  onconnectionstatechange: (() => void) | null = null
  connectionState = 'new'
  signalingState = 'stable'

  addTrack = vi.fn()
  removeTrack = vi.fn()
  getSenders = vi.fn(() => [])
  createOffer = vi.fn().mockResolvedValue({ type: 'offer', sdp: 'mock-sdp' })
  createAnswer = vi.fn().mockResolvedValue({ type: 'answer', sdp: 'mock-sdp' })
  setLocalDescription = vi.fn().mockResolvedValue(undefined)
  setRemoteDescription = vi.fn().mockResolvedValue(undefined)
  addIceCandidate = vi.fn().mockResolvedValue(undefined)
  close = vi.fn()
}

// Stub globals
vi.stubGlobal('RTCPeerConnection', MockRTCPeerConnection)
vi.stubGlobal('RTCSessionDescription', vi.fn((desc: unknown) => desc))
vi.stubGlobal('RTCIceCandidate', vi.fn((c: unknown) => c))
vi.stubGlobal('AudioContext', vi.fn(() => ({
  createAnalyser: vi.fn(() => ({
    fftSize: 0,
    frequencyBinCount: 128,
    getByteFrequencyData: vi.fn(),
  })),
  createMediaStreamSource: vi.fn(() => ({
    connect: vi.fn(),
  })),
  close: vi.fn(),
})))

const mockGetUserMedia = vi.fn().mockResolvedValue(mockMediaStream)
const mockEnumerateDevices = vi.fn().mockResolvedValue([])

vi.stubGlobal('navigator', {
  mediaDevices: {
    getUserMedia: mockGetUserMedia,
    enumerateDevices: mockEnumerateDevices,
  },
})

vi.stubGlobal('crypto', {
  randomUUID: vi.fn(() => 'test-peer-id-1234'),
})

const defaultOptions = {
  sessionCode: 'test-session',
  userId: 'user-1',
  userName: 'Test User',
  enabled: false,
}

describe('useVoiceVideo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAudioTrack.enabled = true
    mockVideoTrack.enabled = true
  })

  it('has correct initial state when disabled', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    expect(result.current.isAudioEnabled).toBe(true)
    expect(result.current.isVideoEnabled).toBe(false)
    expect(result.current.localStream).toBeNull()
    expect(result.current.remoteStreams).toBeInstanceOf(Map)
    expect(result.current.remoteStreams.size).toBe(0)
    expect(result.current.participants).toEqual([])
    expect(result.current.isPushToTalk).toBe(false)
    expect(result.current.audioLevel).toBe(0)
    expect(result.current.availableDevices).toEqual([])
    expect(result.current.selectedAudioDevice).toBe('')
    expect(result.current.selectedVideoDevice).toBe('')
  })

  it('does not acquire media when enabled is false', () => {
    renderHook(() => useVoiceVideo(defaultOptions))

    expect(mockGetUserMedia).not.toHaveBeenCalled()
  })

  it('exposes toggleAudio function that can be called', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    expect(typeof result.current.toggleAudio).toBe('function')
  })

  it('exposes toggleVideo function that can be called', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    expect(typeof result.current.toggleVideo).toBe('function')
  })

  it('setPushToTalk toggles push-to-talk state', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    expect(result.current.isPushToTalk).toBe(false)

    act(() => {
      result.current.setPushToTalk(true)
    })

    expect(result.current.isPushToTalk).toBe(true)
  })

  it('setSelectedAudioDevice updates the selected audio device', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    expect(result.current.selectedAudioDevice).toBe('')

    act(() => {
      result.current.setSelectedAudioDevice('device-abc')
    })

    expect(result.current.selectedAudioDevice).toBe('device-abc')
  })

  it('setSelectedVideoDevice updates the selected video device', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    expect(result.current.selectedVideoDevice).toBe('')

    act(() => {
      result.current.setSelectedVideoDevice('camera-xyz')
    })

    expect(result.current.selectedVideoDevice).toBe('camera-xyz')
  })

  it('returns all expected properties in the return object', () => {
    const { result } = renderHook(() => useVoiceVideo(defaultOptions))

    const expectedKeys: (keyof typeof result.current)[] = [
      'isAudioEnabled',
      'isVideoEnabled',
      'toggleAudio',
      'toggleVideo',
      'localStream',
      'remoteStreams',
      'participants',
      'isPushToTalk',
      'setPushToTalk',
      'startPushToTalk',
      'stopPushToTalk',
      'audioLevel',
      'availableDevices',
      'selectedAudioDevice',
      'selectedVideoDevice',
      'setSelectedAudioDevice',
      'setSelectedVideoDevice',
    ]

    for (const key of expectedKeys) {
      expect(result.current).toHaveProperty(key)
    }
  })
})
