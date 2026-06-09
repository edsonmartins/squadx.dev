"use client";

import { useEffect, useCallback, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  socketClient,
  SocketEvent,
  TaskUpdatedEvent,
  TaskCreatedEvent,
  TaskDeletedEvent,
  ExecutionStartedEvent,
  ExecutionCompletedEvent,
  LiveSessionEvent,
  ChatMessageEvent,
  ParticipantEvent,
  ExecutionLogEvent,
} from "@/lib/socket";
import { useAuthStore } from "@/stores/auth-store";
import { useTaskStore } from "@/stores/task-store";
import { useToast } from "./use-toast";

export function useSocket() {
  const { isAuthenticated } = useAuthStore();
  const connectedRef = useRef(false);

  useEffect(() => {
    if (isAuthenticated && !connectedRef.current) {
      socketClient.connect();
      connectedRef.current = true;
    }

    return () => {
      if (connectedRef.current) {
        socketClient.disconnect();
        connectedRef.current = false;
      }
    };
  }, [isAuthenticated]);

  return socketClient;
}

export function useProjectSocket(projectId: number | null) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { fetchTasks } = useTaskStore();

  useEffect(() => {
    if (!projectId) return;

    const handleProjectEvent = (data: unknown) => {
      const event = data as SocketEvent;

      switch (event.type) {
        case "task_updated":
          fetchTasks(projectId);
          queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
          break;

        case "task_created":
          fetchTasks(projectId);
          queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
          queryClient.invalidateQueries({ queryKey: ["projects"] });
          toast({
            title: "New task",
            description: "A new task has been created",
          });
          break;

        case "task_deleted":
          fetchTasks(projectId);
          queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
          queryClient.invalidateQueries({ queryKey: ["projects"] });
          break;

        case "execution_started":
          fetchTasks(projectId);
          toast({
            title: "Execution started",
            description: "An AI agent has started working on a task",
          });
          break;

        case "execution_completed":
          fetchTasks(projectId);
          toast({
            title: "Execution completed",
            description: "The AI agent has completed the task",
          });
          break;
      }
    };

    const unsubscribe = socketClient.subscribeToProject(projectId, handleProjectEvent);

    return () => {
      unsubscribe();
    };
  }, [projectId, fetchTasks, queryClient, toast]);
}

export function useControlPanelSocket(projectId: number | null | undefined) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!projectId) return;

    const handleEvent = (data: unknown) => {
      const event = data as { type?: string };
      if (event?.type === "spec_task_updated") {
        queryClient.invalidateQueries({ queryKey: ["cp-where-we-are", projectId] });
        queryClient.invalidateQueries({ queryKey: ["cp-tasks"] });
        queryClient.invalidateQueries({ queryKey: ["cp-pass5"] });
      }
    };

    const unsubscribe = socketClient.subscribe(
      `/topic/control-panel/projects/${projectId}/tasks`,
      handleEvent
    );

    return () => unsubscribe();
  }, [projectId, queryClient]);
}

export function useTaskSocket(taskId: number | null) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  useEffect(() => {
    if (!taskId) return;

    const handleTaskEvent = (data: unknown) => {
      const event = data as SocketEvent;

      if (event.type === "task_updated") {
        queryClient.invalidateQueries({ queryKey: ["task", taskId] });
      }
    };

    const unsubscribe = socketClient.subscribeToTask(taskId, handleTaskEvent);

    return () => {
      unsubscribe();
    };
  }, [taskId, queryClient, toast]);
}

export function useLiveSessionSocket(sessionCode: string | null) {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!sessionCode) return;

    // Join the session
    socketClient.joinLiveSession(sessionCode);

    const handleSessionEvent = (data: unknown) => {
      const event = data as LiveSessionEvent;

      switch (event.type) {
        case "session_started":
          queryClient.invalidateQueries({ queryKey: ["live-session", sessionCode] });
          toast({
            title: "Session started",
            description: "The live session is now active",
          });
          break;

        case "session_ended":
          queryClient.invalidateQueries({ queryKey: ["live-session", sessionCode] });
          toast({
            title: "Session ended",
            description: "The live session has ended",
          });
          break;
      }
    };

    const handleParticipantEvent = (data: unknown) => {
      const event = data as ParticipantEvent;
      queryClient.invalidateQueries({ queryKey: ["live-session", sessionCode] });

      if (event.action === "joined") {
        toast({
          title: "New viewer",
          description: `${event.userName} joined the session`,
        });
      }
    };

    const unsubSession = socketClient.subscribeToLiveSession(sessionCode, handleSessionEvent);
    const unsubParticipants = socketClient.subscribeToLiveParticipants(
      sessionCode,
      handleParticipantEvent
    );

    return () => {
      socketClient.leaveLiveSession(sessionCode);
      unsubSession();
      unsubParticipants();
    };
  }, [sessionCode, queryClient, toast]);
}

export function useLiveChatSocket(
  sessionCode: string | null,
  onMessage: (message: ChatMessageEvent) => void
) {
  useEffect(() => {
    if (!sessionCode) return;

    const handleChatMessage = (data: unknown) => {
      const message = data as ChatMessageEvent;
      onMessage(message);
    };

    const unsubscribe = socketClient.subscribeToLiveChat(sessionCode, handleChatMessage);

    return () => {
      unsubscribe();
    };
  }, [sessionCode, onMessage]);

  const sendMessage = useCallback(
    (content: string) => {
      if (sessionCode) {
        socketClient.sendChatMessage(sessionCode, content);
      }
    },
    [sessionCode]
  );

  return { sendMessage };
}

export function useExecutionLogsSocket(
  executionId: number | null,
  onLog: (log: ExecutionLogEvent) => void
) {
  useEffect(() => {
    if (!executionId) return;

    const handleLog = (data: unknown) => {
      const log = data as ExecutionLogEvent;
      onLog(log);
    };

    const unsubscribe = socketClient.subscribeToExecutionLogs(executionId, handleLog);

    return () => {
      unsubscribe();
    };
  }, [executionId, onLog]);
}
