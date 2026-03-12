"use client";

import { useCallback, useEffect, useRef } from "react";
import { Annotation, AnnotationTool } from "@/hooks/use-annotations";

/** Duration in ms before annotations start fading */
const ANNOTATION_TTL = 30_000;
/** Duration in ms of the fade-out effect */
const FADE_DURATION = 5_000;

interface AnnotationOverlayProps {
  annotations: Annotation[];
  activeTool: AnnotationTool;
  onAnnotationStart: (point: { x: number; y: number }) => void;
  onAnnotationUpdate: (point: { x: number; y: number }) => void;
  onAnnotationEnd: () => void;
  isEnabled: boolean;
}

/**
 * Calculate opacity for an annotation based on its age.
 * Full opacity until (TTL - FADE_DURATION), then linear fade to 0.
 */
function getAnnotationOpacity(timestamp: number, now: number): number {
  const age = now - timestamp;
  const fadeStart = ANNOTATION_TTL - FADE_DURATION;

  if (age < fadeStart) return 1;
  if (age >= ANNOTATION_TTL) return 0;

  return 1 - (age - fadeStart) / FADE_DURATION;
}

/**
 * Normalize a mouse/touch event position to [0,1] coordinates
 * relative to the canvas element.
 */
function normalizePoint(
  canvas: HTMLCanvasElement,
  clientX: number,
  clientY: number
): { x: number; y: number } {
  const rect = canvas.getBoundingClientRect();
  return {
    x: (clientX - rect.left) / rect.width,
    y: (clientY - rect.top) / rect.height,
  };
}

export function AnnotationOverlay({
  annotations,
  activeTool,
  onAnnotationStart,
  onAnnotationUpdate,
  onAnnotationEnd,
  isEnabled,
}: AnnotationOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const isDrawingRef = useRef(false);
  const animFrameRef = useRef<number>(0);

  // ---- Drawing helpers ----

  const drawArrowhead = useCallback(
    (ctx: CanvasRenderingContext2D, from: { x: number; y: number }, to: { x: number; y: number }, size: number) => {
      const angle = Math.atan2(to.y - from.y, to.x - from.x);
      ctx.beginPath();
      ctx.moveTo(to.x, to.y);
      ctx.lineTo(
        to.x - size * Math.cos(angle - Math.PI / 6),
        to.y - size * Math.sin(angle - Math.PI / 6)
      );
      ctx.lineTo(
        to.x - size * Math.cos(angle + Math.PI / 6),
        to.y - size * Math.sin(angle + Math.PI / 6)
      );
      ctx.closePath();
      ctx.fill();
    },
    []
  );

  const drawAnnotation = useCallback(
    (ctx: CanvasRenderingContext2D, annotation: Annotation, w: number, h: number, opacity: number) => {
      ctx.save();
      ctx.globalAlpha = opacity;
      ctx.strokeStyle = annotation.color;
      ctx.fillStyle = annotation.color;
      ctx.lineWidth = 2;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";

      const pts = annotation.points.map((p) => ({ x: p.x * w, y: p.y * h }));

      switch (annotation.tool) {
        case "freehand": {
          if (pts.length < 2) break;
          ctx.beginPath();
          ctx.moveTo(pts[0].x, pts[0].y);
          for (let i = 1; i < pts.length; i++) {
            ctx.lineTo(pts[i].x, pts[i].y);
          }
          ctx.stroke();
          break;
        }
        case "rectangle": {
          if (pts.length < 2) break;
          const first = pts[0];
          const last = pts[pts.length - 1];
          ctx.strokeRect(
            first.x,
            first.y,
            last.x - first.x,
            last.y - first.y
          );
          break;
        }
        case "arrow": {
          if (pts.length < 2) break;
          const start = pts[0];
          const end = pts[pts.length - 1];
          ctx.beginPath();
          ctx.moveTo(start.x, start.y);
          ctx.lineTo(end.x, end.y);
          ctx.stroke();
          drawArrowhead(ctx, start, end, 12);
          break;
        }
        case "text": {
          if (pts.length < 1) break;
          ctx.font = "14px sans-serif";
          ctx.fillText(annotation.userName, pts[0].x, pts[0].y);
          break;
        }
        default:
          break;
      }

      ctx.restore();
    },
    [drawArrowhead]
  );

  // ---- Render loop ----

  const render = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Match canvas resolution to display size
    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const w = rect.width * dpr;
    const h = rect.height * dpr;

    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, rect.width, rect.height);

    const now = Date.now();

    for (const annotation of annotations) {
      const opacity = getAnnotationOpacity(annotation.timestamp, now);
      if (opacity <= 0) continue;
      drawAnnotation(ctx, annotation, rect.width, rect.height, opacity);
    }

    animFrameRef.current = requestAnimationFrame(render);
  }, [annotations, drawAnnotation]);

  useEffect(() => {
    animFrameRef.current = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animFrameRef.current);
  }, [render]);

  // ---- Pointer events ----

  const handlePointerDown = useCallback(
    (e: React.PointerEvent<HTMLCanvasElement>) => {
      if (!isEnabled || activeTool === "pointer") return;

      const canvas = canvasRef.current;
      if (!canvas) return;

      isDrawingRef.current = true;
      canvas.setPointerCapture(e.pointerId);

      const point = normalizePoint(canvas, e.clientX, e.clientY);
      onAnnotationStart(point);
    },
    [isEnabled, activeTool, onAnnotationStart]
  );

  const handlePointerMove = useCallback(
    (e: React.PointerEvent<HTMLCanvasElement>) => {
      if (!isDrawingRef.current) return;

      const canvas = canvasRef.current;
      if (!canvas) return;

      const point = normalizePoint(canvas, e.clientX, e.clientY);
      onAnnotationUpdate(point);
    },
    [onAnnotationUpdate]
  );

  const handlePointerUp = useCallback(() => {
    if (!isDrawingRef.current) return;
    isDrawingRef.current = false;
    onAnnotationEnd();
  }, [onAnnotationEnd]);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 w-full h-full z-10"
      style={{
        pointerEvents: isEnabled && activeTool !== "pointer" ? "auto" : "none",
        cursor: isEnabled && activeTool !== "pointer" ? "crosshair" : "default",
        touchAction: "none",
      }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerLeave={handlePointerUp}
    />
  );
}
