import { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { useAuth } from "@/lib/auth";
import { liveViewApi, projectsApi, tasksApi, type TaskResponse } from "@/lib/api";

function StatCard({
  label,
  value,
  color,
}: {
  label: string;
  value: string;
  color: string;
}) {
  return (
    <View style={[styles.card, { borderLeftColor: color }]}>
      <Text style={styles.cardValue}>{value}</Text>
      <Text style={styles.cardLabel}>{label}</Text>
    </View>
  );
}

export default function DashboardScreen() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [liveSessions, setLiveSessions] = useState(0);

  const loadOverview = useCallback(async () => {
    try {
      const [projects, sessions] = await Promise.all([
        projectsApi.list(),
        liveViewApi.getActive(),
      ]);

      const pages = await Promise.all(
        projects.content.map((project) => tasksApi.listByProject(project.id))
      );

      const mergedTasks = pages.flatMap((page) => page.content);
      mergedTasks.sort(
        (a, b) =>
          new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
      );

      setTasks(mergedTasks);
      setLiveSessions(sessions.length);
    } catch {
      setTasks([]);
      setLiveSessions(0);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const activeTasks = tasks.filter((task) =>
    ["TODO", "IN_PROGRESS", "IN_REVIEW", "BLOCKED"].includes(task.status)
  ).length;
  const inReview = tasks.filter((task) => task.status === "IN_REVIEW").length;
  const completed = tasks.filter((task) => task.status === "DONE").length;
  const recentTasks = tasks.slice(0, 5);

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.greeting}>
        Hello, {user?.full_name || "there"}
      </Text>
      <Text style={styles.subGreeting}>Here is your overview</Text>

      <View style={styles.grid}>
        <StatCard label="Active Tasks" value={String(activeTasks)} color="#3b82f6" />
        <StatCard label="In Review" value={String(inReview)} color="#f59e0b" />
        <StatCard label="Completed" value={String(completed)} color="#22c55e" />
        <StatCard label="Live Sessions" value={String(liveSessions)} color="#ef4444" />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Recent Activity</Text>
        {recentTasks.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No recent activity</Text>
          </View>
        ) : (
          recentTasks.map((task) => (
            <View key={task.id} style={styles.activityCard}>
              <Text style={styles.activityTitle}>{task.title}</Text>
              <Text style={styles.activityMeta}>
                {task.project_name} · {task.status.replace(/_/g, " ")}
              </Text>
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0f172a",
    padding: 20,
  },
  greeting: {
    fontSize: 28,
    fontWeight: "800",
    color: "#f8fafc",
    marginTop: 8,
  },
  subGreeting: {
    fontSize: 16,
    color: "#94a3b8",
    marginTop: 4,
    marginBottom: 24,
  },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  card: {
    backgroundColor: "#1e293b",
    borderRadius: 12,
    padding: 16,
    width: "47%",
    borderLeftWidth: 4,
  },
  cardValue: {
    fontSize: 28,
    fontWeight: "700",
    color: "#f8fafc",
  },
  cardLabel: {
    fontSize: 13,
    color: "#94a3b8",
    marginTop: 4,
  },
  section: {
    marginTop: 32,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: "#f8fafc",
    marginBottom: 12,
  },
  emptyState: {
    backgroundColor: "#1e293b",
    borderRadius: 12,
    padding: 32,
    alignItems: "center",
  },
  emptyText: {
    color: "#64748b",
    fontSize: 14,
  },
  activityCard: {
    backgroundColor: "#1e293b",
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  activityTitle: {
    color: "#f8fafc",
    fontSize: 15,
    fontWeight: "700",
  },
  activityMeta: {
    color: "#94a3b8",
    fontSize: 13,
    marginTop: 4,
  },
});
