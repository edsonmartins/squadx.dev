import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Sidebar } from '../sidebar'

const mockLogout = vi.fn()

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(() => '/'),
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: vi.fn(() => ({
    logout: mockLogout,
  })),
}))

vi.mock('@/stores/ui-store', () => ({
  useUIStore: vi.fn(() => ({
    sidebarCollapsed: false,
    toggleSidebar: vi.fn(),
  })),
}))

describe('Sidebar', () => {
  it('renders without crashing', () => {
    const { container } = render(<Sidebar />)
    expect(container.firstChild).toBeTruthy()
  })

  it('renders all expected navigation items', () => {
    render(<Sidebar />)

    const expectedItems = ['Dashboard', 'Projects', 'Tasks', 'Squads', 'Live View', 'Analytics']
    for (const item of expectedItems) {
      expect(screen.getByText(item)).toBeTruthy()
    }
  })

  it('renders Settings in bottom navigation', () => {
    render(<Sidebar />)

    expect(screen.getByText('Settings')).toBeTruthy()
  })

  it('shows org name SquadX.dev when sidebar is expanded', () => {
    render(<Sidebar />)

    expect(screen.getByText('SquadX.dev')).toBeTruthy()
  })
})
