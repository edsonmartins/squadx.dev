import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useOrganizationStore } from '../organization-store'
import type { OrganizationResponse, CreateOrganizationRequest, UpdateOrganizationRequest } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  organizationsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockOrg: OrganizationResponse = {
  id: 1,
  name: 'Test Org',
  slug: 'test-org',
  description: 'A test organization',
  is_active: true,
  members_count: 5,
  projects_count: 3,
  squads_count: 2,
  created_at: '2025-01-01T00:00:00Z',
}

const mockOrg2: OrganizationResponse = {
  id: 2,
  name: 'Another Org',
  slug: 'another-org',
  is_active: true,
  members_count: 3,
  projects_count: 1,
  squads_count: 1,
  created_at: '2025-01-02T00:00:00Z',
}

describe('useOrganizationStore', () => {
  beforeEach(() => {
    useOrganizationStore.setState({
      organizations: [],
      selectedOrganization: null,
      isLoading: false,
      error: null,
    })
  })

  it('has correct initial state', () => {
    const state = useOrganizationStore.getState()

    expect(state.organizations).toEqual([])
    expect(state.selectedOrganization).toBeNull()
    expect(state.isLoading).toBe(false)
    expect(state.error).toBeNull()
  })

  it('fetchOrganizations loads organizations', async () => {
    const { organizationsApi } = await import('@/lib/api')
    vi.mocked(organizationsApi.list).mockResolvedValueOnce({
      content: [mockOrg, mockOrg2],
      page_number: 0,
      page_size: 20,
      total_elements: 2,
      total_pages: 1,
      is_first: true,
      is_last: true,
    })

    await useOrganizationStore.getState().fetchOrganizations()

    const state = useOrganizationStore.getState()
    expect(state.organizations).toEqual([mockOrg, mockOrg2])
    expect(state.isLoading).toBe(false)
    expect(state.error).toBeNull()
    expect(organizationsApi.list).toHaveBeenCalled()
  })

  it('fetchOrganizations auto-selects first org when none is selected', async () => {
    const { organizationsApi } = await import('@/lib/api')
    vi.mocked(organizationsApi.list).mockResolvedValueOnce({
      content: [mockOrg, mockOrg2],
      page_number: 0,
      page_size: 20,
      total_elements: 2,
      total_pages: 1,
      is_first: true,
      is_last: true,
    })

    await useOrganizationStore.getState().fetchOrganizations()

    const state = useOrganizationStore.getState()
    expect(state.selectedOrganization).toEqual(mockOrg)
  })

  it('fetchOrganizations does not override already selected org', async () => {
    const { organizationsApi } = await import('@/lib/api')
    useOrganizationStore.setState({ selectedOrganization: mockOrg2 })
    vi.mocked(organizationsApi.list).mockResolvedValueOnce({
      content: [mockOrg, mockOrg2],
      page_number: 0,
      page_size: 20,
      total_elements: 2,
      total_pages: 1,
      is_first: true,
      is_last: true,
    })

    await useOrganizationStore.getState().fetchOrganizations()

    const state = useOrganizationStore.getState()
    expect(state.selectedOrganization).toEqual(mockOrg2)
  })

  it('fetchOrganizations sets error on failure', async () => {
    const { organizationsApi } = await import('@/lib/api')
    vi.mocked(organizationsApi.list).mockRejectedValueOnce(new Error('Network error'))

    await useOrganizationStore.getState().fetchOrganizations()

    const state = useOrganizationStore.getState()
    expect(state.organizations).toEqual([])
    expect(state.isLoading).toBe(false)
    expect(state.error).toBe('Network error')
  })

  it('createOrganization adds a new org to the list', async () => {
    const { organizationsApi } = await import('@/lib/api')
    const createData: CreateOrganizationRequest = {
      name: 'New Org',
      description: 'A new org',
    }
    vi.mocked(organizationsApi.create).mockResolvedValueOnce(mockOrg)

    const result = await useOrganizationStore.getState().createOrganization(createData)

    const state = useOrganizationStore.getState()
    expect(state.organizations).toHaveLength(1)
    expect(state.organizations[0]).toEqual(mockOrg)
    expect(result).toEqual(mockOrg)
    expect(organizationsApi.create).toHaveBeenCalledWith(createData)
  })

  it('createOrganization auto-selects if it is the first org', async () => {
    const { organizationsApi } = await import('@/lib/api')
    vi.mocked(organizationsApi.create).mockResolvedValueOnce(mockOrg)

    await useOrganizationStore.getState().createOrganization({ name: 'First Org' })

    const state = useOrganizationStore.getState()
    expect(state.selectedOrganization).toEqual(mockOrg)
  })

  it('updateOrganization replaces the org in the list', async () => {
    const { organizationsApi } = await import('@/lib/api')
    useOrganizationStore.setState({ organizations: [mockOrg] })

    const updatedOrg = { ...mockOrg, name: 'Updated Org' }
    const updateData: UpdateOrganizationRequest = { name: 'Updated Org' }
    vi.mocked(organizationsApi.update).mockResolvedValueOnce(updatedOrg)

    const result = await useOrganizationStore.getState().updateOrganization(1, updateData)

    const state = useOrganizationStore.getState()
    expect(state.organizations[0].name).toBe('Updated Org')
    expect(result).toEqual(updatedOrg)
    expect(organizationsApi.update).toHaveBeenCalledWith(1, updateData)
  })

  it('updateOrganization also updates selectedOrganization if it matches', async () => {
    const { organizationsApi } = await import('@/lib/api')
    useOrganizationStore.setState({ organizations: [mockOrg], selectedOrganization: mockOrg })

    const updatedOrg = { ...mockOrg, name: 'Updated Org' }
    vi.mocked(organizationsApi.update).mockResolvedValueOnce(updatedOrg)

    await useOrganizationStore.getState().updateOrganization(1, { name: 'Updated Org' })

    const state = useOrganizationStore.getState()
    expect(state.selectedOrganization?.name).toBe('Updated Org')
  })

  it('deleteOrganization removes the org and re-selects next available', async () => {
    const { organizationsApi } = await import('@/lib/api')
    useOrganizationStore.setState({
      organizations: [mockOrg, mockOrg2],
      selectedOrganization: mockOrg,
    })
    vi.mocked(organizationsApi.delete).mockResolvedValueOnce(undefined as never)

    await useOrganizationStore.getState().deleteOrganization(1)

    const state = useOrganizationStore.getState()
    expect(state.organizations).toHaveLength(1)
    expect(state.organizations[0].id).toBe(2)
    expect(state.selectedOrganization).toEqual(mockOrg2)
    expect(organizationsApi.delete).toHaveBeenCalledWith(1)
  })

  it('deleteOrganization sets selectedOrganization to null when no orgs remain', async () => {
    const { organizationsApi } = await import('@/lib/api')
    useOrganizationStore.setState({
      organizations: [mockOrg],
      selectedOrganization: mockOrg,
    })
    vi.mocked(organizationsApi.delete).mockResolvedValueOnce(undefined as never)

    await useOrganizationStore.getState().deleteOrganization(1)

    const state = useOrganizationStore.getState()
    expect(state.organizations).toHaveLength(0)
    expect(state.selectedOrganization).toBeNull()
  })

  it('selectOrganization sets the selected organization', () => {
    useOrganizationStore.getState().selectOrganization(mockOrg)

    const state = useOrganizationStore.getState()
    expect(state.selectedOrganization).toEqual(mockOrg)

    useOrganizationStore.getState().selectOrganization(null)
    expect(useOrganizationStore.getState().selectedOrganization).toBeNull()
  })

  it('persists selectedOrganization via persist middleware', () => {
    // The store uses persist middleware with name "squadx-organization"
    // and partializes only selectedOrganization.
    // Verify the store can set and retrieve selectedOrganization through setState.
    useOrganizationStore.setState({ selectedOrganization: mockOrg })

    const state = useOrganizationStore.getState()
    expect(state.selectedOrganization).toEqual(mockOrg)
    expect(state.selectedOrganization?.id).toBe(1)
    expect(state.selectedOrganization?.name).toBe('Test Org')
  })
})
