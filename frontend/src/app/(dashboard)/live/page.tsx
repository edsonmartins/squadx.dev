"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Video, Users, Eye, Clock, Play, ExternalLink } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { liveViewApi, organizationsApi } from "@/lib/api";
import { format } from "date-fns";

export default function LiveViewPage() {
  const [joinCode, setJoinCode] = useState("");

  // Fetch organizations
  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const defaultOrgId = organizations?.content?.[0]?.id;

  // Fetch active sessions
  const { data: activeSessions, isLoading } = useQuery({
    queryKey: ["live-sessions", defaultOrgId],
    queryFn: () => liveViewApi.getActiveByOrganization(defaultOrgId!),
    enabled: !!defaultOrgId,
    refetchInterval: 10000, // Refresh every 10 seconds
  });

  const handleJoinSession = () => {
    if (joinCode.length === 6) {
      window.open(`/live/${joinCode}`, "_blank");
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Live View</h1>
          <p className="text-muted-foreground">
            Watch AI agents working on tasks in real-time
          </p>
        </div>
      </div>

      {/* Join Session */}
      <Card>
        <CardHeader>
          <CardTitle>Join a Session</CardTitle>
          <CardDescription>
            Enter a 6-character session code to join a live view
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex gap-3">
            <Input
              placeholder="Enter session code (e.g., XYZ789)"
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
              maxLength={6}
              className="max-w-[200px] font-mono text-lg tracking-wider"
            />
            <Button onClick={handleJoinSession} disabled={joinCode.length !== 6}>
              <Play className="mr-2 h-4 w-4" />
              Join Session
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Active Sessions */}
      <div>
        <h2 className="text-xl font-semibold mb-4">Active Sessions</h2>
        {isLoading ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[...Array(3)].map((_, i) => (
              <Card key={i} className="animate-pulse">
                <CardHeader>
                  <div className="h-5 bg-muted rounded w-3/4" />
                  <div className="h-4 bg-muted rounded w-1/2" />
                </CardHeader>
                <CardContent>
                  <div className="h-20 bg-muted rounded" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : activeSessions?.length === 0 ? (
          <Card className="border-dashed">
            <CardContent className="flex flex-col items-center justify-center py-12">
              <Video className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-medium mb-2">No active sessions</h3>
              <p className="text-muted-foreground text-center max-w-sm">
                There are no live sessions at the moment. Start a task execution to begin a live session.
              </p>
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {activeSessions?.map((session) => (
              <Card key={session.id} className="group hover:shadow-md transition-shadow">
                <CardHeader className="pb-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="flex h-2 w-2 rounded-full bg-red-500 animate-pulse" />
                      <Badge variant="destructive">LIVE</Badge>
                    </div>
                    <span className="text-sm font-mono text-muted-foreground">
                      {session.code}
                    </span>
                  </div>
                  <CardTitle className="text-lg">{session.task_title}</CardTitle>
                  <CardDescription>
                    Hosted by {session.host_user_name}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    <div className="flex items-center gap-4 text-sm text-muted-foreground">
                      <div className="flex items-center gap-1">
                        <Eye className="h-4 w-4" />
                        <span>{session.current_viewers} watching</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <Users className="h-4 w-4" />
                        <span>Max {session.max_viewers}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-1 text-sm text-muted-foreground">
                      <Clock className="h-4 w-4" />
                      <span>Started {format(new Date(session.created_at), "p")}</span>
                    </div>

                    {/* Participants */}
                    {session.participants.length > 0 && (
                      <div className="flex -space-x-2">
                        {session.participants.slice(0, 5).map((p) => (
                          <div
                            key={p.id}
                            className="h-8 w-8 rounded-full bg-primary flex items-center justify-center text-xs text-primary-foreground border-2 border-background"
                            title={p.user_name}
                          >
                            {p.user_name.charAt(0).toUpperCase()}
                          </div>
                        ))}
                        {session.participants.length > 5 && (
                          <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center text-xs border-2 border-background">
                            +{session.participants.length - 5}
                          </div>
                        )}
                      </div>
                    )}

                    <Button className="w-full" asChild>
                      <a
                        href={session.viewer_url}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <Video className="mr-2 h-4 w-4" />
                        Watch Live
                        <ExternalLink className="ml-2 h-3 w-3" />
                      </a>
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
