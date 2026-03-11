import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useTaskStore } from '../task-store'
import type { TaskResponse, CreateTaskRequest, UpdateTaskRequest } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  tasksApi: {
    kanban: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    updateStatus: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockTask: TaskResponse = {
  id: 1,
  title: 'Test Task',
  description: 'A test task',
  status: 'TODO',
  priority: 'MEDIUM',
  order_index: 0,
  project_id: 1,
  project_name: 'Test Project',
  subtasks_count: 0,
  created_at: '2025-01-01T00:00:00Z',
}

const mockTask2: TaskResponse = {
  id: 2,
  title: 'In Progress Task',
  description: 'A task in progress',
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  order_index: 0,
  project_id: 1,
  project_name: 'Test Project',
  subtasks_count: 0,
  created_at: '2025-01-02T00:00:00Z',
}

describe('useTaskStore', () => {
  beforeEach(() => {
    useTaskStore.setState({
      tasks: [],
      selectedTask: null,
      isLoading: false,
      error: null,
    })
  })

  it('has correct initial state', () => {
    const state = useTaskStore.getState()

    expect(state.tasks).toEqual([])
    expect(state.selectedTask).toBeNull()
    expect(state.isLoading).toBe(false)
    expect(state.error).toBeNull()
  })

  it('fetchTasks loads tasks for a project', async () => {
    const { tasksApi } = await import('@/lib/api')
    vi.mocked(tasksApi.kanban).mockResolvedValueOnce([mockTask, mockTask2])

    await useTaskStore.getState().fetchTasks(1)

    const state = useTaskStore.getState()
    expect(state.tasks).toEqual([mockTask, mockTask2])
    expect(state.isLoading).toBe(false)
    expect(state.error).toBeNull()
    expect(tasksApi.kanban).toHaveBeenCalledWith(1)
  })

  it('fetchTasks sets error on failure', async () => {
    const { tasksApi } = await import('@/lib/api')
    vi.mocked(tasksApi.kanban).mockRejectedValueOnce(new Error('Network error'))

    await useTaskStore.getState().fetchTasks(1)

    const state = useTaskStore.getState()
    expect(state.tasks).toEqual([])
    expect(state.isLoading).toBe(false)
    expect(state.error).toBe('Network error')
  })

  it('createTask adds a new task to the list', async () => {
    const { tasksApi } = await import('@/lib/api')
    const createData: CreateTaskRequest = {
      title: 'New Task',
      project_id: 1,
      priority: 'HIGH',
    }
    vi.mocked(tasksApi.create).mockResolvedValueOnce(mockTask)

    const result = await useTaskStore.getState().createTask(createData)

    const state = useTaskStore.getState()
    expect(state.tasks).toHaveLength(1)
    expect(state.tasks[0]).toEqual(mockTask)
    expect(result).toEqual(mockTask)
    expect(tasksApi.create).toHaveBeenCalledWith(createData)
  })

  it('updateTask replaces the task in the list', async () => {
    const { tasksApi } = await import('@/lib/api')
    useTaskStore.setState({ tasks: [mockTask] })

    const updatedTask = { ...mockTask, title: 'Updated Task' }
    const updateData: UpdateTaskRequest = { title: 'Updated Task' }
    vi.mocked(tasksApi.update).mockResolvedValueOnce(updatedTask)

    const result = await useTaskStore.getState().updateTask(1, updateData)

    const state = useTaskStore.getState()
    expect(state.tasks[0].title).toBe('Updated Task')
    expect(result).toEqual(updatedTask)
    expect(tasksApi.update).toHaveBeenCalledWith(1, updateData)
  })

  it('updateTask also updates selectedTask if it matches', async () => {
    const { tasksApi } = await import('@/lib/api')
    useTaskStore.setState({ tasks: [mockTask], selectedTask: mockTask })

    const updatedTask = { ...mockTask, title: 'Updated Task' }
    vi.mocked(tasksApi.update).mockResolvedValueOnce(updatedTask)

    await useTaskStore.getState().updateTask(1, { title: 'Updated Task' })

    const state = useTaskStore.getState()
    expect(state.selectedTask?.title).toBe('Updated Task')
  })

  it('updateTaskStatus changes the status of a task', async () => {
    const { tasksApi } = await import('@/lib/api')
    useTaskStore.setState({ tasks: [mockTask] })
    vi.mocked(tasksApi.updateStatus).mockResolvedValueOnce(undefined as never)

    await useTaskStore.getState().updateTaskStatus(1, 'IN_PROGRESS')

    const state = useTaskStore.getState()
    expect(state.tasks[0].status).toBe('IN_PROGRESS')
    expect(tasksApi.updateStatus).toHaveBeenCalledWith(1, 'IN_PROGRESS')
  })

  it('moveTask updates task status and order_index', () => {
    useTaskStore.setState({ tasks: [mockTask, mockTask2] })

    useTaskStore.getState().moveTask(1, 'IN_PROGRESS', 1)

    const state = useTaskStore.getState()
    const movedTask = state.tasks.find((t) => t.id === 1)
    expect(movedTask?.status).toBe('IN_PROGRESS')
    expect(movedTask?.order_index).toBe(1)
  })

  it('deleteTask removes the task from the list', async () => {
    const { tasksApi } = await import('@/lib/api')
    useTaskStore.setState({ tasks: [mockTask, mockTask2] })
    vi.mocked(tasksApi.delete).mockResolvedValueOnce(undefined as never)

    await useTaskStore.getState().deleteTask(1)

    const state = useTaskStore.getState()
    expect(state.tasks).toHaveLength(1)
    expect(state.tasks[0].id).toBe(2)
    expect(tasksApi.delete).toHaveBeenCalledWith(1)
  })

  it('deleteTask clears selectedTask if it matches', async () => {
    const { tasksApi } = await import('@/lib/api')
    useTaskStore.setState({ tasks: [mockTask], selectedTask: mockTask })
    vi.mocked(tasksApi.delete).mockResolvedValueOnce(undefined as never)

    await useTaskStore.getState().deleteTask(1)

    const state = useTaskStore.getState()
    expect(state.selectedTask).toBeNull()
  })

  it('getTasksByStatus filters and sorts tasks by status', () => {
    const todoTask1: TaskResponse = { ...mockTask, id: 10, status: 'TODO', order_index: 2 }
    const todoTask2: TaskResponse = { ...mockTask, id: 11, status: 'TODO', title: 'Another TODO', order_index: 0 }
    const inProgressTask: TaskResponse = { ...mockTask, id: 12, status: 'IN_PROGRESS', order_index: 0 }
    useTaskStore.setState({ tasks: [todoTask1, inProgressTask, todoTask2] })

    const todoTasks = useTaskStore.getState().getTasksByStatus('TODO')

    expect(todoTasks).toHaveLength(2)
    expect(todoTasks[0].order_index).toBe(0)
    expect(todoTasks[1].order_index).toBe(2)
  })
})
