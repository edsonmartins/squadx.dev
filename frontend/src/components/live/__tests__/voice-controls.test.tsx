import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VoiceControls } from '../voice-controls'

const defaultProps = {
  isAudioEnabled: true,
  isVideoEnabled: false,
  toggleAudio: vi.fn(),
  toggleVideo: vi.fn(),
  isPushToTalk: false,
  setPushToTalk: vi.fn(),
  audioLevel: 0,
  participants: [],
  availableDevices: [],
  selectedAudioDevice: '',
  selectedVideoDevice: '',
  onSelectAudioDevice: vi.fn(),
  onSelectVideoDevice: vi.fn(),
}

describe('VoiceControls', () => {
  it('renders microphone and camera toggle buttons', () => {
    render(<VoiceControls {...defaultProps} />)

    const buttons = screen.getAllByRole('button')
    // At minimum: mic button, camera button, settings button
    expect(buttons.length).toBeGreaterThanOrEqual(3)
  })

  it('calls toggleAudio when microphone button is clicked', async () => {
    const user = userEvent.setup()
    const toggleAudio = vi.fn()

    render(<VoiceControls {...defaultProps} toggleAudio={toggleAudio} />)

    // The mic button is the first non-settings button
    const buttons = screen.getAllByRole('button')
    // Mic button is the first real button (index 0 based on render order)
    await user.click(buttons[0])

    expect(toggleAudio).toHaveBeenCalledTimes(1)
  })

  it('calls toggleVideo when camera button is clicked', async () => {
    const user = userEvent.setup()
    const toggleVideo = vi.fn()

    render(<VoiceControls {...defaultProps} toggleVideo={toggleVideo} />)

    const buttons = screen.getAllByRole('button')
    // Camera button is the second button
    await user.click(buttons[1])

    expect(toggleVideo).toHaveBeenCalledTimes(1)
  })

  it('shows PTT indicator when isPushToTalk is true', () => {
    render(<VoiceControls {...defaultProps} isPushToTalk={true} />)

    expect(screen.getByText('PTT')).toBeInTheDocument()
  })

  it('does not show PTT indicator when isPushToTalk is false', () => {
    render(<VoiceControls {...defaultProps} isPushToTalk={false} />)

    expect(screen.queryByText('PTT')).not.toBeInTheDocument()
  })
})
