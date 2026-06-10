"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Mic,
  MicOff,
  Video,
  VideoOff,
  Maximize2,
  Minimize2,
  PictureInPicture2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import type { VoiceParticipant } from "@/hooks/use-voice-video";

interface VideoTileProps {
  stream: MediaStream | null;
  name: string;
  isAudioEnabled: boolean;
  isVideoEnabled: boolean;
  isSpeaking: boolean;
  isLocal?: boolean;
  muted?: boolean;
}

function VideoTile({
  stream,
  name,
  isAudioEnabled,
  isVideoEnabled,
  isSpeaking,
  isLocal = false,
  muted = false,
}: VideoTileProps) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (videoRef.current && stream) {
      videoRef.current.srcObject = stream;
    }
  }, [stream]);

  const handlePiP = useCallback(async () => {
    if (videoRef.current) {
      try {
        if (document.pictureInPictureElement) {
          await document.exitPictureInPicture();
        } else {
          await videoRef.current.requestPictureInPicture();
        }
      } catch (err) {
        console.error("PiP failed:", err);
      }
    }
  }, []);

  const hasVideo = isVideoEnabled && stream?.getVideoTracks().some((t) => t.enabled);

  return (
    <div
      className={`relative rounded-lg overflow-hidden bg-muted aspect-video ${
        isSpeaking ? "ring-2 ring-ok ring-offset-1" : ""
      }`}
    >
      {hasVideo ? (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted={muted}
          className="w-full h-full object-cover"
        />
      ) : (
        <div className="w-full h-full flex items-center justify-center">
          <div
            className={`h-16 w-16 rounded-full flex items-center justify-center text-2xl font-semibold ${
              isSpeaking
                ? "bg-ok text-white animate-pulse"
                : "bg-muted-foreground/20 text-muted-foreground"
            }`}
          >
            {name.charAt(0).toUpperCase()}
          </div>
        </div>
      )}

      {/* Name overlay */}
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent px-3 py-2">
        <div className="flex items-center justify-between">
          <span className="text-white text-sm font-medium truncate">
            {name}
            {isLocal && (
              <span className="text-white/70 text-xs ml-1">(You)</span>
            )}
          </span>
          <div className="flex items-center gap-1">
            {!isAudioEnabled && (
              <MicOff className="h-3.5 w-3.5 text-danger" />
            )}
            {!isVideoEnabled && (
              <VideoOff className="h-3.5 w-3.5 text-white/70" />
            )}
          </div>
        </div>
      </div>

      {/* Speaking indicator */}
      {isSpeaking && (
        <div className="absolute top-2 left-2">
          <div className="flex items-center gap-1 bg-ok/90 text-white text-[10px] px-1.5 py-0.5 rounded-full">
            <Mic className="h-2.5 w-2.5" />
            Speaking
          </div>
        </div>
      )}

      {/* PiP button (for local video) */}
      {isLocal && hasVideo && (
        <Button
          variant="ghost"
          size="icon"
          className="absolute top-2 right-2 h-6 w-6 bg-black/40 hover:bg-black/60 text-white"
          onClick={handlePiP}
          aria-label="Picture in picture"
        >
          <PictureInPicture2 className="h-3 w-3" />
        </Button>
      )}
    </div>
  );
}

// ---------- Video Grid ----------

interface VideoGridProps {
  localStream: MediaStream | null;
  remoteStreams: Map<string, MediaStream>;
  participants: VoiceParticipant[];
  isAudioEnabled: boolean;
  isVideoEnabled: boolean;
  userName: string;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}

export function VideoGrid({
  localStream,
  remoteStreams,
  participants,
  isAudioEnabled,
  isVideoEnabled,
  userName,
  collapsed = false,
  onToggleCollapse,
}: VideoGridProps) {
  // Count total tiles: local + remote participants with video
  const videoParticipants = participants.filter(
    (p) => p.isVideoEnabled || remoteStreams.has(p.peerId)
  );
  const hasAnyVideo = isVideoEnabled || videoParticipants.length > 0;
  const totalTiles = (isVideoEnabled ? 1 : 0) + videoParticipants.length;

  // If no video at all, show a compact view
  if (!hasAnyVideo && !collapsed) {
    return null;
  }

  // Determine grid columns based on tile count
  const gridCols =
    totalTiles <= 1
      ? "grid-cols-1"
      : totalTiles <= 2
        ? "grid-cols-2"
        : totalTiles <= 4
          ? "grid-cols-2"
          : "grid-cols-3";

  return (
    <div className="border-t">
      {/* Collapse header */}
      <div className="flex items-center justify-between px-4 py-2 bg-muted/50">
        <span className="text-sm font-medium">
          Video ({totalTiles} participant{totalTiles !== 1 ? "s" : ""})
        </span>
        <Button variant="ghost" size="sm" onClick={onToggleCollapse}>
          {collapsed ? (
            <Maximize2 className="h-3.5 w-3.5" />
          ) : (
            <Minimize2 className="h-3.5 w-3.5" />
          )}
        </Button>
      </div>

      {!collapsed && (
        <div className={`grid ${gridCols} gap-2 p-4`}>
          {/* Local video tile */}
          {isVideoEnabled && (
            <VideoTile
              stream={localStream}
              name={userName}
              isAudioEnabled={isAudioEnabled}
              isVideoEnabled={isVideoEnabled}
              isSpeaking={false}
              isLocal
              muted
            />
          )}

          {/* Remote video tiles */}
          {videoParticipants.map((p) => (
            <VideoTile
              key={p.peerId}
              stream={remoteStreams.get(p.peerId) || null}
              name={p.userName}
              isAudioEnabled={p.isAudioEnabled}
              isVideoEnabled={p.isVideoEnabled}
              isSpeaking={p.isSpeaking}
            />
          ))}
        </div>
      )}
    </div>
  );
}
