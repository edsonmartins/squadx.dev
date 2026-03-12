import { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
} from "react-native";
import type { TaskResponse, TaskStatus } from "@/lib/api";

const STATUS_FILTERS: { label: string; value: TaskStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "To Do", value: "TODO" },
  { label: "In Progress", value: "IN_PROGRESS" },
  { label: "In Review", value: "IN_REVIEW" },
  { label: "Done", value: "DONE" },
];

const statusColors: Record<string, string> = {
  TODO: "#94a3b8",
  IN_PROGRESS: "#3b82f6",
  IN_REVIEW: "#f59e0b",
  BLOCKED: "#ef4444",
  DONE: "#22c55e",
  CANCELLED: "#64748b",
};

function TaskItem({ task }: { task: TaskResponse }) {
  return (
    <View style={styles.taskItem}>
      <View style={styles.taskHeader}>
        <View
          style={[
            styles.statusDot,
            { backgroundColor: statusColors[task.status] || "#94a3b8" },
          ]}
        />
        <Text style={styles.taskTitle} numberOfLines={1}>
          {task.title}
        </Text>
      </View>
      <View style={styles.taskMeta}>
        <Text style={styles.taskProject}>{task.project_name}</Text>
        <View
          style={[
            styles.statusBadge,
            { backgroundColor: (statusColors[task.status] || "#94a3b8") + "20" },
          ]}
        >
          <Text
            style={[
              styles.statusBadgeText,
              { color: statusColors[task.status] || "#94a3b8" },
            ]}
          >
            {task.status.replace(/_/g, " ")}
          </Text>
        </View>
      </View>
      {task.assigned_agent_name && (
        <Text style={styles.taskAgent}>Agent: {task.assigned_agent_name}</Text>
      )}
    </View>
  );
}

export default function TasksScreen() {
  const [activeFilter, setActiveFilter] = useState<TaskStatus | "ALL">("ALL");
  const [tasks] = useState<TaskResponse[]>([]);
  const [loading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const filteredTasks =
    activeFilter === "ALL"
      ? tasks
      : tasks.filter((t) => t.status === activeFilter);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    // TODO: fetch tasks from API
    setTimeout(() => setRefreshing(false), 1000);
  }, []);

  return (
    <View style={styles.container}>
      {/* Status filters */}
      <FlatList
        horizontal
        data={STATUS_FILTERS}
        keyExtractor={(item) => item.value}
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filterBar}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={[
              styles.filterChip,
              activeFilter === item.value && styles.filterChipActive,
            ]}
            onPress={() => setActiveFilter(item.value)}
          >
            <Text
              style={[
                styles.filterChipText,
                activeFilter === item.value && styles.filterChipTextActive,
              ]}
            >
              {item.label}
            </Text>
          </TouchableOpacity>
        )}
      />

      {/* Task list */}
      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color="#3b82f6" size="large" />
        </View>
      ) : (
        <FlatList
          data={filteredTasks}
          keyExtractor={(item) => item.id.toString()}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onRefresh}
              tintColor="#3b82f6"
            />
          }
          renderItem={({ item }) => <TaskItem task={item} />}
          ListEmptyComponent={
            <View style={styles.emptyState}>
              <Text style={styles.emptyText}>No tasks found</Text>
              <Text style={styles.emptySubtext}>
                Tasks from your projects will appear here
              </Text>
            </View>
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0f172a",
  },
  filterBar: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: "#1e293b",
    marginRight: 8,
  },
  filterChipActive: {
    backgroundColor: "#3b82f6",
  },
  filterChipText: {
    color: "#94a3b8",
    fontSize: 14,
    fontWeight: "600",
  },
  filterChipTextActive: {
    color: "#fff",
  },
  listContent: {
    padding: 16,
    gap: 12,
  },
  taskItem: {
    backgroundColor: "#1e293b",
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  taskHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  taskTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#f8fafc",
    flex: 1,
  },
  taskMeta: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 8,
  },
  taskProject: {
    fontSize: 13,
    color: "#64748b",
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
  },
  statusBadgeText: {
    fontSize: 11,
    fontWeight: "700",
  },
  taskAgent: {
    fontSize: 12,
    color: "#64748b",
    marginTop: 6,
  },
  center: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  emptyState: {
    alignItems: "center",
    paddingVertical: 48,
  },
  emptyText: {
    color: "#64748b",
    fontSize: 16,
    fontWeight: "600",
  },
  emptySubtext: {
    color: "#475569",
    fontSize: 14,
    marginTop: 4,
  },
});
