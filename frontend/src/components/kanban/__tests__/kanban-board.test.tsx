import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { TaskResponse } from '@/lib/api'

// Mock next/navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

// Mock live view API
vi.mock('@/lib/api', () => ({
  liveViewApi: {
    supabase: { getActive: vi.fn().mockResolvedValue([]) },
  },
}))

const mockTasks: TaskResponse[] = [
  {
    id: 1,
    title: 'Task in TODO',
    status: 'TODO',
    priority: 'MEDIUM',
    order_index: 0,
    project_id: 1,
    project_name: 'P1',
    subtasks_count: 0,
    created_at: '2025-01-01T00:00:00Z',
  },
  {
    id: 2,
    title: 'Task in progress',
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    order_index: 0,
    project_id: 1,
    project_name: 'P1',
    subtasks_count: 0,
    created_at: '2025-01-01T00:00:00Z',
  },
  {
    id: 3,
    title: 'Completed task',
    status: 'DONE',
    priority: 'LOW',
    order_index: 0,
    project_id: 1,
    project_name: 'P1',
    subtasks_count: 0,
    created_at: '2025-01-01T00:00:00Z',
  },
]

// Mock task store
vi.mock('@/stores/task-store', () => ({
  useTaskStore: vi.fn(() => ({
    tasks: mockTasks,
    updateTaskStatus: vi.fn(),
    setSelectedTask: vi.fn(),
  })),
}))

// Mock react-kanban-kit to avoid complex DnD rendering
vi.mock('react-kanban-kit', () => ({
  Kanban: ({
    dataSource,
    renderColumnHeader,
  }: {
    dataSource: Record<string, { id: string; title: string; children: string[]; type?: string }>
    renderColumnHeader: (col: { id: string; title: string; children: string[] }) => React.ReactNode
  }) => {
    const root = dataSource['root']
    return (
      <div data-testid="kanban-board">
        {root.children.map((colId: string) => {
          const col = dataSource[colId]
          return (
            <div key={colId} data-testid={`column-${colId}`}>
              {renderColumnHeader(col)}
              <div data-testid={`column-cards-${colId}`}>
                {col.children.map((cardId: string) => {
                  const card = dataSource[cardId]
                  return <div key={cardId} data-testid={cardId}>{card?.title}</div>
                })}
              </div>
            </div>
          )
        })}
      </div>
    )
  },
}))

import { KanbanBoard } from '../kanban-board'

describe('KanbanBoard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders all six status columns', () => {
    render(<KanbanBoard projectId={1} />)

    expect(screen.getByText('To Do')).toBeInTheDocument()
    expect(screen.getByText('In Progress')).toBeInTheDocument()
    expect(screen.getByText('In Review')).toBeInTheDocument()
    expect(screen.getByText('Blocked')).toBeInTheDocument()
    expect(screen.getByText('Done')).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
  })

  it('renders task titles in the board', () => {
    render(<KanbanBoard projectId={1} />)

    expect(screen.getByText('Task in TODO')).toBeInTheDocument()
    expect(screen.getByText('Task in progress')).toBeInTheDocument()
    expect(screen.getByText('Completed task')).toBeInTheDocument()
  })

  it('places tasks into their correct status columns', () => {
    render(<KanbanBoard projectId={1} />)

    const todoColumn = screen.getByTestId('column-cards-TODO')
    expect(todoColumn).toHaveTextContent('Task in TODO')

    const inProgressColumn = screen.getByTestId('column-cards-IN_PROGRESS')
    expect(inProgressColumn).toHaveTextContent('Task in progress')

    const doneColumn = screen.getByTestId('column-cards-DONE')
    expect(doneColumn).toHaveTextContent('Completed task')
  })

  it('renders empty columns with zero task count', () => {
    render(<KanbanBoard projectId={1} />)

    // IN_REVIEW, BLOCKED, CANCELLED columns have 0 tasks
    const reviewCards = screen.getByTestId('column-cards-IN_REVIEW')
    expect(reviewCards.children).toHaveLength(0)

    const blockedCards = screen.getByTestId('column-cards-BLOCKED')
    expect(blockedCards.children).toHaveLength(0)
  })
})
