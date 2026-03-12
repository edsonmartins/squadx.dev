"use client";

import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart3,
  TrendingUp,
  TrendingDown,
  DollarSign,
  Zap,
  Clock,
  CheckCircle,
  Users,
  Loader2,
} from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { executionsApi, organizationsApi } from "@/lib/api";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from "recharts";

const PIE_COLORS = ["#6366f1", "#8b5cf6", "#a855f7", "#d946ef", "#ec4899", "#f43f5e"];

type Period = "7d" | "30d" | "90d" | "year";

export default function AnalyticsPage() {
  const [period, setPeriod] = useState<Period>("30d");

  // Fetch organizations
  const { data: organizations } = useQuery({
    queryKey: ["organizations"],
    queryFn: () => organizationsApi.list(),
  });

  const defaultOrgId = organizations?.content?.[0]?.id;

  // Fetch metrics
  const { data: metrics, isLoading: metricsLoading } = useQuery({
    queryKey: ["execution-metrics", defaultOrgId],
    queryFn: () => executionsApi.getMetrics(defaultOrgId!),
    enabled: !!defaultOrgId,
  });

  // Fetch executions for charts
  const { data: executions, isLoading: executionsLoading } = useQuery({
    queryKey: ["executions", defaultOrgId],
    queryFn: () => executionsApi.listByOrganization(defaultOrgId!),
    enabled: !!defaultOrgId,
  });

  const isLoading = metricsLoading || executionsLoading;
  const allExecutions = executions?.content ?? [];

  // Compute period boundaries
  const periodDays = useMemo(() => {
    switch (period) {
      case "7d": return 7;
      case "30d": return 30;
      case "90d": return 90;
      case "year": return 365;
    }
  }, [period]);

  const fromDate = useMemo(() => new Date(Date.now() - periodDays * 86400000), [periodDays]);
  const prevFromDate = useMemo(() => new Date(fromDate.getTime() - periodDays * 86400000), [fromDate, periodDays]);

  // Filter executions by current period
  const executionList = useMemo(
    () => allExecutions.filter((e) => new Date(e.started_at ?? e.created_at) >= fromDate),
    [allExecutions, fromDate]
  );

  // Filter executions by previous period (for trend calculation)
  const prevExecutionList = useMemo(
    () => allExecutions.filter((e) => {
      const d = new Date(e.started_at ?? e.created_at);
      return d >= prevFromDate && d < fromDate;
    }),
    [allExecutions, prevFromDate, fromDate]
  );

  // Helper: compute trend percentage
  const calcTrend = (current: number, previous: number): number =>
    previous > 0 ? Math.round(((current - previous) / previous) * 100) : 0;

  // Compute stats from metrics API response
  const stats = useMemo(() => {
    const totalExecutions = executionList.length;
    const totalCost = executionList.reduce((s, e) => s + (e.cost ?? 0), 0);
    const totalTokens = executionList.reduce((s, e) => s + (e.tokens_used ?? 0), 0);
    const completed = executionList.filter((e) => e.status === "COMPLETED").length;
    const failed = executionList.filter((e) => e.status === "FAILED").length;
    const successRate = totalExecutions > 0 ? Math.round((completed / (completed + failed || 1)) * 100) : 0;
    const durations = executionList.filter((e) => e.duration_seconds != null).map((e) => e.duration_seconds!);
    const avgDuration = durations.length > 0 ? durations.reduce((s, d) => s + d, 0) / durations.length / 60 : 0;
    const uniqueAgents = new Set(executionList.map((e) => e.agent_name).filter(Boolean));

    // Previous period stats
    const prevTotal = prevExecutionList.length;
    const prevCost = prevExecutionList.reduce((s, e) => s + (e.cost ?? 0), 0);
    const prevTokens = prevExecutionList.reduce((s, e) => s + (e.tokens_used ?? 0), 0);
    const prevCompleted = prevExecutionList.filter((e) => e.status === "COMPLETED").length;
    const prevFailed = prevExecutionList.filter((e) => e.status === "FAILED").length;
    const prevSuccessRate = prevTotal > 0 ? Math.round((prevCompleted / (prevCompleted + prevFailed || 1)) * 100) : 0;
    const prevDurations = prevExecutionList.filter((e) => e.duration_seconds != null).map((e) => e.duration_seconds!);
    const prevAvgDuration = prevDurations.length > 0 ? prevDurations.reduce((s, d) => s + d, 0) / prevDurations.length / 60 : 0;

    return {
      totalExecutions,
      executionsTrend: calcTrend(totalExecutions, prevTotal),
      totalCost,
      costTrend: calcTrend(totalCost, prevCost),
      totalTokens,
      tokensTrend: calcTrend(totalTokens, prevTokens),
      avgDuration,
      durationTrend: calcTrend(avgDuration, prevAvgDuration),
      successRate,
      successTrend: calcTrend(successRate, prevSuccessRate),
      activeAgents: uniqueAgents.size,
    };
  }, [executionList, prevExecutionList]);

  // Group executions by week for trend chart
  const executionTrendData = useMemo(() => {
    if (executionList.length === 0) return [];
    const weeks: Record<string, { executions: number; cost: number }> = {};
    executionList.forEach((e) => {
      const date = new Date(e.started_at ?? e.created_at);
      const weekStart = new Date(date);
      weekStart.setDate(date.getDate() - date.getDay());
      const key = `${weekStart.getMonth() + 1}/${weekStart.getDate()}`;
      if (!weeks[key]) weeks[key] = { executions: 0, cost: 0 };
      weeks[key].executions += 1;
      weeks[key].cost += e.cost ?? 0;
    });
    return Object.entries(weeks)
      .map(([name, data]) => ({ name, executions: data.executions, cost: Math.round(data.cost * 100) / 100 }))
      .slice(-8);
  }, [executionList]);

  // Group executions by agent for performance chart
  const agentPerformanceData = useMemo(() => {
    if (executionList.length === 0) return [];
    const agents: Record<string, { tasks: number; success: number }> = {};
    executionList.forEach((e) => {
      const name = e.agent_name ?? "Unknown";
      if (!agents[name]) agents[name] = { tasks: 0, success: 0 };
      agents[name].tasks += 1;
      if (e.status === "COMPLETED") agents[name].success += 1;
    });
    return Object.entries(agents).map(([name, data]) => ({ name, ...data }));
  }, [executionList]);

  // Cost breakdown by agent
  const costBreakdownData = useMemo(() => {
    if (executionList.length === 0) return [];
    const costs: Record<string, number> = {};
    executionList.forEach((e) => {
      const name = e.agent_name ?? "Unknown";
      costs[name] = (costs[name] ?? 0) + (e.cost ?? 0);
    });
    const total = Object.values(costs).reduce((s, v) => s + v, 0);
    if (total === 0) return [];
    return Object.entries(costs)
      .sort((a, b) => b[1] - a[1])
      .map(([name, value], i) => ({
        name,
        value: Math.round((value / total) * 100),
        color: PIE_COLORS[i % PIE_COLORS.length],
      }));
  }, [executionList]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Analytics</h1>
          <p className="text-muted-foreground">
            Track your AI squad performance and costs
          </p>
        </div>
        <Select value={period} onValueChange={(v) => setPeriod(v as Period)}>
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="Select period" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="7d">Last 7 days</SelectItem>
            <SelectItem value="30d">Last 30 days</SelectItem>
            <SelectItem value="90d">Last 90 days</SelectItem>
            <SelectItem value="year">This year</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          <span className="ml-2 text-muted-foreground">Loading analytics...</span>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && executionList.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <BarChart3 className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-medium">No data yet</h3>
            <p className="text-sm text-muted-foreground">
              Run some executions to see analytics here.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Stats Grid */}
      {!isLoading && executionList.length > 0 && (<>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Total Executions"
          value={stats.totalExecutions.toString()}
          trend={stats.executionsTrend}
          icon={<Zap className="h-4 w-4" />}
        />
        <StatCard
          title="Total Cost"
          value={`$${stats.totalCost.toFixed(2)}`}
          trend={stats.costTrend}
          icon={<DollarSign className="h-4 w-4" />}
          trendInverse
        />
        <StatCard
          title="Total Tokens"
          value={formatNumber(stats.totalTokens)}
          trend={stats.tokensTrend}
          icon={<BarChart3 className="h-4 w-4" />}
        />
        <StatCard
          title="Avg Duration"
          value={`${stats.avgDuration.toFixed(1)} min`}
          trend={stats.durationTrend}
          icon={<Clock className="h-4 w-4" />}
          trendInverse
        />
      </div>

      {/* Charts Row */}
      <div className="grid gap-4 md:grid-cols-2">
        {/* Execution Trends */}
        <Card>
          <CardHeader>
            <CardTitle>Execution Trends</CardTitle>
            <CardDescription>Executions and cost over time</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={executionTrendData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="name" className="text-xs" />
                  <YAxis yAxisId="left" className="text-xs" />
                  <YAxis yAxisId="right" orientation="right" className="text-xs" />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "hsl(var(--card))",
                      border: "1px solid hsl(var(--border))",
                    }}
                  />
                  <Line
                    yAxisId="left"
                    type="monotone"
                    dataKey="executions"
                    stroke="hsl(var(--primary))"
                    strokeWidth={2}
                    dot={{ fill: "hsl(var(--primary))" }}
                  />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="cost"
                    stroke="hsl(var(--destructive))"
                    strokeWidth={2}
                    dot={{ fill: "hsl(var(--destructive))" }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Cost Breakdown */}
        <Card>
          <CardHeader>
            <CardTitle>Cost by Model</CardTitle>
            <CardDescription>Distribution of costs across LLM models</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-[300px] flex items-center">
              <ResponsiveContainer width="50%" height="100%">
                <PieChart>
                  <Pie
                    data={costBreakdownData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={2}
                    dataKey="value"
                  >
                    {costBreakdownData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "hsl(var(--card))",
                      border: "1px solid hsl(var(--border))",
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
              <div className="space-y-2">
                {costBreakdownData.map((item) => (
                  <div key={item.name} className="flex items-center gap-2">
                    <div
                      className="h-3 w-3 rounded-full"
                      style={{ backgroundColor: item.color }}
                    />
                    <span className="text-sm">{item.name}</span>
                    <span className="text-sm text-muted-foreground">
                      {item.value}%
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Agent Performance */}
      <Card>
        <CardHeader>
          <CardTitle>Agent Performance</CardTitle>
          <CardDescription>Tasks completed by agent type</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={agentPerformanceData} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis type="number" className="text-xs" />
                <YAxis dataKey="name" type="category" className="text-xs" width={80} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "hsl(var(--card))",
                    border: "1px solid hsl(var(--border))",
                  }}
                />
                <Bar dataKey="tasks" fill="hsl(var(--muted))" name="Total Tasks" />
                <Bar dataKey="success" fill="hsl(var(--primary))" name="Successful" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Additional Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Success Rate</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <CheckCircle className="h-5 w-5 text-green-500" />
              <span className="text-3xl font-bold">{stats.successRate}%</span>
              <TrendIndicator value={stats.successTrend} />
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Tasks completed successfully
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Active Agents</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <Users className="h-5 w-5 text-blue-500" />
              <span className="text-3xl font-bold">{stats.activeAgents}</span>
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Across all squads
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Avg Cost per Task</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <DollarSign className="h-5 w-5 text-green-500" />
              <span className="text-3xl font-bold">
                ${stats.totalExecutions > 0 ? (stats.totalCost / stats.totalExecutions).toFixed(2) : "0.00"}
              </span>
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Per execution average
            </p>
          </CardContent>
        </Card>
      </div>
      </>)}
    </div>
  );
}

interface StatCardProps {
  title: string;
  value: string;
  trend: number;
  icon: React.ReactNode;
  trendInverse?: boolean;
}

function StatCard({ title, value, trend, icon, trendInverse }: StatCardProps) {
  const isPositive = trendInverse ? trend < 0 : trend > 0;

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        <div className="flex items-center text-xs text-muted-foreground">
          <TrendIndicator value={trend} inverse={trendInverse} />
          <span className="ml-1">vs last period</span>
        </div>
      </CardContent>
    </Card>
  );
}

function TrendIndicator({ value, inverse }: { value: number; inverse?: boolean }) {
  const isPositive = inverse ? value < 0 : value > 0;
  const Icon = value > 0 ? TrendingUp : TrendingDown;

  return (
    <span
      className={`flex items-center ${
        isPositive ? "text-green-500" : "text-red-500"
      }`}
    >
      <Icon className="h-3 w-3 mr-0.5" />
      {Math.abs(value)}%
    </span>
  );
}

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`;
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`;
  }
  return num.toString();
}
