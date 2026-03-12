import { useCallback, useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  TouchableOpacity,
  FlatList,
  Alert,
  Dimensions,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";

interface ChatMessage {
  id: string;
  sender_name: string;
  content: string;
  timestamp: string;
}

export default function LiveSessionScreen() {
  const { code } = useLocalSearchParams<{ code: string }>();
  const router = useRouter();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [connected, setConnected] = useState(false);

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
      {/* Video area */}
      <View style={styles.videoArea}>
        <View style={styles.videoPlaceholder}>
          <Text style={styles.videoPlaceholderText}>
            {connected ? "WebRTC Stream" : "Connecting..."}
          </Text>
          <Text style={styles.sessionCodeLabel}>Session: {code}</Text>
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

const { width } = Dimensions.get("window");

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0f172a",
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
