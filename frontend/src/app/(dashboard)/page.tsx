"use client";

import { useEffect, useState, useMemo } from "react";
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
  Eye,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { organizationsApi, projectsApi, executionsApi, liveViewApi } from "@/lib/api";
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
  const { selectedOrganization, selectOrganization } = useOrganizationStore();

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
      value: currentOrganization?.squads_count || 0,
      icon: TrendingUp,
      change: currentOrganization?.name || "No org selected",
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
