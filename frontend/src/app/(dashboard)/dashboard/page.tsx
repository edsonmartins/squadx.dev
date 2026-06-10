"use client";

import { useEffect, useState, useMemo } from "react";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  FolderKanban,
  ListTodo,
  Users,
  TrendingUp,
  Clock,
  CheckCircle2,
  AlertCircle,
  Play,
  ArrowRight,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { organizationsApi, projectsApi, executionsApi, liveViewApi, ExecutionResponse, PageResponse } from "@/lib/api";
import { useOrganizationStore } from "@/stores/organization-store";
import { useAuthStore } from "@/stores/auth-store";
import { ProjectModal } from "@/components/projects/project-modal";
import { EmptyState } from "@/components/shared/empty-state";
import { cn } from "@/lib/utils";
import {
  EXECUTION_STATUS_TONE,
  TONE_BADGE,
  TONE_TEXT,
  type SemanticTone,
} from "@/lib/design/semantics";

interface MetricsData {
  total_input_tokens?: number;
  total_output_tokens?: number;
  total_cost?: number;
}

const EXECUTION_STATUS_LABEL: Record<string, string> = {
  COMPLETED: "Completed",
  RUNNING: "Running",
  FAILED: "Failed",
  PENDING: "Pending",
  CANCELLED: "Cancelled",
};

