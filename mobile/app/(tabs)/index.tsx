import { View, Text, StyleSheet, ScrollView } from "react-native";
import { useAuth } from "@/lib/auth";

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

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.greeting}>
        Hello, {user?.full_name || "there"}
      </Text>
      <Text style={styles.subGreeting}>Here is your overview</Text>

      <View style={styles.grid}>
        <StatCard label="Active Tasks" value="--" color="#3b82f6" />
        <StatCard label="In Review" value="--" color="#f59e0b" />
        <StatCard label="Completed" value="--" color="#22c55e" />
        <StatCard label="Live Sessions" value="--" color="#ef4444" />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Recent Activity</Text>
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>No recent activity</Text>
        </View>
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
});
