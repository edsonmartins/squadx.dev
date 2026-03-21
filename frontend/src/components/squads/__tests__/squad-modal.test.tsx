import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SquadModal } from '../squad-modal'

vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}))

vi.mock('@/lib/api', () => ({
  squadsApi: {
    create: vi.fn().mockResolvedValue({}),
    update: vi.fn().mockResolvedValue({}),
  },
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
  organizationId: 1,
}

describe('SquadModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders create squad dialog with form fields', () => {
    renderWithProviders(<SquadModal {...defaultProps} />)

    expect(screen.getByRole('heading', { name: 'Create Squad' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name *')).toBeInTheDocument()
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
  })

  it('shows edit title when squad prop is provided', () => {
    const squad = {
      id: 1,
      name: 'Backend Squad',
      description: 'Handles backend tasks',
      is_active: true,
      organization_id: 1,
      organization_name: 'Test Org',
      agents_count: 5,
      active_agents_count: 3,
      created_at: '2025-01-01T00:00:00Z',
    }

    renderWithProviders(<SquadModal {...defaultProps} squad={squad} />)

    expect(screen.getByText('Edit Squad')).toBeInTheDocument()
    expect(screen.getByText('Update your AI squad details.')).toBeInTheDocument()
  })

  it('calls onClose when cancel button is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    renderWithProviders(<SquadModal {...defaultProps} onClose={onClose} />)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('does not render dialog content when open is false', () => {
    renderWithProviders(<SquadModal {...defaultProps} open={false} />)

    expect(screen.queryByText('Create Squad')).not.toBeInTheDocument()
  })
})
