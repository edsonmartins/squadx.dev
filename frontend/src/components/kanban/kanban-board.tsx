"use client";

import { useCallback, useMemo } from "react";
import { Kanban, type BoardData, type BoardItem, type BoardProps } from "react-kanban-kit";

// Extract ConfigMap type from BoardProps
type ConfigMap = BoardProps["configMap"];
import { useTaskStore } from "@/stores/task-store";
import { TaskCardContent } from "./task-card";
import type { TaskStatus, TaskResponse } from "@/lib/api";

const COLUMNS: { id: TaskStatus; title: string; color: string }[] = [
  { id: "TODO", title: "To Do", color: "bg-slate-500" },
  { id: "IN_PROGRESS", title: "In Progress", color: "bg-blue-500" },
  { id: "IN_REVIEW", title: "In Review", color: "bg-purple-500" },
  { id: "BLOCKED", title: "Blocked", color: "bg-red-500" },
  { id: "DONE", title: "Done", color: "bg-green-500" },
  { id: "CANCELLED", title: "Cancelled", color: "bg-gray-500" },
];

interface KanbanBoardProps {
  projectId: number;
}

// Extended BoardItem with task data
interface TaskBoardItem extends BoardItem {
  task?: TaskResponse;
  color?: string;
}

export function KanbanBoard({ projectId }: KanbanBoardProps) {
  const { tasks, updateTaskStatus, setSelectedTask } = useTaskStore();

  // Transform tasks into BoardData structure
  const dataSource = useMemo<BoardData>(() => {
    const data: Record<string, TaskBoardItem> = {};

    // Create root item with column IDs as children
    data["root"] = {
      id: "root",
      title: "Root",
      parentId: null,
      children: COLUMNS.map((col) => col.id),
      totalChildrenCount: COLUMNS.length,
    };

    // Create columns and cards
    COLUMNS.forEach((col) => {
      const columnTasks = tasks
        .filter((t) => t.status === col.id)
        .sort((a, b) => a.order_index - b.order_index);

      // Create column item
      data[col.id] = {
        id: col.id,
        title: col.title,
        parentId: "root",
        children: columnTasks.map((t) => `task-${t.id}`),
        totalChildrenCount: columnTasks.length,
        color: col.color,
        type: "column",
      };

      // Create card items for tasks
      columnTasks.forEach((task) => {
        data[`task-${task.id}`] = {
          id: `task-${task.id}`,
          title: task.title,
          parentId: col.id,
          children: [],
          totalChildrenCount: 0,
          task,
          type: "task",
        };
      });
    });

    return data as BoardData;
  }, [tasks]);

  // Handle card move (drag-and-drop)
  const handleCardMove = useCallback(
    async ({
      cardId,
      toColumnId,
    }: {
      cardId: string;
      fromColumnId: string;
      toColumnId: string;
      taskAbove: string | null;
      taskBelow: string | null;
      position: number;
    }) => {
      // Extract task ID from cardId (format: "task-{id}")
      const taskId = parseInt(cardId.replace("task-", ""), 10);
      const newStatus = toColumnId as TaskStatus;

      try {
        await updateTaskStatus(taskId, newStatus);
      } catch (error) {
        console.error("Failed to update task status:", error);
      }
    },
    [updateTaskStatus]
  );

  // Handle card click
  const handleCardClick = useCallback(
    (_e: React.MouseEvent<HTMLDivElement>, card: BoardItem) => {
      const taskCard = card as TaskBoardItem;
      if (taskCard.task) {
        setSelectedTask(taskCard.task);
      }
    },
    [setSelectedTask]
  );

  // Get column color
  const getColumnColor = useCallback((columnId: string): string => {
    const column = COLUMNS.find((c) => c.id === columnId);
    return column?.color || "bg-slate-500";
  }, []);

  // Config map for rendering cards
  const configMap = useMemo<ConfigMap>(
    () => ({
      task: {
        render: ({ data }) => {
          const cardData = data as TaskBoardItem;
          if (!cardData.task) return null;
          return <TaskCardContent task={cardData.task} />;
        },
        isDraggable: true,
      },
    }),
    []
  );

  // Render column header
  const renderColumnHeader = useCallback(
    (column: BoardItem) => {
      const color = getColumnColor(column.id);
      return (
        <div className="flex items-center gap-2 p-3 border-b">
          <div className={`h-3 w-3 rounded-full ${color}`} />
          <h3 className="font-medium">{column.title}</h3>
          <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-xs font-medium">
            {column.children.length}
          </span>
        </div>
      );
    },
    [getColumnColor]
  );

  // Column wrapper styles
  const columnWrapperClassName = useCallback(() => {
    return "flex w-80 shrink-0 flex-col rounded-lg bg-muted/50";
  }, []);

  const columnListContentClassName = useCallback(() => {
    return "flex-1 space-y-3 p-3 min-h-[200px]";
  }, []);

  return (
    <div className="flex gap-4 overflow-x-auto pb-4">
      <Kanban
        dataSource={dataSource}
        configMap={configMap}
        onCardMove={handleCardMove}
        onCardClick={handleCardClick}
        renderColumnHeader={renderColumnHeader}
        columnWrapperClassName={columnWrapperClassName}
        columnListContentClassName={columnListContentClassName}
        cardsGap={12}
      />
    </div>
  );
}
