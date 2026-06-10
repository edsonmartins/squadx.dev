"use client";

import { use, useCallback, useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  Users,
  Clock,
  Copy,
  Check,
  MessageSquare,
  Send,
  PenTool,
  Phone,
  PhoneOff,
} from "lucide-react";
import { format } from "date-fns";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { StreamViewer } from "@/components/live/stream-viewer";
import { AnnotationOverlay } from "@/components/live/annotation-overlay";
import { AnnotationToolbar } from "@/components/live/annotation-toolbar";
import { VoiceControls } from "@/components/live/voice-controls";
import { VideoGrid } from "@/components/live/video-grid";
import { signaling, LiveSession, LiveParticipant, ChatMessage } from "@/lib/supabase";
import { liveViewApi } from "@/lib/api";
import { useRealtimeChat } from "@/hooks/use-realtime-chat";
import { useAnnotations } from "@/hooks/use-annotations";
import { useVoiceVideo } from "@/hooks/use-voice-video";
import { ConnectionState } from "@/hooks/use-webrtc";
import { useAuthStore } from "@/stores/auth-store";

interface LiveStreamPageProps {
  params: Promise<{ code: string }>;
}

export default function LiveStreamPage({ params }: LiveStreamPageProps) {
  const { code } = use(params);
  const router = useRouter();
  const { user } = useAuthStore();

  const [copied, setCopied] = useState(false);
  const [connectionState, setConnectionState] =
    useState<ConnectionState>("disconnected");
  const [newMessage, setNewMessage] = useState("");
  const [annotationsEnabled, setAnnotationsEnabled] = useState(false);
  const [voiceEnabled, setVoiceEnabled] = useState(false);
  const [videoGridCollapsed, setVideoGridCollapsed] = useState(false);
  const chatContainerRef = useRef<HTMLDivElement>(null);

  // Fetch session from backend API (try Spring Boot first, then Supabase)
  const { data: session, isLoading: sessionLoading } = useQuery({
    queryKey: ["live-session", code],
    queryFn: async () => {
      try {
        return await liveViewApi.getByCode(code);
      } catch {
        // Fallback to Supabase endpoint for Python client sessions
        return await liveViewApi.supabase.getByCode(code);
      }
    },
    retry: false,
  });

  // Fetch session from Supabase (for WebRTC signaling)
  const { data: supabaseSession } = useQuery({
    queryKey: ["supabase-session", code],
    queryFn: () => signaling.getSessionByCode(code),
  });

  // Fetch participants from Supabase
  const { data: participants } = useQuery({
    queryKey: ["participants", supabaseSession?.id],
    queryFn: () =>
      supabaseSession
        ? signaling.getParticipants(supabaseSession.id)
        : Promise.resolve([]),
    enabled: !!supabaseSession,
    refetchInterval: 5000,
  });

  // Real-time chat via Supabase
  const { messages: chatMessages, sendMessage } = useRealtimeChat({
    sessionCode: code,
    displayName: user?.full_name || user?.email || "Anonymous",
  });

  // Annotations
  const {
    annotations,
    activeTool,
    activeColor,
    setActiveTool,
    setActiveColor,
    startAnnotation,
    updateAnnotation,
    endAnnotation,
    clearAnnotations,
  } = useAnnotations({
    sessionCode: code,
    userId: user?.id?.toString() || "anonymous",
    userName: user?.full_name || user?.email || "Anonymous",
  });

  // Voice/Video calls
  const voiceVideo = useVoiceVideo({
    sessionCode: code,
    userId: user?.id?.toString() || "anonymous",
    userName: user?.full_name || user?.email || "Anonymous",
    enabled: voiceEnabled,
  });

  // Push-to-talk keyboard shortcut (Space key)
  useEffect(() => {
    if (!voiceEnabled || !voiceVideo.isPushToTalk) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Only trigger on Space, and not when typing in an input
      if (
        e.code === "Space" &&
        !["INPUT", "TEXTAREA", "SELECT"].includes(
          (e.target as HTMLElement)?.tagName
        )
      ) {
        e.preventDefault();
        voiceVideo.startPushToTalk();
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (
        e.code === "Space" &&
        !["INPUT", "TEXTAREA", "SELECT"].includes(
          (e.target as HTMLElement)?.tagName
        )
      ) {
        e.preventDefault();
        voiceVideo.stopPushToTalk();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("keyup", handleKeyUp);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
    };
  }, [voiceEnabled, voiceVideo]);

  // Auto-scroll chat to bottom when new messages arrive
  useEffect(() => {
    if (chatContainerRef.current) {
      chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
    }
  }, [chatMessages]);

  const handleCopyCode = useCallback(() => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [code]);

  const handleSendMessage = useCallback(async () => {
    if (newMessage.trim()) {
      await sendMessage(newMessage.trim());
      setNewMessage("");
    }
  }, [newMessage, sendMessage]);

  const handleKeyPress = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        handleSendMessage();
      }
    },
    [handleSendMessage]
  );

  if (sessionLoading) {
    return (
      <div className="flex items-center justify-center h-[calc(100vh-4rem)]">
        <div className="animate-pulse text-center">
          <div className="h-8 w-32 bg-muted rounded mx-auto mb-4" />
          <div className="h-4 w-48 bg-muted rounded mx-auto" />
        </div>
      </div>
    );
  }

  if (!session) {
    return (
      <div className="flex flex-col items-center justify-center h-[calc(100vh-4rem)]">
        <h1 className="text-2xl font-bold mb-2">Session Not Found</h1>
        <p className="text-muted-foreground mb-4">
          The session code &quot;{code}&quot; does not exist or has ended.
        </p>
        <Button onClick={() => router.push("/live")}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Live View
        </Button>
      </div>
    );
  }

  const sessionId = supabaseSession?.id || session.id.toString();
  const sessionTitle = session.task_title || session.agent_name || "Direct Agent Session";
  const sessionSubtitle =
    session.session_mode === "DIRECT_AGENT" && session.agent_name
      ? `Always-available conversation with ${session.agent_name}`
      : `Hosted by ${session.host_user_name}`;

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)]">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-6 py-3">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={() => router.push("/live")}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back
          </Button>
          <div>
            <h1 className="text-lg font-semibold">{sessionTitle}</h1>
            <p className="text-sm text-muted-foreground">
              {sessionSubtitle}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          {/* Session Code */}
          <Button
            variant="outline"
            size="sm"
            className="font-mono"
            onClick={handleCopyCode}
          >
            {copied ? (
              <>
                <Check className="h-4 w-4 mr-2 text-ok" />
                Copied!
              </>
            ) : (
              <>
                <Copy className="h-4 w-4 mr-2" />
                {code}
              </>
            )}
          </Button>

          {/* Annotations Toggle */}
          <Button
            variant={annotationsEnabled ? "default" : "outline"}
            size="sm"
            onClick={() => setAnnotationsEnabled((prev) => !prev)}
          >
            <PenTool className="h-4 w-4 mr-2" />
            {annotationsEnabled ? "Drawing" : "Annotate"}
          </Button>

          {/* Voice/Video Toggle */}
          <Button
            variant={voiceEnabled ? "destructive" : "outline"}
            size="sm"
            onClick={() => setVoiceEnabled((prev) => !prev)}
          >
            {voiceEnabled ? (
              <>
                <PhoneOff className="h-4 w-4 mr-2" />
                Leave Call
              </>
            ) : (
              <>
                <Phone className="h-4 w-4 mr-2" />
                Join Call
              </>
            )}
          </Button>

          {/* Status Badge */}
          <Badge
            variant={session.status === "ACTIVE" ? "default" : "secondary"}
            className={
              session.status === "ACTIVE"
                ? "bg-live text-white hover:bg-live/85"
                : undefined
            }
          >
            {session.status === "ACTIVE" && (
              <span className="animate-pulse mr-1.5">
                <span className="inline-block h-1.5 w-1.5 bg-white rounded-full" />
              </span>
            )}
            {session.status}
          </Badge>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex flex-1 overflow-hidden">
        {/* Video Area */}
        <div className="flex-1 flex flex-col">
          <div className="flex-1 p-4">
            <div className="relative w-full h-full min-h-[400px]">
              <StreamViewer
                sessionId={sessionId}
                className="w-full h-full"
                onConnectionChange={setConnectionState}
              />
              <AnnotationOverlay
                annotations={annotations}
                activeTool={activeTool}
                onAnnotationStart={startAnnotation}
                onAnnotationUpdate={updateAnnotation}
                onAnnotationEnd={endAnnotation}
                isEnabled={annotationsEnabled}
              />
              {annotationsEnabled && (
                <AnnotationToolbar
                  activeTool={activeTool}
                  activeColor={activeColor}
                  onToolChange={setActiveTool}
                  onColorChange={setActiveColor}
                  onClear={clearAnnotations}
                />
              )}
            </div>
          </div>

          {/* Video Grid (collapsible, below stream viewer) */}
          {voiceEnabled && (
            <VideoGrid
              localStream={voiceVideo.localStream}
              remoteStreams={voiceVideo.remoteStreams}
              participants={voiceVideo.participants}
              isAudioEnabled={voiceVideo.isAudioEnabled}
              isVideoEnabled={voiceVideo.isVideoEnabled}
              userName={user?.full_name || user?.email || "Anonymous"}
              collapsed={videoGridCollapsed}
              onToggleCollapse={() => setVideoGridCollapsed((prev) => !prev)}
            />
          )}

          {/* Bottom bar with voice controls */}
          {voiceEnabled && (
            <div className="flex items-center justify-center border-t px-4 py-2 bg-muted/30">
              <VoiceControls
                isAudioEnabled={voiceVideo.isAudioEnabled}
                isVideoEnabled={voiceVideo.isVideoEnabled}
                toggleAudio={voiceVideo.toggleAudio}
                toggleVideo={voiceVideo.toggleVideo}
                isPushToTalk={voiceVideo.isPushToTalk}
                setPushToTalk={voiceVideo.setPushToTalk}
                audioLevel={voiceVideo.audioLevel}
                participants={voiceVideo.participants}
                availableDevices={voiceVideo.availableDevices}
                selectedAudioDevice={voiceVideo.selectedAudioDevice}
                selectedVideoDevice={voiceVideo.selectedVideoDevice}
                onSelectAudioDevice={voiceVideo.setSelectedAudioDevice}
                onSelectVideoDevice={voiceVideo.setSelectedVideoDevice}
              />
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="w-80 border-l flex flex-col">
          {/* Session Info */}
          <Card className="m-4 mb-2">
            <CardHeader className="py-3 px-4">
              <CardTitle className="text-sm">Session Info</CardTitle>
            </CardHeader>
            <CardContent className="py-2 px-4 space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground flex items-center gap-1">
                  <Users className="h-4 w-4" />
                  Viewers
                </span>
                <span className="font-medium">
                  {session.current_viewers} / {session.max_viewers}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground flex items-center gap-1">
                  <Clock className="h-4 w-4" />
                  Started
                </span>
                <span className="font-medium">
                  {format(new Date(session.created_at), "p")}
                </span>
              </div>
            </CardContent>
          </Card>

          {/* Participants */}
          <Card className="mx-4 mb-2 flex-shrink-0">
            <CardHeader className="py-3 px-4">
              <CardTitle className="text-sm flex items-center gap-2">
                <Users className="h-4 w-4" />
                Participants ({participants?.length || 0})
              </CardTitle>
            </CardHeader>
            <CardContent className="py-2 px-4 max-h-32 overflow-y-auto">
              <div className="space-y-2">
                {participants?.map((p: LiveParticipant) => (
                  <div
                    key={p.id}
                    className="flex items-center gap-2 text-sm"
                  >
                    <div
                      className={`h-2 w-2 rounded-full ${
                        p.connection_state === "connected"
                          ? "bg-ok"
                          : "bg-neutral"
                      }`}
                    />
                    <span className="truncate">{p.display_name}</span>
                    {p.role === "host" && (
                      <Badge variant="outline" className="text-[10px] px-1 py-0">
                        Host
                      </Badge>
                    )}
                    {p.control_state === "granted" && (
                      <Badge variant="secondary" className="text-[10px] px-1 py-0">
                        Control
                      </Badge>
                    )}
                  </div>
                ))}
                {(!participants || participants.length === 0) && (
                  <p className="text-sm text-muted-foreground">
                    No participants yet
                  </p>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Chat */}
          <Card className="mx-4 mb-4 flex-1 flex flex-col min-h-0">
            <CardHeader className="py-3 px-4 flex-shrink-0">
              <CardTitle className="text-sm flex items-center gap-2">
                <MessageSquare className="h-4 w-4" />
                Chat
              </CardTitle>
            </CardHeader>
            <CardContent className="flex-1 flex flex-col min-h-0 p-0">
              {/* Messages */}
              <div
                ref={chatContainerRef}
                className="flex-1 overflow-y-auto px-4 py-2 space-y-2"
              >
                {chatMessages.length === 0 ? (
                  <p className="text-sm text-muted-foreground text-center py-4">
                    No messages yet
                  </p>
                ) : (
                  chatMessages.map((msg) => (
                    <div key={msg.id} className="text-sm group">
                      <div className="flex items-baseline gap-2">
                        <span className="font-medium">{msg.sender_name}</span>
                        <span className="text-[10px] text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity">
                          {format(new Date(msg.timestamp), "p")}
                        </span>
                      </div>
                      <p className="text-muted-foreground">{msg.content}</p>
                    </div>
                  ))
                )}
              </div>

              {/* Input */}
              <div className="flex items-center gap-2 p-4 border-t">
                <Input
                  placeholder="Type a message..."
                  value={newMessage}
                  onChange={(e) => setNewMessage(e.target.value)}
                  onKeyDown={handleKeyPress}
                  className="flex-1"
                />
                <Button size="icon" onClick={handleSendMessage} aria-label="Send message">
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
