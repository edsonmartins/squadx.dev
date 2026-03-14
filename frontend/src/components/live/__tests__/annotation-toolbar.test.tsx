import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnotationToolbar } from '../annotation-toolbar'

const defaultProps = {
  activeTool: 'pointer' as const,
  activeColor: '#ef4444',
  onToolChange: vi.fn(),
  onColorChange: vi.fn(),
  onClear: vi.fn(),
}

describe('AnnotationToolbar', () => {
  it('renders all five tool buttons, color swatches, and clear button', () => {
    render(<AnnotationToolbar {...defaultProps} />)

    // 5 tool buttons + 5 color swatches + 1 clear button = 11 total buttons
    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBe(11)
  })

  it('calls onToolChange when a tool button is clicked', async () => {
    const user = userEvent.setup()
    const onToolChange = vi.fn()

    render(<AnnotationToolbar {...defaultProps} onToolChange={onToolChange} />)

    // Click the second tool button (Rectangle)
    const buttons = screen.getAllByRole('button')
    await user.click(buttons[1])

    expect(onToolChange).toHaveBeenCalledWith('rectangle')
  })

  it('renders five color swatches', () => {
    const { container } = render(<AnnotationToolbar {...defaultProps} />)

    // Color swatches are <button> elements with rounded-full class
    // but they are not role="button" by default since they are native <button>
    // They are distinguishable by their inline backgroundColor style
    const swatches = container.querySelectorAll('button.rounded-full')
    expect(swatches).toHaveLength(5)
  })

  it('calls onColorChange when a color swatch is clicked', async () => {
    const user = userEvent.setup()
    const onColorChange = vi.fn()

    const { container } = render(
      <AnnotationToolbar {...defaultProps} onColorChange={onColorChange} />
    )

    const swatches = container.querySelectorAll('button.rounded-full')
    // Click the blue swatch (index 1 = #3b82f6)
    await user.click(swatches[1])

    expect(onColorChange).toHaveBeenCalledWith('#3b82f6')
  })

  it('calls onClear when clear button is clicked', async () => {
    const user = userEvent.setup()
    const onClear = vi.fn()

    render(<AnnotationToolbar {...defaultProps} onClear={onClear} />)

    // Clear button is the last icon button
    const buttons = screen.getAllByRole('button')
    await user.click(buttons[buttons.length - 1])

    expect(onClear).toHaveBeenCalledTimes(1)
  })
})
