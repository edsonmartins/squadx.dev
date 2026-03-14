import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TaskCardContent } from '../task-card'
import type { TaskResponse } from '@/lib/api'

const makeTask = (overrides: Partial<TaskResponse> = {}): TaskResponse => ({
  id: 1,
  title: 'Implement login flow',
  description: 'Add OAuth2 login with Google provider',
  status: 'TODO',
  priority: 'HIGH',
  story_points: 5,
  order_index: 0,
  project_id: 10,
  project_name: 'Frontend',
  subtasks_count: 0,
  created_at: '2025-06-01T00:00:00Z',
  tags: ['auth', 'frontend'],
  assigned_agent_name: 'CodeBot',
  ...overrides,
})

describe('TaskCardContent', () => {
  it('renders the task title', () => {
    render(<TaskCardContent task={makeTask()} />)
    expect(screen.getByText('Implement login flow')).toBeInTheDocument()
  })

  it('renders the priority badge', () => {
    render(<TaskCardContent task={makeTask({ priority: 'URGENT' })} />)
    expect(screen.getByText('Urgent')).toBeInTheDocument()
  })

  it('renders the assigned agent name', () => {
    render(<TaskCardContent task={makeTask({ assigned_agent_name: 'DesignBot' })} />)
    expect(screen.getByText('DesignBot')).toBeInTheDocument()
  })

  it('calls onClick when card is clicked', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()

    render(<TaskCardContent task={makeTask()} onClick={handleClick} />)
    await user.click(screen.getByText('Implement login flow'))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('renders tags (up to 2 visible, with overflow indicator)', () => {
    render(
      <TaskCardContent
        task={makeTask({ tags: ['auth', 'frontend', 'urgent'] })}
      />
    )
    expect(screen.getByText('auth')).toBeInTheDocument()
    expect(screen.getByText('frontend')).toBeInTheDocument()
    expect(screen.getByText('+1')).toBeInTheDocument()
  })

  it('shows LIVE badge when liveSessionCode is provided', () => {
    render(
      <TaskCardContent
        task={makeTask()}
        liveSessionCode="abc123"
        onWatchLive={vi.fn()}
      />
    )
    expect(screen.getByText('LIVE')).toBeInTheDocument()
  })

  it('calls onWatchLive with session code when LIVE badge is clicked', async () => {
    const user = userEvent.setup()
    const onWatchLive = vi.fn()

    render(
      <TaskCardContent
        task={makeTask()}
        liveSessionCode="abc123"
        onWatchLive={onWatchLive}
      />
    )
    await user.click(screen.getByText('LIVE'))

    expect(onWatchLive).toHaveBeenCalledWith('abc123')
  })
})
