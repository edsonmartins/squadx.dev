"use client";

import { useEffect, useState } from "react";
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
  Eye,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { organizationsApi, projectsApi, executionsApi, liveViewApi, changesApi, WhereWeAreResponse } from "@/lib/api";
import { useOrganizationStore } from "@/stores/organization-store";
import { ProjectModal } from "@/components/projects/project-modal";

interface MetricsData {
  total_input_tokens?: number;
  total_output_tokens?: number;
  total_cost?: number;
}

export default function DashboardPage() {
  const router = useRouter();
  const [isProjectModalOpen, setIsProjectModalOpen] = useState(false);
  const { selectedOrganization, fetchOrganizations } = useOrganizationStore();

  useEffect(() => {
    fetchOrganizations();
  }, [fetchOrganizations]);

  const { data: orgsData } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectsApi.list(),
  });

  const organizations = orgsData?.content || [];
  const projects = projectsData?.content || [];

  const { data: progressByProject } = useQuery({
    queryKey: ["change-progress", projects.map((project) => project.id)],
    queryFn: async () => {
      const entries = await Promise.all(
        projects.slice(0, 8).map(async (project) => [
          project.id,
          await changesApi.whereWeAre(project.id),
        ] as const),
      );
      return Object.fromEntries(entries) as Record<number, WhereWeAreResponse>;
    },
    enabled: projects.length > 0,
  });

  const { data: activeSessions } = useQuery({
    queryKey: ["active-sessions", selectedOrganization?.id],
    queryFn: () =>
      selectedOrganization
        ? liveViewApi.getActiveByOrganization(selectedOrganization.id)
        : Promise.resolve([]),
    enabled: !!selectedOrganization,
  });

  const { data: metricsData } = useQuery<MetricsData>({
    queryKey: ["metrics", selectedOrganization?.id],
    queryFn: async () => {
      if (!selectedOrganization) return {};
      const result = await executionsApi.getMetrics(selectedOrganization.id);
      return result as MetricsData;
    },
    enabled: !!selectedOrganization,
  });

  const stats = [
    {
      title: "Total Projects",
      value: projects.length,
      icon: FolderKanban,
      change: "+2 this week",
      changeType: "positive" as const,
    },
    {
      title: "Active Tasks",
      value: projects.reduce((acc, p) => acc + p.tasks_count, 0),
      icon: ListTodo,
      change: "15 in progress",
      changeType: "neutral" as const,
    },
    {
      title: "Organizations",
      value: organizations.length,
      icon: Users,
      change: `${organizations.reduce((acc, o) => acc + o.members_count, 0)} members`,
      changeType: "neutral" as const,
    },
    {
      title: "AI Squads",
      value: organizations.reduce((acc, o) => acc + o.squads_count, 0),
      icon: TrendingUp,
      change: "All active",
      changeType: "positive" as const,
    },
  ];

  const recentActivity = [
    {
      id: 1,
      type: "completed",
      title: "Implement user authentication",
      project: "squadx-frontend",
      time: "2 hours ago",
      icon: CheckCircle2,
      iconColor: "text-green-500",
    },
    {
      id: 2,
      type: "in_progress",
      title: "Fix API response handling",
      project: "squadx-backend",
      time: "4 hours ago",
      icon: Clock,
      iconColor: "text-blue-500",
    },
    {
      id: 3,
      type: "blocked",
      title: "Database migration script",
      project: "squadx-backend",
      time: "1 day ago",
      icon: AlertCircle,
      iconColor: "text-yellow-500",
    },
  ];

  const handleProjectCreated = () => {
    setIsProjectModalOpen(false);
    router.push("/projects");
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Dashboard</h1>
          <p className="text-muted-foreground">
            Welcome back! Here&apos;s what&apos;s happening with your AI squads.
          </p>
        </div>
        {activeSessions && activeSessions.length > 0 && (
          <Button
            variant="outline"
            onClick={() => router.push("/live")}
            className="gap-2"
          >
            <Eye className="h-4 w-4" />
            {activeSessions.length} Live Session{activeSessions.length > 1 ? "s" : ""}
          </Button>
        )}
      </div>

      {/* Stats Grid */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <Card key={stat.title}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {stat.title}
              </CardTitle>
              <stat.icon className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stat.value}</div>
              <p
                className={`text-xs ${
                  stat.changeType === "positive"
                    ? "text-green-500"
                    : "text-muted-foreground"
                }`}
              >
                {stat.change}
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Cost Metrics */}
      {metricsData && (
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Total Input Tokens
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {(metricsData.total_input_tokens || 0).toLocaleString()}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Total Output Tokens
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {(metricsData.total_output_tokens || 0).toLocaleString()}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Total Cost
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">
                ${(metricsData.total_cost || 0).toFixed(2)}
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Onde estamos</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {projects.slice(0, 8).map((project) => {
              const progress = progressByProject?.[project.id];
              const percent = Math.round((progress?.progress || 0) * 100);
              return (
                <div key={project.id} className="space-y-1">
                  <div className="flex items-center justify-between text-sm">
                    <span className="truncate font-medium">{project.name}</span>
                    <span className="text-muted-foreground">{progress ? `${progress.concluidas}/${progress.total}` : "—"}</span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${percent}%` }} />
                  </div>
                </div>
              );
            })}
            {projects.length === 0 && <p className="text-sm text-muted-foreground">Nenhum projeto disponível.</p>}
          </CardContent>
        </Card>

        {/* Recent Activity */}
        <Card>
          <CardHeader>
            <CardTitle>Recent Activity</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {recentActivity.map((activity) => (
                <div
                  key={activity.id}
                  className="flex items-start gap-4 rounded-lg p-3 hover:bg-muted/50 transition-colors cursor-pointer"
                >
                  <activity.icon className={`h-5 w-5 ${activity.iconColor}`} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">
                      {activity.title}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {activity.project} • {activity.time}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Quick Actions */}
        <Card>
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid gap-3 sm:grid-cols-2">
              <button
                onClick={() => setIsProjectModalOpen(true)}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center hover:border-primary hover:bg-muted/50 transition-colors"
              >
                <FolderKanban className="h-8 w-8 text-muted-foreground" />
                <span className="text-sm font-medium">Create Project</span>
              </button>
              <button
                onClick={() => router.push("/tasks")}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center hover:border-primary hover:bg-muted/50 transition-colors"
              >
                <ListTodo className="h-8 w-8 text-muted-foreground" />
                <span className="text-sm font-medium">View Tasks</span>
              </button>
              <button
                onClick={() => router.push("/squads")}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center hover:border-primary hover:bg-muted/50 transition-colors"
              >
                <Users className="h-8 w-8 text-muted-foreground" />
                <span className="text-sm font-medium">Configure Squad</span>
              </button>
              <button
                onClick={() => router.push("/analytics")}
                className="flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 text-center hover:border-primary hover:bg-muted/50 transition-colors"
              >
                <TrendingUp className="h-8 w-8 text-muted-foreground" />
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
