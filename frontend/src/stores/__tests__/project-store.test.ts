import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useProjectStore } from '../project-store'
import type { ProjectResponse, CreateProjectRequest, UpdateProjectRequest } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  projectsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockProject: ProjectResponse = {
  id: 1,
  name: 'Test Project',
  slug: 'test-project',
  description: 'A test project',
  default_branch: 'main',
  is_active: true,
  organization_id: 1,
  organization_name: 'Test Org',
  tasks_count: 5,
  created_at: '2025-01-01T00:00:00Z',
}

const mockProject2: ProjectResponse = {
  id: 2,
  name: 'Another Project',
  slug: 'another-project',
  default_branch: 'main',
  is_active: true,
  organization_id: 1,
  organization_name: 'Test Org',
  tasks_count: 3,
  created_at: '2025-01-02T00:00:00Z',
}

describe('useProjectStore', () => {
  beforeEach(() => {
    useProjectStore.setState({
      projects: [],
      selectedProject: null,
      isLoading: false,
      error: null,
    })
  })

  it('has correct initial state', () => {
    const state = useProjectStore.getState()

    expect(state.projects).toEqual([])
    expect(state.selectedProject).toBeNull()
    expect(state.isLoading).toBe(false)
    expect(state.error).toBeNull()
  })

  it('fetchProjects loads projects', async () => {
    const { projectsApi } = await import('@/lib/api')
    vi.mocked(projectsApi.list).mockResolvedValueOnce({
      content: [mockProject, mockProject2],
      page_number: 0,
      page_size: 20,
      total_elements: 2,
      total_pages: 1,
      is_first: true,
      is_last: true,
    })

    await useProjectStore.getState().fetchProjects(1)

    const state = useProjectStore.getState()
    expect(state.projects).toEqual([mockProject, mockProject2])
    expect(state.isLoading).toBe(false)
    expect(state.error).toBeNull()
    expect(projectsApi.list).toHaveBeenCalledWith(1)
  })

  it('fetchProjects sets error on failure', async () => {
    const { projectsApi } = await import('@/lib/api')
    vi.mocked(projectsApi.list).mockRejectedValueOnce(new Error('Failed to load'))

    await useProjectStore.getState().fetchProjects()

    const state = useProjectStore.getState()
    expect(state.projects).toEqual([])
    expect(state.isLoading).toBe(false)
    expect(state.error).toBe('Failed to load')
  })

  it('fetchProject loads a single project into selectedProject', async () => {
    const { projectsApi } = await import('@/lib/api')
    vi.mocked(projectsApi.get).mockResolvedValueOnce(mockProject)

    await useProjectStore.getState().fetchProject(1)

    const state = useProjectStore.getState()
    expect(state.selectedProject).toEqual(mockProject)
    expect(state.isLoading).toBe(false)
    expect(projectsApi.get).toHaveBeenCalledWith(1)
  })

  it('createProject adds a new project to the list', async () => {
    const { projectsApi } = await import('@/lib/api')
    const createData: CreateProjectRequest = {
      name: 'New Project',
      organization_id: 1,
    }
    vi.mocked(projectsApi.create).mockResolvedValueOnce(mockProject)

    const result = await useProjectStore.getState().createProject(createData)

    const state = useProjectStore.getState()
    expect(state.projects).toHaveLength(1)
    expect(state.projects[0]).toEqual(mockProject)
    expect(result).toEqual(mockProject)
    expect(projectsApi.create).toHaveBeenCalledWith(createData)
  })

  it('updateProject replaces the project in the list', async () => {
    const { projectsApi } = await import('@/lib/api')
    useProjectStore.setState({ projects: [mockProject] })

    const updatedProject = { ...mockProject, name: 'Updated Project' }
    const updateData: UpdateProjectRequest = { name: 'Updated Project' }
    vi.mocked(projectsApi.update).mockResolvedValueOnce(updatedProject)

    const result = await useProjectStore.getState().updateProject(1, updateData)

    const state = useProjectStore.getState()
    expect(state.projects[0].name).toBe('Updated Project')
    expect(result).toEqual(updatedProject)
    expect(projectsApi.update).toHaveBeenCalledWith(1, updateData)
  })

  it('updateProject also updates selectedProject if it matches', async () => {
    const { projectsApi } = await import('@/lib/api')
    useProjectStore.setState({ projects: [mockProject], selectedProject: mockProject })

    const updatedProject = { ...mockProject, name: 'Updated Project' }
    vi.mocked(projectsApi.update).mockResolvedValueOnce(updatedProject)

    await useProjectStore.getState().updateProject(1, { name: 'Updated Project' })

    const state = useProjectStore.getState()
    expect(state.selectedProject?.name).toBe('Updated Project')
  })

  it('deleteProject removes the project from the list', async () => {
    const { projectsApi } = await import('@/lib/api')
    useProjectStore.setState({ projects: [mockProject, mockProject2] })
    vi.mocked(projectsApi.delete).mockResolvedValueOnce(undefined as never)

    await useProjectStore.getState().deleteProject(1)

    const state = useProjectStore.getState()
    expect(state.projects).toHaveLength(1)
    expect(state.projects[0].id).toBe(2)
    expect(projectsApi.delete).toHaveBeenCalledWith(1)
  })

  it('selectProject sets the selected project', () => {
    useProjectStore.getState().selectProject(mockProject)

    const state = useProjectStore.getState()
    expect(state.selectedProject).toEqual(mockProject)

    useProjectStore.getState().selectProject(null)
    expect(useProjectStore.getState().selectedProject).toBeNull()
  })

  it('clearError resets the error state', () => {
    useProjectStore.setState({ error: 'Something went wrong' })

    useProjectStore.getState().clearError()

    const state = useProjectStore.getState()
    expect(state.error).toBeNull()
  })
})
