"use client";

import { useCallback, useState } from "react";
import {
  Mic,
  MicOff,
  Video,
  VideoOff,
  Settings,
  Hand,
  ChevronDown,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import type { VoiceParticipant } from "@/hooks/use-voice-video";

interface VoiceControlsProps {
  isAudioEnabled: boolean;
  isVideoEnabled: boolean;
  toggleAudio: () => void;
  toggleVideo: () => void;
  isPushToTalk: boolean;
  setPushToTalk: (enabled: boolean) => void;
  audioLevel: number;
  participants: VoiceParticipant[];
  availableDevices: MediaDeviceInfo[];
  selectedAudioDevice: string;
  selectedVideoDevice: string;
  onSelectAudioDevice: (deviceId: string) => void;
  onSelectVideoDevice: (deviceId: string) => void;
}

export function VoiceControls({
  isAudioEnabled,
  isVideoEnabled,
  toggleAudio,
  toggleVideo,
  isPushToTalk,
  setPushToTalk,
  audioLevel,
  participants,
  availableDevices,
  selectedAudioDevice,
  selectedVideoDevice,
  onSelectAudioDevice,
  onSelectVideoDevice,
}: VoiceControlsProps) {
  const audioInputDevices = availableDevices.filter(
    (d) => d.kind === "audioinput"
  );
  const videoInputDevices = availableDevices.filter(
    (d) => d.kind === "videoinput"
  );

  const speakingParticipants = participants.filter((p) => p.isSpeaking);

  return (
    <div className="flex items-center gap-2">
      {/* Audio level indicator */}
      <div className="flex items-center gap-1.5 mr-1">
        <div className="flex items-end gap-px h-4">
          {[20, 40, 60, 80, 100].map((threshold) => (
            <div
              key={threshold}
              className={`w-1 rounded-sm transition-colors ${
                audioLevel >= threshold
                  ? threshold <= 60
                    ? "bg-green-500"
                    : threshold <= 80
                      ? "bg-yellow-500"
                      : "bg-red-500"
                  : "bg-muted"
              }`}
              style={{ height: `${threshold / 25 + 4}px` }}
            />
          ))}
        </div>
      </div>

      {/* Microphone toggle */}
      <Button
        variant={isAudioEnabled ? "outline" : "destructive"}
        size="sm"
        onClick={toggleAudio}
        className="relative"
      >
        {isAudioEnabled ? (
          <Mic className="h-4 w-4" />
        ) : (
          <MicOff className="h-4 w-4" />
        )}
      </Button>

      {/* Camera toggle */}
      <Button
        variant={isVideoEnabled ? "outline" : "secondary"}
        size="sm"
        onClick={toggleVideo}
      >
        {isVideoEnabled ? (
          <Video className="h-4 w-4" />
        ) : (
          <VideoOff className="h-4 w-4" />
        )}
      </Button>

      {/* Push-to-talk indicator */}
      {isPushToTalk && (
        <div className="flex items-center gap-1 text-xs text-muted-foreground px-2 py-1 bg-muted rounded">
          <Hand className="h-3 w-3" />
          <span>PTT</span>
        </div>
      )}

      {/* Participant avatars */}
      {participants.length > 0 && (
        <div className="flex items-center -space-x-2 ml-2">
          {participants.slice(0, 5).map((p) => (
            <div
              key={p.peerId}
              className={`relative h-7 w-7 rounded-full border-2 border-background flex items-center justify-center text-[10px] font-medium ${
                p.isSpeaking
                  ? "bg-green-500 text-white ring-2 ring-green-400 ring-offset-1 animate-pulse"
                  : "bg-muted text-muted-foreground"
              }`}
              title={`${p.userName}${!p.isAudioEnabled ? " (muted)" : ""}`}
            >
              {p.userName.charAt(0).toUpperCase()}
              {!p.isAudioEnabled && (
                <div className="absolute -bottom-0.5 -right-0.5 h-3 w-3 bg-destructive rounded-full flex items-center justify-center">
                  <MicOff className="h-2 w-2 text-white" />
                </div>
              )}
            </div>
          ))}
          {participants.length > 5 && (
            <div className="h-7 w-7 rounded-full border-2 border-background bg-muted flex items-center justify-center text-[10px] font-medium text-muted-foreground">
              +{participants.length - 5}
            </div>
          )}
        </div>
      )}

      {/* Settings dropdown */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="sm">
            <Settings className="h-4 w-4" />
            <ChevronDown className="h-3 w-3 ml-1" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64">
          {/* Push-to-talk toggle */}
          <div className="flex items-center justify-between px-2 py-2">
            <Label htmlFor="ptt-toggle" className="text-sm cursor-pointer">
              Push-to-Talk (Space)
            </Label>
            <Switch
              id="ptt-toggle"
              checked={isPushToTalk}
              onCheckedChange={setPushToTalk}
            />
          </div>

          <DropdownMenuSeparator />

          {/* Audio devices */}
          <DropdownMenuLabel className="text-xs">Microphone</DropdownMenuLabel>
          {audioInputDevices.length > 0 ? (
            audioInputDevices.map((device) => (
              <DropdownMenuItem
                key={device.deviceId}
                onClick={() => onSelectAudioDevice(device.deviceId)}
                className={
                  selectedAudioDevice === device.deviceId ? "bg-accent" : ""
                }
              >
                <Mic className="h-3 w-3 mr-2 flex-shrink-0" />
                <span className="truncate text-xs">
                  {device.label || `Microphone ${device.deviceId.slice(0, 8)}`}
                </span>
              </DropdownMenuItem>
            ))
          ) : (
            <DropdownMenuItem disabled>
              <span className="text-xs text-muted-foreground">
                No microphones found
              </span>
            </DropdownMenuItem>
          )}

          <DropdownMenuSeparator />

          {/* Video devices */}
          <DropdownMenuLabel className="text-xs">Camera</DropdownMenuLabel>
          {videoInputDevices.length > 0 ? (
            videoInputDevices.map((device) => (
              <DropdownMenuItem
                key={device.deviceId}
                onClick={() => onSelectVideoDevice(device.deviceId)}
                className={
                  selectedVideoDevice === device.deviceId ? "bg-accent" : ""
                }
              >
                <Video className="h-3 w-3 mr-2 flex-shrink-0" />
                <span className="truncate text-xs">
                  {device.label || `Camera ${device.deviceId.slice(0, 8)}`}
                </span>
              </DropdownMenuItem>
            ))
          ) : (
            <DropdownMenuItem disabled>
              <span className="text-xs text-muted-foreground">
                No cameras found
              </span>
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
