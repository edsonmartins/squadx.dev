"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Video, Play, Clock, HardDrive, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import {
  recordingsApi,
  liveViewApi,
  RecordingResponse,
  RecordingStatus,
  organizationsApi,
} from "@/lib/api";
import { RECORDING_STATUS_TONE, TONE_BADGE } from "@/lib/design/semantics";

const statusColors: Record<RecordingStatus, string> = {
  RECORDING: TONE_BADGE[RECORDING_STATUS_TONE.RECORDING],
  COMPLETED: TONE_BADGE[RECORDING_STATUS_TONE.COMPLETED],
  FAILED: TONE_BADGE[RECORDING_STATUS_TONE.FAILED],
};

const statusIcons: Record<RecordingStatus, React.ReactNode> = {
  RECORDING: <span className="relative flex h-2 w-2 mr-1.5"><span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-danger opacity-75"></span><span className="relative inline-flex rounded-full h-2 w-2 bg-danger"></span></span>,
  COMPLETED: null,
  FAILED: <AlertCircle className="h-3 w-3 mr-1" />,
};

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDuration(seconds?: number) {
  if (!seconds) return "--";
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  if (mins < 60) return `${mins}m ${secs}s`;
  const hrs = Math.floor(mins / 60);
  return `${hrs}h ${mins % 60}m`;
}

function formatFileSize(bytes?: number) {
  if (!bytes) return "--";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export default function RecordingsPage() {
  const [sessionIdInput, setSessionIdInput] = useState("");
  const [searchSessionId, setSearchSessionId] = useState<number | null>(null);
  const { toast } = useToast();

  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const defaultOrgId = organizations?.content?.[0]?.id;

  // Fetch active live sessions to find recordings
  const { data: activeSessions } = useQuery({
    queryKey: ["live-sessions", defaultOrgId],
    queryFn: () => liveViewApi.getActiveByOrganization(defaultOrgId!),
    enabled: !!defaultOrgId,
  });

  // Fetch recordings by session ID
  const { data: recordings, isLoading } = useQuery({
    queryKey: ["recordings", searchSessionId],
    queryFn: () => recordingsApi.listBySession(searchSessionId!),
    enabled: !!searchSessionId,
  });

  // If we have active sessions, also fetch their recordings
  const firstSessionId = activeSessions?.[0]?.id;
  const { data: activeRecordings, isLoading: isLoadingActive } = useQuery({
    queryKey: ["recordings", "active", firstSessionId],
    queryFn: () => recordingsApi.listBySession(firstSessionId!),
    enabled: !!firstSessionId && !searchSessionId,
  });

  const displayRecordings = searchSessionId ? recordings : activeRecordings;
  const loading = isLoading || isLoadingActive;

  const handleSearch = () => {
    const id = parseInt(sessionIdInput);
    if (!isNaN(id) && id > 0) {
      setSearchSessionId(id);
    }
  };

  const handleWatch = async (recording: RecordingResponse) => {
    try {
      const data = await recordingsApi.getUrl(recording.id);
      if (data.playback_url) {
        window.open(data.playback_url, "_blank");
      } else {
        toast({
          title: "Unavailable",
          description: "Playback URL is not available for this recording.",
          variant: "destructive",
        });
      }
    } catch {
      toast({
        title: "Error",
        description: "Failed to get playback URL. Please try again.",
        variant: "destructive",
      });
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Recordings</h1>
        <p className="text-muted-foreground">
          Browse and watch session recordings
        </p>
      </div>

      {/* Search by Session */}
      <div className="flex items-end gap-3">
        <div className="space-y-2">
          <Label htmlFor="session-id">Session ID</Label>
          <Input
            id="session-id"
            type="number"
            placeholder="Enter session ID..."
            className="w-full sm:w-[200px]"
            value={sessionIdInput}
            onChange={(e) => setSessionIdInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            data-testid="recording-session-id-input"
          />
        </div>
        <Button onClick={handleSearch} variant="outline" data-testid="recording-search-button">
          Search
        </Button>
        {searchSessionId && (
          <Button
            variant="ghost"
            onClick={() => {
              setSearchSessionId(null);
              setSessionIdInput("");
            }}
          >
            Clear
          </Button>
        )}
      </div>

      {/* Recordings List */}
      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : !displayRecordings || displayRecordings.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-64 gap-4">
          <Video className="h-12 w-12 text-muted-foreground" />
          <p className="text-muted-foreground">
            {searchSessionId
              ? "No recordings found for this session"
              : "No recordings available"}
          </p>
          <p className="text-sm text-muted-foreground">
            Enter a session ID above to search for recordings
          </p>
        </div>
      ) : (
        <div className="grid gap-4">
          {displayRecordings.map((recording) => (
            <Card key={recording.id} data-testid={`recording-card-${recording.id}`}>
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <CardTitle className="text-base">
                    Session #{recording.session_id}
                  </CardTitle>
                  <Badge
                    variant="secondary"
                    className={`flex items-center ${statusColors[recording.status]}`}
                  >
                    {statusIcons[recording.status]}
                    {recording.status}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3.5 w-3.5" />
                      Started {formatDate(recording.started_at)}
                    </span>
                    <span className="flex items-center gap-1">
                      <Video className="h-3.5 w-3.5" />
                      {formatDuration(recording.duration_seconds)}
                    </span>
                    <span className="flex items-center gap-1">
                      <HardDrive className="h-3.5 w-3.5" />
                      {formatFileSize(recording.file_size_bytes)}
                    </span>
                    {recording.completed_at && (
                      <span>Completed {formatDate(recording.completed_at)}</span>
                    )}
                  </div>
                  {recording.status === "COMPLETED" && (
                    <Button
                      size="sm"
                      onClick={() => handleWatch(recording)}
                    >
                      <Play className="mr-1.5 h-4 w-4" />
                      Watch
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
