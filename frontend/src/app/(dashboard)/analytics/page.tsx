"use client";

import { useState } from "react";
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

// Sample data for charts (replace with real API data)
const executionTrendData = [
  { name: "Jan 1", executions: 12, cost: 2.5 },
  { name: "Jan 8", executions: 18, cost: 3.8 },
  { name: "Jan 15", executions: 25, cost: 5.2 },
  { name: "Jan 22", executions: 32, cost: 6.8 },
  { name: "Jan 29", executions: 28, cost: 5.9 },
  { name: "Feb 5", executions: 38, cost: 8.1 },
];

const agentPerformanceData = [
  { name: "Frontend", tasks: 45, success: 42 },
  { name: "Backend", tasks: 38, success: 35 },
  { name: "Fullstack", tasks: 52, success: 48 },
  { name: "DevOps", tasks: 28, success: 26 },
  { name: "QA", tasks: 35, success: 33 },
];

const costBreakdownData = [
  { name: "GPT-4o", value: 45, color: "#6366f1" },
  { name: "Claude 3.5", value: 35, color: "#8b5cf6" },
  { name: "GPT-4o Mini", value: 15, color: "#a855f7" },
  { name: "Others", value: 5, color: "#d946ef" },
];

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
  const { data: metrics } = useQuery({
    queryKey: ["execution-metrics", defaultOrgId],
    queryFn: () => executionsApi.getMetrics(defaultOrgId!),
    enabled: !!defaultOrgId,
  });

  // Calculate stats (using sample data for now)
  const stats = {
    totalExecutions: 127,
    executionsTrend: 23,
    totalCost: 23.45,
    costTrend: -12,
    totalTokens: 1250000,
    tokensTrend: 18,
    avgDuration: 15.3,
    durationTrend: -8,
    successRate: 94,
    successTrend: 2,
    activeAgents: 8,
  };

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

      {/* Stats Grid */}
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
                ${(stats.totalCost / stats.totalExecutions).toFixed(2)}
              </span>
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Per execution average
            </p>
          </CardContent>
        </Card>
      </div>
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
