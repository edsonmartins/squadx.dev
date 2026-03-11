import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Header } from '../header'

const mockUser = {
  id: 1,
  email: 'jane@squadx.dev',
  full_name: 'Jane Doe',
  role: 'USER' as const,
  is_active: true,
  email_verified: true,
  created_at: '2025-01-01T00:00:00Z',
}

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: vi.fn(() => ({
    user: mockUser,
  })),
}))

describe('Header', () => {
  it('renders without crashing', () => {
    const { container } = render(<Header />)
    expect(container.querySelector('header')).toBeTruthy()
  })

  it('shows user full name and email from auth store', () => {
    render(<Header />)

    expect(screen.getByText('Jane Doe')).toBeTruthy()
    expect(screen.getByText('jane@squadx.dev')).toBeTruthy()
  })

  it('shows the first letter of user name as avatar', () => {
    render(<Header />)

    expect(screen.getByText('J')).toBeTruthy()
  })

  it('has a search input and New Task button', () => {
    render(<Header />)

    expect(screen.getByPlaceholderText('Search tasks, projects...')).toBeTruthy()
    expect(screen.getByText('New Task')).toBeTruthy()
  })
})
