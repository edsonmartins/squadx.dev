"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Maximize2,
  Minimize2,
  Volume2,
  VolumeX,
  Settings,
  Wifi,
  WifiOff,
  RefreshCw,
  Loader2,
  MousePointer2,
  MouseOff,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  TooltipProvider,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import {
  useWebRTC,
  ConnectionState,
  parseWebRTCStats,
} from "@/hooks/use-webrtc";
import { useRemoteControl } from "@/hooks/use-remote-control";

interface StreamViewerProps {
  sessionId: string;
  className?: string;
  onConnectionChange?: (state: ConnectionState) => void;
  /** Enable remote control capability (default: false) */
  enableRemoteControl?: boolean;
}

export function StreamViewer({
  sessionId,
  className,
  onConnectionChange,
  enableRemoteControl = false,
}: StreamViewerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isMuted, setIsMuted] = useState(true);
  const [showControls, setShowControls] = useState(true);
  const [showStats, setShowStats] = useState(false);
  const [hasControl, setHasControl] = useState(false);

  const { connectionState, remoteStream, connect, disconnect, stats, dataChannel } =
    useWebRTC({
      sessionId,
      onTrack: (stream) => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      },
      onConnectionStateChange: onConnectionChange,
      enableDataChannel: enableRemoteControl,
    });

  // Remote control hook
  const { requestControl, releaseControl } = useRemoteControl({
    videoElement: videoRef.current,
    dataChannel,
    enabled: enableRemoteControl && hasControl,
    onControlStateChange: setHasControl,
  });

  const parsedStats = parseWebRTCStats(stats);

  // Auto-connect on mount
  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  // Attach stream to video element
  useEffect(() => {
    if (videoRef.current && remoteStream) {
      videoRef.current.srcObject = remoteStream;
    }
  }, [remoteStream]);

  // Handle fullscreen changes
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };

    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => {
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
    };
  }, []);

  // Auto-hide controls
  useEffect(() => {
    let timeout: NodeJS.Timeout;

    if (connectionState === "connected" && showControls) {
      timeout = setTimeout(() => setShowControls(false), 3000);
    }

    return () => clearTimeout(timeout);
  }, [connectionState, showControls]);

  const toggleFullscreen = useCallback(() => {
    if (!containerRef.current) return;

    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      containerRef.current.requestFullscreen();
    }
  }, []);

  const toggleMute = useCallback(() => {
    if (videoRef.current) {
      videoRef.current.muted = !videoRef.current.muted;
      setIsMuted(videoRef.current.muted);
    }
  }, []);

  const toggleControl = useCallback(() => {
    if (hasControl) {
      releaseControl();
    } else {
      requestControl();
    }
  }, [hasControl, requestControl, releaseControl]);

  const handleReconnect = useCallback(() => {
    disconnect();
    setTimeout(() => connect(), 500);
  }, [connect, disconnect]);

  const getConnectionStatusColor = () => {
    switch (connectionState) {
      case "connected":
        return "bg-ok";
      case "connecting":
        return "bg-warn";
      case "failed":
        return "bg-danger";
      default:
        return "bg-neutral";
    }
  };

  return (
    <TooltipProvider>
      <div
        ref={containerRef}
        className={cn(
          "relative bg-black rounded-lg overflow-hidden group",
          className
        )}
        onMouseMove={() => setShowControls(true)}
        onMouseLeave={() =>
          connectionState === "connected" && setShowControls(false)
        }
      >
        {/* Video Element */}
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted={isMuted}
          tabIndex={enableRemoteControl ? 0 : undefined}
          className={cn(
            "w-full h-full object-contain",
            hasControl && "cursor-crosshair"
          )}
        />

        {/* Connection Status Overlay */}
        {connectionState !== "connected" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/80 text-white">
            {connectionState === "connecting" && (
              <>
                <Loader2 className="h-12 w-12 animate-spin mb-4" />
                <p className="text-lg font-medium">Connecting to stream...</p>
                <p className="text-sm text-white/60 mt-2">
                  Establishing WebRTC connection
                </p>
              </>
            )}

            {connectionState === "failed" && (
              <>
                <WifiOff className="h-12 w-12 text-danger mb-4" />
                <p className="text-lg font-medium">Connection failed</p>
                <p className="text-sm text-white/60 mt-2 mb-4">
                  Unable to connect to the live stream
                </p>
                <Button variant="outline" onClick={handleReconnect}>
                  <RefreshCw className="h-4 w-4 mr-2" />
                  Try Again
                </Button>
              </>
            )}

            {connectionState === "disconnected" && (
              <>
                <Wifi className="h-12 w-12 text-neutral mb-4" />
                <p className="text-lg font-medium">Stream disconnected</p>
                <p className="text-sm text-white/60 mt-2 mb-4">
                  The live stream has ended or is unavailable
                </p>
                <Button variant="outline" onClick={handleReconnect}>
                  <RefreshCw className="h-4 w-4 mr-2" />
                  Reconnect
                </Button>
              </>
            )}

            {connectionState === "closed" && (
              <>
                <WifiOff className="h-12 w-12 text-neutral mb-4" />
                <p className="text-lg font-medium">Session ended</p>
                <p className="text-sm text-white/60 mt-2 mb-4">
                  The live session has been closed
                </p>
                <Button variant="outline" onClick={handleReconnect}>
                  <RefreshCw className="h-4 w-4 mr-2" />
                  Reconnect
                </Button>
              </>
            )}

            {connectionState === "reconnecting" && (
              <>
                <Loader2 className="h-12 w-12 animate-spin text-warn mb-4" />
                <p className="text-lg font-medium">Reconnecting...</p>
                <p className="text-sm text-white/60 mt-2">
                  Attempting to restore the connection
                </p>
              </>
            )}
          </div>
        )}

        {/* Controls Overlay */}
        <div
          className={cn(
            "absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent p-4 transition-opacity duration-300",
            showControls || connectionState !== "connected"
              ? "opacity-100"
              : "opacity-0"
          )}
        >
          {/* Bottom Controls */}
          <div className="flex items-center justify-between">
            {/* Left Controls */}
            <div className="flex items-center gap-2">
              {/* Connection Status */}
              <div className="flex items-center gap-2">
                <span
                  className={cn(
                    "h-2 w-2 rounded-full",
                    getConnectionStatusColor()
                  )}
                />
                <Badge variant="secondary" className="capitalize">
                  {connectionState === "connected" ? (
                    <>
                      <span className="mr-1.5">
                        <span className="live-dot inline-block h-1.5 w-1.5" />
                      </span>
                      LIVE
                    </>
                  ) : (
                    connectionState
                  )}
                </Badge>
              </div>

              {/* Stats Display */}
              {showStats && connectionState === "connected" && (
                <div className="flex items-center gap-3 text-xs text-white/70 ml-2">
                  {parsedStats.resolution && (
                    <span>{parsedStats.resolution}</span>
                  )}
                  {parsedStats.framerate > 0 && (
                    <span>{parsedStats.framerate} fps</span>
                  )}
                  {parsedStats.bitrate > 0 && (
                    <span>{parsedStats.bitrate} kbps</span>
                  )}
                </div>
              )}
            </div>

            {/* Right Controls */}
            <div className="flex items-center gap-1">
              {/* Remote Control Toggle */}
              {enableRemoteControl && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      className={cn(
                        "h-8 w-8 text-white hover:bg-white/20",
                        hasControl && "bg-primary/50"
                      )}
                      onClick={toggleControl}
                    >
                      {hasControl ? (
                        <MousePointer2 className="h-4 w-4" />
                      ) : (
                        <MouseOff className="h-4 w-4" />
                      )}
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>
                    <p>{hasControl ? "Release" : "Request"} Control</p>
                  </TooltipContent>
                </Tooltip>
              )}

              {/* Stats Toggle */}
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-white hover:bg-white/20"
                    onClick={() => setShowStats(!showStats)}
                  >
                    <Settings className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{showStats ? "Hide" : "Show"} Stats</p>
                </TooltipContent>
              </Tooltip>

              {/* Mute Toggle */}
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-white hover:bg-white/20"
                    onClick={toggleMute}
                  >
                    {isMuted ? (
                      <VolumeX className="h-4 w-4" />
                    ) : (
                      <Volume2 className="h-4 w-4" />
                    )}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{isMuted ? "Unmute" : "Mute"}</p>
                </TooltipContent>
              </Tooltip>

              {/* Fullscreen Toggle */}
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-white hover:bg-white/20"
                    onClick={toggleFullscreen}
                  >
                    {isFullscreen ? (
                      <Minimize2 className="h-4 w-4" />
                    ) : (
                      <Maximize2 className="h-4 w-4" />
                    )}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{isFullscreen ? "Exit Fullscreen" : "Fullscreen"}</p>
                </TooltipContent>
              </Tooltip>
            </div>
          </div>
        </div>

        {/* Session ID Display (top) */}
        <div
          className={cn(
            "absolute top-4 right-4 transition-opacity duration-300",
            showControls || connectionState !== "connected"
              ? "opacity-100"
              : "opacity-0"
          )}
        >
          <Badge variant="secondary" className="font-mono text-xs">
            {sessionId.slice(0, 8)}...
          </Badge>
        </div>
      </div>
    </TooltipProvider>
  );
}
