"use client";

/**
 * SquadX Kanban Board Component
 * Gerencia visualização e manipulação de tasks com drag-and-drop
 */
import React, { useState, useMemo } from 'react';
import {
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragOverlay,
  DragStartEvent,
  PointerSensor,
  useSensor,
  useSensors,
  closestCorners,
} from '@dnd-kit/core';
import {
  SortableContext,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { Plus } from 'lucide-react';

import { KanbanColumn } from './KanbanColumn';
import { TaskCard } from './TaskCard';
import { Button } from '@/components/ui/button';
import { useTaskStore } from '@/stores/taskStore';
import { Task, TaskStatus } from '@/types/models';

interface KanbanBoardProps {
  projectId: string;
}

const COLUMN_CONFIGS: Record<TaskStatus, { title: string; color: string }> = {
  todo: { title: 'To Do', color: 'bg-slate-100' },
  in_progress: { title: 'In Progress', color: 'bg-blue-100' },
  in_review: { title: 'In Review', color: 'bg-purple-100' },
  blocked: { title: 'Blocked', color: 'bg-red-100' },
  done: { title: 'Done', color: 'bg-green-100' },
  cancelled: { title: 'Cancelled', color: 'bg-gray-100' },
};

export function KanbanBoard({ projectId }: KanbanBoardProps) {
  const [activeTask, setActiveTask] = useState<Task | null>(null);
  const [isCreatingTask, setIsCreatingTask] = useState<TaskStatus | null>(null);

  const { tasks, moveTask, updateTaskPosition } = useTaskStore();

  // Filtrar tasks do projeto atual
  const projectTasks = useMemo(
    () => tasks.filter((task) => task.project_id === projectId),
    [tasks, projectId]
  );

  // Agrupar tasks por status
  const tasksByStatus = useMemo(() => {
    const grouped: Record<TaskStatus, Task[]> = {
      todo: [],
      in_progress: [],
      in_review: [],
      blocked: [],
      done: [],
      cancelled: [],
    };

    projectTasks.forEach((task) => {
      grouped[task.status].push(task);
    });

    // Ordenar por position
    Object.keys(grouped).forEach((status) => {
      grouped[status as TaskStatus].sort((a, b) => a.position - b.position);
    });

    return grouped;
  }, [projectTasks]);

  // Configurar sensores para drag
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8, // 8px de movimento para iniciar drag
      },
    })
  );

  // Handler: Início do drag
  const handleDragStart = (event: DragStartEvent) => {
    const { active } = event;
    const task = projectTasks.find((t) => t.id === active.id);
    if (task) {
      setActiveTask(task);
    }
  };

  // Handler: Drag sobre outra área
  const handleDragOver = (event: DragOverEvent) => {
    const { active, over } = event;
    if (!over) return;

    const activeId = active.id as string;
    const overId = over.id as string;

    // Se está sobre uma coluna
    if (overId.startsWith('column-')) {
      const newStatus = overId.replace('column-', '') as TaskStatus;
      const task = projectTasks.find((t) => t.id === activeId);
      
      if (task && task.status !== newStatus) {
        // Atualização otimista
        moveTask(activeId, newStatus);
      }
    }
  };

  // Handler: Fim do drag
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    setActiveTask(null);

    if (!over) return;

    const activeId = active.id as string;
    const overId = over.id as string;

    // Se dropou sobre outra task (reordenação)
    if (!overId.startsWith('column-')) {
      const activeTask = projectTasks.find((t) => t.id === activeId);
      const overTask = projectTasks.find((t) => t.id === overId);

      if (activeTask && overTask && activeTask.status === overTask.status) {
        // Reordenar na mesma coluna
        updateTaskPosition(activeId, overTask.position);
      }
    }
  };

  // Handler: Criar nova task
  const handleCreateTask = (status: TaskStatus) => {
    setIsCreatingTask(status);
    // TODO: Abrir modal de criação de task
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
    >
      <div className="flex gap-4 overflow-x-auto pb-4 h-full">
        {Object.entries(COLUMN_CONFIGS).map(([status, config]) => {
          const columnTasks = tasksByStatus[status as TaskStatus];
          
          return (
            <KanbanColumn
              key={status}
              id={`column-${status}`}
              title={config.title}
              count={columnTasks.length}
              color={config.color}
            >
              <SortableContext
                items={columnTasks.map((t) => t.id)}
                strategy={verticalListSortingStrategy}
              >
                <div className="space-y-2 flex-1">
                  {columnTasks.map((task) => (
                    <TaskCard key={task.id} task={task} />
                  ))}
                </div>
              </SortableContext>

              {/* Botão adicionar task */}
              <Button
                variant="ghost"
                size="sm"
                className="w-full mt-2 justify-start"
                onClick={() => handleCreateTask(status as TaskStatus)}
              >
                <Plus className="h-4 w-4 mr-2" />
                Add Task
              </Button>
            </KanbanColumn>
          );
        })}
      </div>

      {/* Overlay durante drag */}
      <DragOverlay>
        {activeTask ? (
          <div className="opacity-90 rotate-3 scale-105">
            <TaskCard task={activeTask} isDragging />
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
