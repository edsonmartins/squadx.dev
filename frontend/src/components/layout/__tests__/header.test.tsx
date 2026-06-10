import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Header } from '../header'

const mockPush = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({ push: mockPush })),
  usePathname: vi.fn(() => '/tasks'),
}))

describe('Header', () => {
  it('renders without crashing', () => {
    const { container } = render(<Header />)
    expect(container.querySelector('header')).toBeTruthy()
  })

  it('shows breadcrumb with current section', () => {
    render(<Header />)

    expect(screen.getByText('Workspace')).toBeTruthy()
    expect(screen.getByText('Tasks')).toBeTruthy()
  })

  it('has a search input and New Task button', () => {
    render(<Header />)

    expect(screen.getByPlaceholderText('Search tasks, projects...')).toBeTruthy()
    expect(screen.getByText('New Task')).toBeTruthy()
  })

  it('navigates to tasks when New Task is clicked', () => {
    render(<Header />)

    screen.getByText('New Task').closest('button')?.click()
    expect(mockPush).toHaveBeenCalledWith('/tasks')
  })
})
