"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Input event types for remote control.
 */
export type InputEventType =
  | "mousemove"
  | "mousedown"
  | "mouseup"
  | "wheel"
  | "keydown"
  | "keyup";

/**
 * Mouse button mapping.
 */
export type MouseButton = "left" | "middle" | "right";

/**
 * Input event data structure sent via data channel.
 */
export interface InputEvent {
  type: InputEventType;
  timestamp: number;

  // Mouse events
  x?: number; // Relative position (0-1)
  y?: number;
  button?: MouseButton;
  deltaX?: number;
  deltaY?: number;

  // Keyboard events
  key?: string;
  code?: string;
  modifiers?: {
    ctrl?: boolean;
    alt?: boolean;
    shift?: boolean;
    meta?: boolean;
  };
}

interface UseRemoteControlOptions {
  /** Video element to attach event listeners to */
  videoElement: HTMLVideoElement | null;
  /** Data channel to send events through */
  dataChannel: RTCDataChannel | null;
  /** Whether remote control is enabled */
  enabled: boolean;
  /** Called when control state changes */
  onControlStateChange?: (active: boolean) => void;
}

interface UseRemoteControlReturn {
  /** Whether control is currently active */
  isActive: boolean;
  /** Request control of the remote session */
  requestControl: () => void;
  /** Release control of the remote session */
  releaseControl: () => void;
}

/**
 * Hook for handling remote control input events.
 * Captures mouse and keyboard events and sends them via WebRTC data channel.
 */
