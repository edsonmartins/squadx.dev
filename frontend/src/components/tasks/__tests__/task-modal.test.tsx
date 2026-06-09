import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TaskModal } from '../task-modal'

// Mock stores and hooks
vi.mock('@/stores/task-store', () => ({
  useTaskStore: vi.fn(() => ({
    createTask: vi.fn().mockResolvedValue({}),
    updateTask: vi.fn().mockResolvedValue({}),
  })),
}))

vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}))

vi.mock('@/lib/api', () => ({
  tasksApi: {},
  agentsApi: { listByOrganization: vi.fn().mockResolvedValue({ content: [] }) },
  squadsApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
}))

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

const defaultProps = {
  open: true,
  onClose: vi.fn(),
  projectId: 1,
  organizationId: 1,
}

describe('TaskModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the create task dialog when open', () => {
    renderWithProviders(<TaskModal {...defaultProps} />)

    expect(screen.getByRole('heading', { name: 'Create Task' })).toBeInTheDocument()
    expect(
      screen.getByText('Create a new task for your project.')
    ).toBeInTheDocument()
  })

  it('renders all form fields', () => {
    renderWithProviders(<TaskModal {...defaultProps} />)

    expect(screen.getByLabelText('Title *')).toBeInTheDocument()
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
    expect(screen.getByLabelText('Story Points')).toBeInTheDocument()
    expect(screen.getByLabelText('Est. Hours')).toBeInTheDocument()
    expect(screen.getByLabelText('Due Date')).toBeInTheDocument()
    expect(screen.getByLabelText('Tags')).toBeInTheDocument()
  })

  it('shows edit title when task prop is provided', () => {
    const task = {
      id: 1,
      title: 'Existing task',
      description: 'desc',
      status: 'TODO' as const,
      priority: 'MEDIUM' as const,
      order_index: 0,
      project_id: 1,
      project_name: 'P1',
      subtasks_count: 0,
      created_at: '2025-01-01T00:00:00Z',
    }

    renderWithProviders(<TaskModal {...defaultProps} task={task} />)

    expect(screen.getByText('Edit Task')).toBeInTheDocument()
    expect(screen.getByText('Update your task details.')).toBeInTheDocument()
  })

  it('calls onClose when cancel button is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    renderWithProviders(<TaskModal {...defaultProps} onClose={onClose} />)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
