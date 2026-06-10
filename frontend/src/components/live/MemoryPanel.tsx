'use client'

import { useState, useEffect } from 'react'
import { memoryApi, type MemoryRecord } from '@/lib/api'

interface MemoryPanelProps {
  taskId: number
  className?: string
}

/**
 * Side panel showing BrainSentry memories relevant to the current task.
 * Displays past decisions, patterns, bugs, and learnings.
 */
export default function MemoryPanel({ taskId, className = '' }: MemoryPanelProps) {
  const [memories, setMemories] = useState<MemoryRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState(false)

  useEffect(() => {
    const fetchMemories = async () => {
      try {
        const response = await memoryApi.getTaskContext(taskId)
        setMemories(response || [])
      } catch {
        // BrainSentry may not be configured - fail silently
        setMemories([])
      } finally {
        setLoading(false)
      }
    }

    fetchMemories()
  }, [taskId])

  if (loading) {
    return (
      <div className={`p-4 ${className}`}>
        <p className="text-sm text-muted-foreground">Loading memories...</p>
      </div>
    )
  }

  if (memories.length === 0) {
    return (
      <div className={`p-4 ${className}`}>
        <p className="text-sm text-muted-foreground">No relevant memories found for this task.</p>
      </div>
    )
  }

  const categoryColors: Record<string, string> = {
    DECISION: 'bg-info-soft text-info',
    PATTERN: 'bg-ok-soft text-ok',
    BUG: 'bg-danger-soft text-danger',
    ANTIPATTERN: 'bg-warn-soft text-warn',
    INSIGHT: 'bg-info-soft text-info',
    KNOWLEDGE: 'bg-neutral-soft text-neutral',
  }

  const displayMemories = expanded ? memories : memories.slice(0, 5)

  return (
    <div className={`p-4 space-y-3 ${className}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">Agent Memory</h3>
        <span className="text-xs text-muted-foreground">{memories.length} memories</span>
      </div>

      <div className="space-y-2">
        {displayMemories.map((memory) => (
          <div key={memory.id} className="p-2 rounded border border-border text-xs">
            <div className="flex items-center gap-1 mb-1">
              <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${categoryColors[memory.category || ''] || 'bg-neutral-soft text-neutral'}`}>
                {memory.category}
              </span>
              {(memory.tags || []).slice(0, 2).map((tag) => (
                <span key={tag} className="text-muted-foreground">#{tag}</span>
              ))}
            </div>
            <p className="text-foreground line-clamp-2">{memory.summary || memory.content}</p>
          </div>
        ))}
      </div>

      {memories.length > 5 && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="text-xs text-info hover:text-info/80"
        >
          {expanded ? 'Show less' : `Show ${memories.length - 5} more`}
        </button>
      )}
    </div>
  )
}
