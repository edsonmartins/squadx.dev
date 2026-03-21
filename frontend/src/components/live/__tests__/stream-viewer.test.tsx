import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StreamViewer } from '../stream-viewer'

const mockConnect = vi.fn()
const mockDisconnect = vi.fn()

vi.mock('@/hooks/use-webrtc', () => ({
  useWebRTC: vi.fn(() => ({
    connectionState: 'connecting',
    remoteStream: null,
    connect: mockConnect,
    disconnect: mockDisconnect,
    stats: null,
    reconnectAttempts: 0,
    dataChannel: null,
  })),
  parseWebRTCStats: vi.fn(() => ({
    resolution: null,
    framerate: 0,
    bitrate: 0,
  })),
}))

vi.mock('@/hooks/use-remote-control', () => ({
  useRemoteControl: vi.fn(() => ({
    requestControl: vi.fn(),
    releaseControl: vi.fn(),
  })),
}))

// Import the mocked module so we can change return values per test
import { useWebRTC } from '@/hooks/use-webrtc'
const mockUseWebRTC = vi.mocked(useWebRTC)

describe('StreamViewer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseWebRTC.mockReturnValue({
      connectionState: 'connecting',
      remoteStream: null,
      connect: mockConnect,
      disconnect: mockDisconnect,
      stats: null,
      reconnectAttempts: 0,
      dataChannel: null,
    } as ReturnType<typeof useWebRTC>)
  })

  it('renders a video element', () => {
    const { container } = render(<StreamViewer sessionId="sess-123" />)
    const video = container.querySelector('video')
    expect(video).toBeTruthy()
  })

  it('shows connecting state with loading message', () => {
    render(<StreamViewer sessionId="sess-123" />)

    expect(screen.getByText('Connecting to stream...')).toBeInTheDocument()
    expect(screen.getByText('Establishing WebRTC connection')).toBeInTheDocument()
  })

  it('shows failed state with retry button', () => {
    mockUseWebRTC.mockReturnValue({
      connectionState: 'failed',
      remoteStream: null,
      connect: mockConnect,
      disconnect: mockDisconnect,
      stats: null,
      reconnectAttempts: 0,
      dataChannel: null,
    } as ReturnType<typeof useWebRTC>)

    render(<StreamViewer sessionId="sess-123" />)

    expect(screen.getByText('Connection failed')).toBeInTheDocument()
    expect(screen.getByText('Try Again')).toBeInTheDocument()
  })

  it('shows LIVE badge when connected', () => {
    mockUseWebRTC.mockReturnValue({
      connectionState: 'connected',
      remoteStream: null,
      connect: mockConnect,
      disconnect: mockDisconnect,
      stats: null,
      reconnectAttempts: 0,
      dataChannel: null,
    } as ReturnType<typeof useWebRTC>)

    render(<StreamViewer sessionId="sess-123" />)

    expect(screen.getByText('LIVE')).toBeInTheDocument()
  })

  it('calls connect on mount', () => {
    render(<StreamViewer sessionId="sess-123" />)

    expect(mockConnect).toHaveBeenCalled()
  })
})
