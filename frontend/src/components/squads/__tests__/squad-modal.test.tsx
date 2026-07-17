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

describe('SquadModal — sandbox egress policy (RFC-0006)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('defaults a new squad to the deny-by-default policy', async () => {
    const { squadsApi } = await import('@/lib/api')
    renderWithProviders(<SquadModal {...defaultProps} />)

    await userEvent.type(screen.getByLabelText('Name *'), 'Backend')
    await userEvent.click(screen.getByRole('button', { name: 'Create Squad' }))

    await vi.waitFor(() =>
      expect(squadsApi.create).toHaveBeenCalledWith(
        expect.objectContaining({ sandbox_egress_policy: 'AGENT_DEFAULT' })
      )
    )
  })

  it('shows the policy an existing squad is running under', () => {
    renderWithProviders(
      <SquadModal
        {...defaultProps}
        squad={{
          id: 1,
          name: 'Backend',
          is_active: true,
          organization_id: 1,
          organization_name: 'Org',
          agents_count: 0,
          active_agents_count: 0,
          created_at: '',
          sandbox_egress_policy: 'DENY_ALL',
        } as never}
      />
    )

    expect(screen.getByLabelText('Network access')).toHaveTextContent('No network')
  })

  it('falls back to the default when the backend predates the field', () => {
    // An installed client can talk to an older backend; the control must show the
    // policy actually in force rather than render empty.
    renderWithProviders(
      <SquadModal
        {...defaultProps}
        squad={{
          id: 1,
          name: 'Backend',
          is_active: true,
          organization_id: 1,
          organization_name: 'Org',
          agents_count: 0,
          active_agents_count: 0,
          created_at: '',
        } as never}
      />
    )

    expect(screen.getByLabelText('Network access')).toHaveTextContent('Default (recommended)')
  })
})
