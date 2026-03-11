import { describe, it, expect, beforeEach } from 'vitest'
import { useUIStore } from '../ui-store'

describe('useUIStore', () => {
  beforeEach(() => {
    useUIStore.setState({
      sidebarCollapsed: true,
    })
  })

  it('has correct initial state', () => {
    const state = useUIStore.getState()

    expect(state.sidebarCollapsed).toBe(true)
    expect(typeof state.toggleSidebar).toBe('function')
    expect(typeof state.setSidebarCollapsed).toBe('function')
  })

  it('toggleSidebar flips sidebarCollapsed from true to false', () => {
    expect(useUIStore.getState().sidebarCollapsed).toBe(true)

    useUIStore.getState().toggleSidebar()

    expect(useUIStore.getState().sidebarCollapsed).toBe(false)
  })

  it('toggleSidebar flips sidebarCollapsed from false to true', () => {
    useUIStore.setState({ sidebarCollapsed: false })

    useUIStore.getState().toggleSidebar()

    expect(useUIStore.getState().sidebarCollapsed).toBe(true)
  })

  it('toggleSidebar twice returns to original state', () => {
    const original = useUIStore.getState().sidebarCollapsed

    useUIStore.getState().toggleSidebar()
    useUIStore.getState().toggleSidebar()

    expect(useUIStore.getState().sidebarCollapsed).toBe(original)
  })

  it('setSidebarCollapsed sets the value explicitly', () => {
    useUIStore.getState().setSidebarCollapsed(false)
    expect(useUIStore.getState().sidebarCollapsed).toBe(false)

    useUIStore.getState().setSidebarCollapsed(true)
    expect(useUIStore.getState().sidebarCollapsed).toBe(true)
  })
})