export default function DashboardPage() {
  const router = useRouter();
  const [isProjectModalOpen, setIsProjectModalOpen] = useState(false);
  const { selectedOrganization, selectOrganization } = useOrganizationStore();
  const { user } = useAuthStore();

  const { data: orgsData } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  // Keep selectedOrganization in sync with fresh data from query
  const currentOrganization = useMemo(() => {
    if (!orgsData?.content || orgsData.content.length === 0) return selectedOrganization;

    // Find the selected org in fresh data, or default to first
    const freshOrg = selectedOrganization
      ? orgsData.content.find(o => o.id === selectedOrganization.id)
      : orgsData.content[0];

    return freshOrg || orgsData.content[0];
  }, [orgsData, selectedOrganization]);

  // Update store when fresh data is available
  useEffect(() => {
    if (currentOrganization && currentOrganization.id !== selectedOrganization?.id) {
      selectOrganization(currentOrganization);
    } else if (currentOrganization && selectedOrganization &&
               currentOrganization.squads_count !== selectedOrganization.squads_count) {
      selectOrganization(currentOrganization);
    }
  }, [currentOrganization, selectedOrganization, selectOrganization]);

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectsApi.list(),
  });

  const { data: activeSessions } = useQuery({
    queryKey: ["active-sessions", currentOrganization?.id],
    queryFn: () =>
      currentOrganization
        ? liveViewApi.getActiveByOrganization(currentOrganization.id)
        : Promise.resolve([]),
    enabled: !!currentOrganization,
  });

  const { data: metricsData } = useQuery<MetricsData>({
    queryKey: ["metrics", currentOrganization?.id],
    queryFn: async () => {
      if (!currentOrganization) return {};
      const result = await executionsApi.getMetrics(currentOrganization.id);
      return result as MetricsData;
    },
    enabled: !!currentOrganization,
  });

  const organizations = orgsData?.content || [];
  const projects = projectsData?.content || [];

  const stats = [
    {
      title: "Total Projects",
      value: projects.length,
      icon: FolderKanban,
      change: "+2 this week",
      tone: "info" as SemanticTone,
    },
    {
      title: "Active Tasks",
      value: projects.reduce((acc, p) => acc + p.tasks_count, 0),
      icon: ListTodo,
      change: "15 in progress",
      tone: "warn" as SemanticTone,
    },
    {
      title: "Organizations",
      value: organizations.length,
      icon: Users,
      change: `${organizations.reduce((acc, o) => acc + o.members_count, 0)} members`,
      tone: "neutral" as SemanticTone,
    },
    {
      title: "AI Squads",
      value: currentOrganization?.squads_count || 0,
      icon: TrendingUp,
      change: currentOrganization?.name || "No org selected",
      tone: "ok" as SemanticTone,
    },
  ];

  const { data: executionsData, isLoading: executionsLoading } = useQuery<PageResponse<ExecutionResponse>>({
    queryKey: ["recent-executions", currentOrganization?.id],
    queryFn: () =>
      currentOrganization
        ? executionsApi.listByOrganization(currentOrganization.id)
        : Promise.resolve({ content: [], page_number: 0, page_size: 0, total_elements: 0, total_pages: 0, is_first: true, is_last: true }),
    enabled: !!currentOrganization,
  });

  const recentActivity = useMemo(() => {
    const executions = executionsData?.content || [];
    return executions.slice(0, 8).map((exec: ExecutionResponse) => {
      const statusIcons: Record<string, typeof CheckCircle2> = {
        COMPLETED: CheckCircle2,
        RUNNING: Play,
        FAILED: AlertCircle,
        PENDING: Clock,
        CANCELLED: AlertCircle,
      };
      const icon = statusIcons[exec.status] || Clock;
      const tone = EXECUTION_STATUS_TONE[exec.status] || "neutral";
      const time = exec.started_at || exec.created_at;
      const elapsed = Date.now() - new Date(time).getTime();
      const hours = Math.floor(elapsed / 3600000);
      const timeLabel =
        hours < 1
          ? "< 1h ago"
          : hours < 24
          ? `${hours}h ago`
          : `${Math.floor(hours / 24)}d ago`;

      return {
        id: exec.id,
        status: exec.status,
        title: exec.task_title,
        project: exec.agent_name || "Unknown agent",
        time: timeLabel,
        icon,
        tone,
      };
    });
  }, [executionsData]);

  const handleProjectCreated = () => {
    setIsProjectModalOpen(false);
    router.push("/projects");
  };

  const firstName = user?.full_name?.split(" ")[0];
  const liveCount = activeSessions?.length ?? 0;

  return (
    <div className="space-y-5">
      {/* Page Header */}
      <div className="animate-rise flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            {firstName ? `Welcome back, ${firstName}` : "Dashboard"}
          </h1>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {currentOrganization?.squads_count || 0} squads ·{" "}
            {liveCount > 0 ? `${liveCount} live session${liveCount > 1 ? "s" : ""} · ` : ""}
            here&apos;s what&apos;s happening with your AI squads.
          </p>
        </div>
      </div>

      {/* Live strip */}
      {liveCount > 0 && (
        <Card className="animate-rise overflow-hidden" style={{ animationDelay: "60ms" }}>
          <div className="flex items-center gap-3 bg-[radial-gradient(80%_200%_at_100%_0%,hsl(var(--live)/0.07)_0%,transparent_55%)] px-5 py-3.5">
            <span className="live-dot h-[9px] w-[9px] shrink-0" aria-hidden="true" />
            <p className="text-sm">
              <span className="font-semibold">
                {liveCount} live session{liveCount > 1 ? "s" : ""}
              </span>{" "}
              <span className="text-muted-foreground">
                — your squads are executing right now
              </span>
            </p>
            <Button
              variant="ghost"
              size="sm"
              className="ml-auto h-8 gap-1.5 font-heading font-semibold text-primary hover:text-primary"
              onClick={() => router.push("/live")}
            >
              Watch
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>
        </Card>
      )}

      {/* Stats Grid */}
      <div className="grid gap-3.5 md:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat, i) => (
          <Card
            key={stat.title}
            className="animate-rise transition-all duration-200 hover:-translate-y-0.5 hover:shadow-card-hover"
            style={{ animationDelay: `${80 + i * 50}ms` }}
          >
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="font-heading text-xs font-semibold text-muted-foreground">
                {stat.title}
              </CardTitle>
              <span
                className={cn(
                  "flex h-7 w-7 items-center justify-center rounded-md",
                  TONE_BADGE[stat.tone]
                )}
                aria-hidden="true"
              >
                <stat.icon className="h-4 w-4" />
              </span>
            </CardHeader>
            <CardContent>
              <div className="font-mono text-[26px] font-semibold leading-none tracking-tight">
                {stat.value}
              </div>
              <p className="mt-2 text-xs text-muted-foreground">{stat.change}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Cost Metrics */}
      {metricsData && (
        <Card className="animate-rise" style={{ animationDelay: "280ms" }}>
          <CardHeader className="border-b py-3.5">
            <CardTitle className="font-heading text-sm font-bold">Usage &amp; Cost</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="grid divide-y sm:grid-cols-3 sm:divide-x sm:divide-y-0">
              <div className="px-5 py-4">
                <p className="font-heading text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Input tokens
                </p>
                <p className="mt-1 font-mono text-lg font-semibold tracking-tight">
                  {(metricsData.total_input_tokens || 0).toLocaleString()}
                </p>
              </div>
              <div className="px-5 py-4">
                <p className="font-heading text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Output tokens
                </p>
                <p className="mt-1 font-mono text-lg font-semibold tracking-tight">
                  {(metricsData.total_output_tokens || 0).toLocaleString()}
                </p>
              </div>
              <div className="px-5 py-4">
                <p className="font-heading text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Total cost
                </p>
                <p className={cn("mt-1 font-mono text-lg font-semibold tracking-tight", TONE_TEXT.ok)}>
                  ${(metricsData.total_cost || 0).toFixed(2)}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-3.5 lg:grid-cols-2">
        {/* Recent Activity */}
        <Card className="animate-rise" style={{ animationDelay: "340ms" }}>
          <CardHeader className="border-b py-3.5">
            <CardTitle className="font-heading text-sm font-bold">Recent Activity</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {executionsLoading && (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-hidden="true" />
                <span className="ml-2 text-sm text-muted-foreground">Loading activity...</span>
              </div>
            )}
            {!executionsLoading && recentActivity.length === 0 && (
              <EmptyState
                icon={Clock}
                title="No recent activity"
                description="Executions from your squads will show up here."
                actionLabel="Go to tasks"
                onAction={() => router.push("/tasks")}
              />
            )}
            <div>
              {recentActivity.map((activity) => (
                <div
                  key={activity.id}
                  className="flex items-center gap-3 border-b border-border/60 px-5 py-3 transition-colors last:border-b-0 hover:bg-muted/40"
                >
                  <span
                    className={cn(
                      "flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-full",
                      TONE_BADGE[activity.tone]
                    )}
                    aria-hidden="true"
                  >
                    <activity.icon
                      className={cn("h-3.5 w-3.5", activity.status === "RUNNING" && "animate-pulse")}
                    />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13.5px] font-medium">{activity.title}</p>
                    <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      <span className="truncate">{activity.project}</span>
                      <span
                        className={cn(
                          "badge-pill px-2 py-0 text-[10px] font-semibold",
                          TONE_BADGE[activity.tone]
                        )}
                      >
                        {EXECUTION_STATUS_LABEL[activity.status] || activity.status}
                      </span>
                    </p>
                  </div>
                  <span className="shrink-0 font-mono text-[11px] text-muted-foreground">
                    {activity.time}
                  </span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Quick Actions */}
        <Card className="animate-rise" style={{ animationDelay: "400ms" }}>
          <CardHeader className="border-b py-3.5">
            <CardTitle className="font-heading text-sm font-bold">Quick Actions</CardTitle>
          </CardHeader>
          <CardContent className="p-5">
            <div className="grid gap-3 sm:grid-cols-2">
              <button
                onClick={() => setIsProjectModalOpen(true)}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center transition-colors hover:border-primary hover:bg-muted/50"
              >
                <FolderKanban className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
                <span className="text-sm font-medium">Create Project</span>
              </button>
              <button
                onClick={() => router.push("/tasks")}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center transition-colors hover:border-primary hover:bg-muted/50"
              >
                <ListTodo className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
                <span className="text-sm font-medium">View Tasks</span>
              </button>
              <button
                onClick={() => router.push("/squads")}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center transition-colors hover:border-primary hover:bg-muted/50"
              >
                <Users className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
                <span className="text-sm font-medium">Configure Squad</span>
              </button>
              <button
                onClick={() => router.push("/analytics")}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center transition-colors hover:border-primary hover:bg-muted/50"
              >
                <TrendingUp className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
                <span className="text-sm font-medium">View Analytics</span>
              </button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Project Modal */}
      <ProjectModal
        open={isProjectModalOpen}
        onOpenChange={setIsProjectModalOpen}
        onSuccess={handleProjectCreated}
      />
    </div>
  );
}
