import { useCallback, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  TextInput,
  Alert,
  RefreshControl,
} from "react-native";
import { useRouter } from "expo-router";
import type { LiveSessionResponse } from "@/lib/api";

function SessionCard({
  session,
  onPress,
}: {
  session: LiveSessionResponse;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity style={styles.sessionCard} onPress={onPress}>
      <View style={styles.sessionHeader}>
        <Text style={styles.sessionTitle} numberOfLines={1}>
          {session.task_title}
        </Text>
        <View
          style={[
            styles.statusBadge,
            {
              backgroundColor:
                session.status === "ACTIVE" ? "#22c55e20" : "#64748b20",
            },
          ]}
        >
          <Text
            style={[
              styles.statusText,
              {
                color:
                  session.status === "ACTIVE" ? "#22c55e" : "#64748b",
              },
            ]}
          >
            {session.status}
          </Text>
        </View>
      </View>
      <Text style={styles.sessionHost}>
        Host: {session.host_user_name}
      </Text>
      <View style={styles.sessionMeta}>
        <Text style={styles.sessionCode}>Code: {session.code}</Text>
        <Text style={styles.sessionViewers}>
          {session.current_viewers}/{session.max_viewers} viewers
        </Text>
      </View>
    </TouchableOpacity>
  );
}

export default function LiveScreen() {
  const router = useRouter();
  const [joinCode, setJoinCode] = useState("");
  const [sessions] = useState<LiveSessionResponse[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  const handleJoin = useCallback(() => {
    const trimmed = joinCode.trim();
    if (!trimmed) {
      Alert.alert("Error", "Please enter a session code.");
      return;
    }
    router.push(`/live/${trimmed}`);
  }, [joinCode, router]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    // TODO: fetch active sessions from API
    setTimeout(() => setRefreshing(false), 1000);
  }, []);

  return (
    <View style={styles.container}>
      {/* Join by code */}
      <View style={styles.joinSection}>
        <Text style={styles.joinLabel}>Join a session</Text>
        <View style={styles.joinRow}>
          <TextInput
            style={styles.joinInput}
            placeholder="Enter session code"
            placeholderTextColor="#64748b"
            value={joinCode}
            onChangeText={setJoinCode}
            autoCapitalize="characters"
            autoCorrect={false}
          />
          <TouchableOpacity style={styles.joinButton} onPress={handleJoin}>
            <Text style={styles.joinButtonText}>Join</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Active sessions */}
      <Text style={styles.sectionTitle}>Active Sessions</Text>

      <FlatList
        data={sessions}
        keyExtractor={(item) => item.id.toString()}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor="#3b82f6"
          />
        }
        renderItem={({ item }) => (
          <SessionCard
            session={item}
            onPress={() => router.push(`/live/${item.code}`)}
          />
        )}
        ListEmptyComponent={
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No active sessions</Text>
            <Text style={styles.emptySubtext}>
              Join a session with a code or wait for one to start
            </Text>
          </View>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0f172a",
  },
  joinSection: {
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: "#1e293b",
  },
  joinLabel: {
    fontSize: 16,
    fontWeight: "700",
    color: "#f8fafc",
    marginBottom: 12,
  },
  joinRow: {
    flexDirection: "row",
    gap: 12,
  },
  joinInput: {
    flex: 1,
    backgroundColor: "#1e293b",
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    color: "#f8fafc",
    borderWidth: 1,
    borderColor: "#334155",
    fontFamily: "monospace",
  },
  joinButton: {
    backgroundColor: "#3b82f6",
    borderRadius: 12,
    paddingHorizontal: 24,
    justifyContent: "center",
  },
  joinButtonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "700",
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: "#f8fafc",
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 12,
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 20,
  },
  sessionCard: {
    backgroundColor: "#1e293b",
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  sessionHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  sessionTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#f8fafc",
    flex: 1,
    marginRight: 8,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
  },
  statusText: {
    fontSize: 11,
    fontWeight: "700",
  },
  sessionHost: {
    fontSize: 13,
    color: "#94a3b8",
    marginTop: 6,
  },
  sessionMeta: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 8,
  },
  sessionCode: {
    fontSize: 13,
    color: "#64748b",
    fontFamily: "monospace",
  },
  sessionViewers: {
    fontSize: 13,
    color: "#64748b",
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
    textAlign: "center",
  },
});
