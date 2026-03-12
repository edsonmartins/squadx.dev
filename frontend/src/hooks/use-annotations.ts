"use client";

import { useCallback, useEffect, useState, useRef } from "react";
import { supabase } from "@/lib/supabase";
import { RealtimeChannel } from "@supabase/supabase-js";

export type AnnotationTool = "pointer" | "rectangle" | "arrow" | "freehand" | "text";

export interface Annotation {
  id: string;
  tool: AnnotationTool;
  points: { x: number; y: number }[];
  color: string;
  userId: string;
  userName: string;
  timestamp: number;
}

/** Duration in ms before annotations start fading */
const ANNOTATION_TTL = 30_000;
/** How often to prune expired annotations */
const CLEANUP_INTERVAL = 1_000;

const PRESET_COLORS = ["#ef4444", "#3b82f6", "#22c55e", "#eab308", "#ffffff"];

/**
 * Assigns a consistent color to a user based on their ID.
 */
function colorForUser(userId: string): string {
  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = (hash << 5) - hash + userId.charCodeAt(i);
    hash |= 0;
  }
  return PRESET_COLORS[Math.abs(hash) % PRESET_COLORS.length];
}

interface UseAnnotationsOptions {
  sessionCode: string;
  userId: string;
  userName: string;
}

interface UseAnnotationsReturn {
  annotations: Annotation[];
  activeTool: AnnotationTool;
  activeColor: string;
  setActiveTool: (tool: AnnotationTool) => void;
  setActiveColor: (color: string) => void;
  startAnnotation: (point: { x: number; y: number }) => void;
  updateAnnotation: (point: { x: number; y: number }) => void;
  endAnnotation: () => void;
  clearAnnotations: () => void;
}

export function useAnnotations({
  sessionCode,
  userId,
  userName,
}: UseAnnotationsOptions): UseAnnotationsReturn {
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [activeTool, setActiveTool] = useState<AnnotationTool>("pointer");
  const [activeColor, setActiveColor] = useState<string>(() => colorForUser(userId));
  const currentAnnotationRef = useRef<Annotation | null>(null);
  const channelRef = useRef<RealtimeChannel | null>(null);

  // Subscribe to annotation broadcast channel
  useEffect(() => {
    if (!sessionCode) return;

    const channel = supabase.channel(`annotations:${sessionCode}`, {
      config: { broadcast: { self: false } },
    });

    channel.on("broadcast", { event: "annotation" }, ({ payload }) => {
      const annotation = payload as Annotation;
      setAnnotations((prev) => {
        if (prev.some((a) => a.id === annotation.id)) {
          // Update existing (in-progress) annotation
          return prev.map((a) => (a.id === annotation.id ? annotation : a));
        }
        return [...prev, annotation];
      });
    });

    channel.on("broadcast", { event: "annotation-clear" }, () => {
      setAnnotations([]);
    });

    channel.subscribe();
    channelRef.current = channel;

    return () => {
      supabase.removeChannel(channel);
      channelRef.current = null;
    };
  }, [sessionCode]);

  // Auto-expire annotations after TTL
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      setAnnotations((prev) => prev.filter((a) => now - a.timestamp < ANNOTATION_TTL));
    }, CLEANUP_INTERVAL);

    return () => clearInterval(interval);
  }, []);

  const broadcastAnnotation = useCallback((annotation: Annotation) => {
    channelRef.current?.send({
      type: "broadcast",
      event: "annotation",
      payload: annotation,
    });
  }, []);

  const startAnnotation = useCallback(
    (point: { x: number; y: number }) => {
      if (activeTool === "pointer") return;

      const annotation: Annotation = {
        id: crypto.randomUUID(),
        tool: activeTool,
        points: [point],
        color: activeColor,
        userId,
        userName,
        timestamp: Date.now(),
      };

      currentAnnotationRef.current = annotation;

      // Add to local state immediately
      setAnnotations((prev) => [...prev, annotation]);
    },
    [activeTool, activeColor, userId, userName]
  );

  const updateAnnotation = useCallback(
    (point: { x: number; y: number }) => {
      const current = currentAnnotationRef.current;
      if (!current) return;

      const updated: Annotation = {
        ...current,
        points: [...current.points, point],
      };

      currentAnnotationRef.current = updated;

      // Update local state
      setAnnotations((prev) =>
        prev.map((a) => (a.id === updated.id ? updated : a))
      );
    },
    []
  );

  const endAnnotation = useCallback(() => {
    const current = currentAnnotationRef.current;
    if (!current) return;

    // Finalize timestamp so TTL starts from completion
    const final: Annotation = { ...current, timestamp: Date.now() };
    currentAnnotationRef.current = null;

    // Update local and broadcast to others
    setAnnotations((prev) =>
      prev.map((a) => (a.id === final.id ? final : a))
    );
    broadcastAnnotation(final);
  }, [broadcastAnnotation]);

  const clearAnnotations = useCallback(() => {
    setAnnotations([]);
    channelRef.current?.send({
      type: "broadcast",
      event: "annotation-clear",
      payload: {},
    });
  }, []);

  return {
    annotations,
    activeTool,
    activeColor,
    setActiveTool,
    setActiveColor,
    startAnnotation,
    updateAnnotation,
    endAnnotation,
    clearAnnotations,
  };
}
