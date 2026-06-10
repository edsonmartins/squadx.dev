import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Sidebar } from '../sidebar'

const mockLogout = vi.fn()

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(() => '/'),
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: vi.fn(() => ({
    user: {
      id: 1,
      email: 'jane@squadx.dev',
      full_name: 'Jane Doe',
    },
    logout: mockLogout,
  })),
}))

vi.mock('@/stores/ui-store', () => ({
  useUIStore: vi.fn(() => ({
    sidebarCollapsed: false,
    theme: 'system',
    toggleSidebar: vi.fn(),
    setTheme: vi.fn(),
  })),
}))

vi.mock('@/lib/api', () => ({
  approvalsApi: {
    getPending: vi.fn(() => Promise.resolve({ content: [], total_elements: 0 })),
  },
  liveViewApi: {
    supabase: {
      getActive: vi.fn(() => Promise.resolve([])),
    },
  },
}))

function renderSidebar() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <Sidebar />
    </QueryClientProvider>
  )
}

describe('Sidebar', () => {
  it('renders without crashing', () => {
    const { container } = renderSidebar()
    expect(container.firstChild).toBeTruthy()
  })

  it('renders all expected navigation items', () => {
    renderSidebar()

    const expectedItems = ['Dashboard', 'Projects', 'Tasks', 'Squads', 'Live View', 'Analytics']
    for (const item of expectedItems) {
      expect(screen.getByText(item)).toBeTruthy()
    }
  })

  it('renders navigation groups', () => {
    renderSidebar()

    expect(screen.getByText('Workspace')).toBeTruthy()
    expect(screen.getByText('Operação')).toBeTruthy()
    expect(screen.getByText('Insights')).toBeTruthy()
  })

  it('renders Settings in bottom navigation', () => {
    renderSidebar()

    expect(screen.getByText('Settings')).toBeTruthy()
  })

  it('shows brand name when sidebar is expanded', () => {
    renderSidebar()

    expect(screen.getByText('SquadX')).toBeTruthy()
  })

  it('shows user name and email in footer', () => {
    renderSidebar()

    expect(screen.getByText('Jane Doe')).toBeTruthy()
    expect(screen.getByText('jane@squadx.dev')).toBeTruthy()
  })
})