export function useRemoteControl({
  videoElement,
  dataChannel,
  enabled,
  onControlStateChange,
}: UseRemoteControlOptions): UseRemoteControlReturn {
  const [isActive, setIsActive] = useState(false);
  const isActiveRef = useRef(false);

  // Send input event via data channel
  const sendInputEvent = useCallback(
    (event: InputEvent) => {
      if (!dataChannel || dataChannel.readyState !== "open") {
        return;
      }

      try {
        dataChannel.send(
          JSON.stringify({
            type: "input",
            event,
          })
        );
      } catch (error) {
        console.warn("[RemoteControl] Error sending input event:", error);
      }
    },
    [dataChannel]
  );

  // Get relative mouse position within video element
  const getRelativePosition = useCallback(
    (e: MouseEvent): { x: number; y: number } | null => {
      if (!videoElement) return null;

      const rect = videoElement.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;

      // Clamp to valid range
      return {
        x: Math.max(0, Math.min(1, x)),
        y: Math.max(0, Math.min(1, y)),
      };
    },
    [videoElement]
  );

  // Convert mouse button number to string
  const getMouseButton = (button: number): MouseButton => {
    switch (button) {
      case 0:
        return "left";
      case 1:
        return "middle";
      case 2:
        return "right";
      default:
        return "left";
    }
  };

  // Get keyboard modifiers
  const getModifiers = (e: KeyboardEvent) => ({
    ctrl: e.ctrlKey,
    alt: e.altKey,
    shift: e.shiftKey,
    meta: e.metaKey,
  });

  // Handle mouse move
  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isActiveRef.current) return;

      const pos = getRelativePosition(e);
      if (!pos) return;

      sendInputEvent({
        type: "mousemove",
        timestamp: Date.now(),
        x: pos.x,
        y: pos.y,
      });
    },
    [getRelativePosition, sendInputEvent]
  );

  // Handle mouse down
  const handleMouseDown = useCallback(
    (e: MouseEvent) => {
      if (!isActiveRef.current) return;

      e.preventDefault();
      const pos = getRelativePosition(e);
      if (!pos) return;

      sendInputEvent({
        type: "mousedown",
        timestamp: Date.now(),
        x: pos.x,
        y: pos.y,
        button: getMouseButton(e.button),
      });
    },
    [getRelativePosition, sendInputEvent]
  );

  // Handle mouse up
  const handleMouseUp = useCallback(
    (e: MouseEvent) => {
      if (!isActiveRef.current) return;

      e.preventDefault();
      const pos = getRelativePosition(e);
      if (!pos) return;

      sendInputEvent({
        type: "mouseup",
        timestamp: Date.now(),
        x: pos.x,
        y: pos.y,
        button: getMouseButton(e.button),
      });
    },
    [getRelativePosition, sendInputEvent]
  );

  // Handle wheel events
  const handleWheel = useCallback(
    (e: WheelEvent) => {
      if (!isActiveRef.current) return;

      e.preventDefault();
      const pos = getRelativePosition(e);
      if (!pos) return;

      sendInputEvent({
        type: "wheel",
        timestamp: Date.now(),
        x: pos.x,
        y: pos.y,
        deltaX: e.deltaX,
        deltaY: e.deltaY,
      });
    },
    [getRelativePosition, sendInputEvent]
  );

  // Handle key down
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (!isActiveRef.current) return;

      // Prevent default for most keys when control is active
      // but allow some browser shortcuts
      if (!e.metaKey && !e.ctrlKey) {
        e.preventDefault();
      }

      sendInputEvent({
        type: "keydown",
        timestamp: Date.now(),
        key: e.key,
        code: e.code,
        modifiers: getModifiers(e),
      });
    },
    [sendInputEvent]
  );

  // Handle key up
  const handleKeyUp = useCallback(
    (e: KeyboardEvent) => {
      if (!isActiveRef.current) return;

      e.preventDefault();

      sendInputEvent({
        type: "keyup",
        timestamp: Date.now(),
        key: e.key,
        code: e.code,
        modifiers: getModifiers(e),
      });
    },
    [sendInputEvent]
  );

  // Attach event listeners
  useEffect(() => {
    if (!videoElement || !enabled) return;

    // Mouse events on video element
    videoElement.addEventListener("mousemove", handleMouseMove);
    videoElement.addEventListener("mousedown", handleMouseDown);
    videoElement.addEventListener("mouseup", handleMouseUp);
    videoElement.addEventListener("wheel", handleWheel, { passive: false });

    // Prevent context menu
    const handleContextMenu = (e: MouseEvent) => {
      if (isActiveRef.current) {
        e.preventDefault();
      }
    };
    videoElement.addEventListener("contextmenu", handleContextMenu);

    // Keyboard events on document when video is focused
    const handleKeyDownWrapper = (e: KeyboardEvent) => {
      // Only handle if video element is focused or in fullscreen
      if (
        document.activeElement === videoElement ||
        document.fullscreenElement === videoElement.parentElement
      ) {
        handleKeyDown(e);
      }
    };

    const handleKeyUpWrapper = (e: KeyboardEvent) => {
      if (
        document.activeElement === videoElement ||
        document.fullscreenElement === videoElement.parentElement
      ) {
        handleKeyUp(e);
      }
    };

    document.addEventListener("keydown", handleKeyDownWrapper);
    document.addEventListener("keyup", handleKeyUpWrapper);

    return () => {
      videoElement.removeEventListener("mousemove", handleMouseMove);
      videoElement.removeEventListener("mousedown", handleMouseDown);
      videoElement.removeEventListener("mouseup", handleMouseUp);
      videoElement.removeEventListener("wheel", handleWheel);
      videoElement.removeEventListener("contextmenu", handleContextMenu);
      document.removeEventListener("keydown", handleKeyDownWrapper);
      document.removeEventListener("keyup", handleKeyUpWrapper);
    };
  }, [
    videoElement,
    enabled,
    handleMouseMove,
    handleMouseDown,
    handleMouseUp,
    handleWheel,
    handleKeyDown,
    handleKeyUp,
  ]);

  // Request control
  const requestControl = useCallback(() => {
    if (!dataChannel || dataChannel.readyState !== "open") {
      console.warn("[RemoteControl] Cannot request control: data channel not ready");
      return;
    }

    dataChannel.send(
      JSON.stringify({
        type: "control-request",
        timestamp: Date.now(),
      })
    );

    isActiveRef.current = true;
    setIsActive(true);
    onControlStateChange?.(true);
  }, [dataChannel, onControlStateChange]);

  // Release control
  const releaseControl = useCallback(() => {
    if (!dataChannel || dataChannel.readyState !== "open") {
      isActiveRef.current = false;
      setIsActive(false);
      onControlStateChange?.(false);
      return;
    }

    dataChannel.send(
      JSON.stringify({
        type: "control-release",
        timestamp: Date.now(),
      })
    );

    isActiveRef.current = false;
    setIsActive(false);
    onControlStateChange?.(false);
  }, [dataChannel, onControlStateChange]);

  return {
    isActive,
    requestControl,
    releaseControl,
  };
}
