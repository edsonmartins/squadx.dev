import { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
} from "react-native";
import { useLocalSearchParams } from "expo-router";
import { liveViewApi, type LiveSessionResponse } from "@/lib/api";

interface ChatMessage {
  id: string;
  sender_name: string;
  content: string;
  timestamp: string;
}

export default function LiveSessionScreen() {
  const { code } = useLocalSearchParams<{ code: string }>();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [connected, setConnected] = useState(false);
  const [session, setSession] = useState<LiveSessionResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const joinSession = async () => {
      if (!code) return;

      try {
        const sessionResponse = await liveViewApi.joinSession(code);
        setSession(sessionResponse);
        setConnected(true);
      } catch {
        try {
          const sessionResponse = await liveViewApi.getByCode(code);
          setSession(sessionResponse);
          setConnected(true);
        } catch {
          setConnected(false);
        }
      } finally {
        setLoading(false);
      }
    };

    void joinSession();
  }, [code]);

  const handleSend = useCallback(() => {
    if (!newMessage.trim()) return;

    const msg: ChatMessage = {
      id: Date.now().toString(),
      sender_name: "You",
      content: newMessage.trim(),
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, msg]);
    setNewMessage("");
  }, [newMessage]);

  return (
    <View style={styles.container}>
      {loading ? (
        <View style={styles.loadingState}>
          <ActivityIndicator size="large" color="#3b82f6" />
          <Text style={styles.loadingText}>Joining live session...</Text>
        </View>
      ) : null}

      {/* Video area */}
      <View style={styles.videoArea}>
        <View style={styles.videoPlaceholder}>
          <Text style={styles.videoPlaceholderText}>
            {connected ? "Live session connected" : "Unable to connect"}
          </Text>
          <Text style={styles.sessionCodeLabel}>Session: {code}</Text>
          {session ? (
            <>
              <Text style={styles.sessionMeta}>{session.task_title}</Text>
              <Text style={styles.sessionMeta}>
                Host: {session.host_user_name}
              </Text>
            </>
          ) : null}
        </View>
      </View>

      {/* Connection status */}
      <View style={styles.statusBar}>
        <View
          style={[
            styles.statusDot,
            { backgroundColor: connected ? "#22c55e" : "#f59e0b" },
          ]}
        />
        <Text style={styles.statusText}>
          {connected ? "Connected" : "Connecting"}
        </Text>
      </View>

      {/* Chat */}
      <View style={styles.chatSection}>
        <Text style={styles.chatTitle}>Chat</Text>
        <FlatList
          data={messages}
          keyExtractor={(item) => item.id}
          style={styles.chatList}
          renderItem={({ item }) => (
            <View style={styles.chatMessage}>
              <Text style={styles.chatSender}>{item.sender_name}</Text>
              <Text style={styles.chatContent}>{item.content}</Text>
            </View>
          )}
          ListEmptyComponent={
            <Text style={styles.chatEmpty}>No messages yet</Text>
          }
        />

        <View style={styles.chatInput}>
          <TextInput
            style={styles.chatTextInput}
            placeholder="Type a message..."
            placeholderTextColor="#64748b"
            value={newMessage}
            onChangeText={setNewMessage}
            onSubmitEditing={handleSend}
            returnKeyType="send"
          />
          <TouchableOpacity style={styles.sendButton} onPress={handleSend}>
            <Text style={styles.sendButtonText}>Send</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0f172a",
  },
  loadingState: {
    position: "absolute",
    top: 24,
    left: 24,
    right: 24,
    zIndex: 10,
    alignItems: "center",
    gap: 8,
  },
  loadingText: {
    color: "#94a3b8",
    fontSize: 14,
  },
  videoArea: {
    width: "100%",
    aspectRatio: 16 / 9,
    backgroundColor: "#000",
  },
  videoPlaceholder: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  videoPlaceholderText: {
    color: "#64748b",
    fontSize: 18,
    fontWeight: "600",
  },
  sessionCodeLabel: {
    color: "#475569",
    fontSize: 14,
    marginTop: 8,
    fontFamily: "monospace",
  },
  sessionMeta: {
    color: "#94a3b8",
    fontSize: 14,
    marginTop: 6,
  },
  statusBar: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#1e293b",
    gap: 8,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusText: {
    color: "#94a3b8",
    fontSize: 14,
  },
  chatSection: {
    flex: 1,
    padding: 16,
  },
  chatTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#f8fafc",
    marginBottom: 12,
  },
  chatList: {
    flex: 1,
  },
  chatMessage: {
    marginBottom: 12,
  },
  chatSender: {
    fontSize: 13,
    fontWeight: "700",
    color: "#f8fafc",
  },
  chatContent: {
    fontSize: 14,
    color: "#94a3b8",
    marginTop: 2,
  },
  chatEmpty: {
    color: "#475569",
    fontSize: 14,
    textAlign: "center",
    paddingVertical: 24,
  },
  chatInput: {
    flexDirection: "row",
    gap: 8,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: "#1e293b",
  },
  chatTextInput: {
    flex: 1,
    backgroundColor: "#1e293b",
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 15,
    color: "#f8fafc",
  },
  sendButton: {
    backgroundColor: "#3b82f6",
    borderRadius: 12,
    paddingHorizontal: 20,
    justifyContent: "center",
  },
  sendButtonText: {
    color: "#fff",
    fontWeight: "700",
    fontSize: 14,
  },
});
