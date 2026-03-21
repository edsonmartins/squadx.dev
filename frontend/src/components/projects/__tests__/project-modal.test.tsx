import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ProjectModal } from '../project-modal'

vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}))

vi.mock('@/lib/api', () => ({
  projectsApi: {
    create: vi.fn().mockResolvedValue({}),
    update: vi.fn().mockResolvedValue({}),
  },
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
  onOpenChange: vi.fn(),
  organizationId: 1,
}

describe('ProjectModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders create project dialog with all form fields', () => {
    renderWithProviders(<ProjectModal {...defaultProps} />)

    expect(screen.getByRole('heading', { name: 'Create Project' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name *')).toBeInTheDocument()
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
    expect(screen.getByLabelText('Repository URL')).toBeInTheDocument()
    expect(screen.getByLabelText('Default Branch')).toBeInTheDocument()
  })

  it('shows edit title when project prop is provided', () => {
    const project = {
      id: 1,
      name: 'My Project',
      slug: 'my-project',
      description: 'A project',
      repository_url: 'https://github.com/org/repo',
      default_branch: 'main',
      is_active: true,
      squad_id: null,
      organization_id: 1,
      organization_name: 'Test Org',
      tasks_count: 0,
      created_at: '2025-01-01T00:00:00Z',
    }

    renderWithProviders(<ProjectModal {...defaultProps} project={project} />)

    expect(screen.getByText('Edit Project')).toBeInTheDocument()
    expect(screen.getByText('Update your project details below.')).toBeInTheDocument()
  })

  it('calls onOpenChange when cancel is clicked', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    renderWithProviders(
      <ProjectModal {...defaultProps} onOpenChange={onOpenChange} />
    )
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    // Cancel calls onClose which calls onOpenChange(false)
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('does not render dialog content when open is false', () => {
    renderWithProviders(<ProjectModal {...defaultProps} open={false} />)

    expect(screen.queryByText('Create Project')).not.toBeInTheDocument()
  })
})
