import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VideoGrid } from '../video-grid'
import type { VoiceParticipant } from '@/hooks/use-voice-video'

const defaultProps = {
  localStream: null,
  remoteStreams: new Map<string, MediaStream>(),
  participants: [] as VoiceParticipant[],
  isAudioEnabled: true,
  isVideoEnabled: false,
  userName: 'Test User',
  collapsed: false,
  onToggleCollapse: vi.fn(),
}

describe('VideoGrid', () => {
  it('returns null when no video streams are active and not collapsed', () => {
    const { container } = render(<VideoGrid {...defaultProps} />)

    // Component returns null when hasAnyVideo is false and not collapsed
    expect(container.innerHTML).toBe('')
  })

  it('renders the grid header when local video is enabled', () => {
    render(
      <VideoGrid
        {...defaultProps}
        isVideoEnabled={true}
      />
    )

    expect(screen.getByText(/Video/)).toBeInTheDocument()
    expect(screen.getByText(/1 participant/)).toBeInTheDocument()
  })

  it('renders participant tiles for remote video participants', () => {
    const participants: VoiceParticipant[] = [
      {
        peerId: 'peer-1',
        userId: 'user-1',
        userName: 'Alice',
        isAudioEnabled: true,
        isVideoEnabled: true,
        isSpeaking: false,
      },
      {
        peerId: 'peer-2',
        userId: 'user-2',
        userName: 'Bob',
        isAudioEnabled: false,
        isVideoEnabled: true,
        isSpeaking: true,
      },
    ]

    const remoteStreams = new Map<string, MediaStream>()

    render(
      <VideoGrid
        {...defaultProps}
        participants={participants}
        remoteStreams={remoteStreams}
      />
    )

    // Both participants have isVideoEnabled = true, so they show up
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.getByText(/2 participants/)).toBeInTheDocument()
  })

  it('calls onToggleCollapse when collapse button is clicked', async () => {
    const user = userEvent.setup()
    const onToggleCollapse = vi.fn()

    const participants: VoiceParticipant[] = [
      {
        peerId: 'peer-1',
        userId: 'user-1',
        userName: 'Alice',
        isAudioEnabled: true,
        isVideoEnabled: true,
        isSpeaking: false,
      },
    ]

    render(
      <VideoGrid
        {...defaultProps}
        participants={participants}
        onToggleCollapse={onToggleCollapse}
      />
    )

    // The collapse/expand button is in the header
    const buttons = screen.getAllByRole('button')
    // Find the collapse button (it's in the header area)
    await user.click(buttons[0])

    expect(onToggleCollapse).toHaveBeenCalledTimes(1)
  })
})
