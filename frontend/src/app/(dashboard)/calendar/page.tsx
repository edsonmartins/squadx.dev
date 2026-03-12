"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  CalendarDays,
  Clock,
  Plus,
  Users,
  Check,
  X,
  HelpCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import {
  meetingsApi,
  MeetingResponse,
  MeetingStatus,
  RsvpStatus,
  CreateMeetingRequest,
  organizationsApi,
} from "@/lib/api";

const statusColors: Record<MeetingStatus, string> = {
  SCHEDULED: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  IN_PROGRESS: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  COMPLETED: "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200",
  CANCELLED: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
};

const rsvpColors: Record<RsvpStatus, string> = {
  PENDING: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
  ACCEPTED: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  DECLINED: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  TENTATIVE: "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200",
};

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDuration(minutes: number) {
  if (minutes < 60) return `${minutes}m`;
  const hrs = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hrs}h ${mins}m` : `${hrs}h`;
}

export default function CalendarPage() {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [formData, setFormData] = useState({
    title: "",
    description: "",
    scheduled_at: "",
    duration_minutes: 30,
  });
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const defaultOrgId = organizations?.content?.[0]?.id;

  const { data: meetingsData, isLoading } = useQuery({
    queryKey: ["meetings"],
    queryFn: () => meetingsApi.upcoming(),
  });

  const meetings = meetingsData?.content || [];

  const createMutation = useMutation({
    mutationFn: (data: CreateMeetingRequest) => meetingsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["meetings"] });
      toast({
        title: "Meeting scheduled",
        description: "Your meeting has been created successfully.",
      });
      setIsCreateOpen(false);
      setFormData({ title: "", description: "", scheduled_at: "", duration_minutes: 30 });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create meeting. Please try again.",
        variant: "destructive",
      });
    },
  });

  const rsvpMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: RsvpStatus }) =>
      meetingsApi.rsvp(id, { status }),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey: ["meetings"] });
      const previousData = queryClient.getQueryData(["meetings"]);
      queryClient.setQueryData(["meetings"], (old: typeof meetingsData) => {
        if (!old) return old;
        return {
          ...old,
          content: old.content.map((m: MeetingResponse) =>
            m.id === id ? { ...m, my_rsvp: status } : m
          ),
        };
      });
      return { previousData };
    },
    onError: (_err, _vars, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(["meetings"], context.previousData);
      }
      toast({
        title: "Error",
        description: "Failed to update RSVP. Please try again.",
        variant: "destructive",
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["meetings"] });
    },
  });

  const handleCreate = () => {
    if (!formData.title || !formData.scheduled_at || !defaultOrgId) return;
    createMutation.mutate({
      title: formData.title,
      description: formData.description || undefined,
      scheduled_at: new Date(formData.scheduled_at).toISOString(),
      duration_minutes: formData.duration_minutes,
      organization_id: defaultOrgId,
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Calendar</h1>
          <p className="text-muted-foreground">
            Manage your meetings and schedule
          </p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Schedule Meeting
        </Button>
      </div>

      {/* Meetings List */}
      {meetings.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-64 gap-4">
          <CalendarDays className="h-12 w-12 text-muted-foreground" />
          <p className="text-muted-foreground">No upcoming meetings</p>
          <Button variant="outline" onClick={() => setIsCreateOpen(true)}>
            Schedule your first meeting
          </Button>
        </div>
      ) : (
        <div className="grid gap-4">
          {meetings.map((meeting) => (
            <Card key={meeting.id}>
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div className="space-y-1">
                    <CardTitle className="text-base">{meeting.title}</CardTitle>
                    {meeting.description && (
                      <p className="text-sm text-muted-foreground">
                        {meeting.description}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {meeting.my_rsvp && (
                      <Badge
                        variant="secondary"
                        className={rsvpColors[meeting.my_rsvp]}
                      >
                        {meeting.my_rsvp}
                      </Badge>
                    )}
                    <Badge
                      variant="secondary"
                      className={statusColors[meeting.status]}
                    >
                      {meeting.status.replace("_", " ")}
                    </Badge>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <CalendarDays className="h-3.5 w-3.5" />
                      {formatDateTime(meeting.scheduled_at)}
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="h-3.5 w-3.5" />
                      {formatDuration(meeting.duration_minutes)}
                    </span>
                    <span className="flex items-center gap-1">
                      <Users className="h-3.5 w-3.5" />
                      {meeting.attendees_count} attendee{meeting.attendees_count !== 1 ? "s" : ""}
                    </span>
                    <span>Organized by {meeting.organizer_name}</span>
                  </div>
                  {meeting.status === "SCHEDULED" && (
                    <div className="flex items-center gap-1.5">
                      <Button
                        size="sm"
                        variant={meeting.my_rsvp === "ACCEPTED" ? "default" : "outline"}
                        className="h-8"
                        onClick={() =>
                          rsvpMutation.mutate({ id: meeting.id, status: "ACCEPTED" })
                        }
                      >
                        <Check className="mr-1 h-3.5 w-3.5" />
                        Accept
                      </Button>
                      <Button
                        size="sm"
                        variant={meeting.my_rsvp === "TENTATIVE" ? "default" : "outline"}
                        className="h-8"
                        onClick={() =>
                          rsvpMutation.mutate({ id: meeting.id, status: "TENTATIVE" })
                        }
                      >
                        <HelpCircle className="mr-1 h-3.5 w-3.5" />
                        Maybe
                      </Button>
                      <Button
                        size="sm"
                        variant={meeting.my_rsvp === "DECLINED" ? "default" : "outline"}
                        className="h-8 text-red-600 hover:text-red-700"
                        onClick={() =>
                          rsvpMutation.mutate({ id: meeting.id, status: "DECLINED" })
                        }
                      >
                        <X className="mr-1 h-3.5 w-3.5" />
                        Decline
                      </Button>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Schedule Meeting Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Schedule Meeting</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="meeting-title">Title</Label>
              <Input
                id="meeting-title"
                placeholder="Meeting title"
                value={formData.title}
                onChange={(e) =>
                  setFormData((prev) => ({ ...prev, title: e.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="meeting-description">Description</Label>
              <Textarea
                id="meeting-description"
                placeholder="Meeting description (optional)"
                value={formData.description}
                onChange={(e) =>
                  setFormData((prev) => ({ ...prev, description: e.target.value }))
                }
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="meeting-date">Date & Time</Label>
                <Input
                  id="meeting-date"
                  type="datetime-local"
                  value={formData.scheduled_at}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, scheduled_at: e.target.value }))
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="meeting-duration">Duration (minutes)</Label>
                <Input
                  id="meeting-duration"
                  type="number"
                  min={5}
                  max={480}
                  value={formData.duration_minutes}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      duration_minutes: parseInt(e.target.value) || 30,
                    }))
                  }
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleCreate}
              disabled={!formData.title || !formData.scheduled_at || createMutation.isPending}
            >
              {createMutation.isPending ? "Scheduling..." : "Schedule"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
