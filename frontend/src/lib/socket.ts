import { Client, IMessage, StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { api } from "./api";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";

type MessageCallback = (data: unknown) => void;

class StompClient {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private listeners: Map<string, Set<MessageCallback>> = new Map();
  private isConnecting = false;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  connect() {
    if (this.client?.connected || this.isConnecting) {
      return;
    }

    const token = api.getToken();
    if (!token) {
      if (process.env.NODE_ENV === "development") {
        console.warn("No token available for socket connection");
      }
      return;
    }

    this.isConnecting = true;

    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        if (process.env.NODE_ENV === "development") {
          console.log("STOMP:", str);
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        if (process.env.NODE_ENV === "development") console.log("STOMP connected");
        this.isConnecting = false;
        this.reconnectAttempts = 0;

        // Re-subscribe to all channels
        this.resubscribeAll();
      },
      onDisconnect: () => {
        if (process.env.NODE_ENV === "development") console.log("STOMP disconnected");
        this.isConnecting = false;
      },
      onStompError: (frame) => {
        if (process.env.NODE_ENV === "development") {
          console.error("STOMP error:", frame.headers["message"]);
          console.error("Details:", frame.body);
        }
        this.isConnecting = false;
        this.reconnectAttempts++;

        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
          if (process.env.NODE_ENV === "development") {
            console.error("Max reconnection attempts reached");
          }
          this.client?.deactivate();
        }
      },
      onWebSocketError: (event) => {
        if (process.env.NODE_ENV === "development") {
          console.error("WebSocket error:", event);
        }
        this.isConnecting = false;
      },
    });

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.client.deactivate();
      this.client = null;
      this.listeners.clear();
    }
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  private resubscribeAll() {
    // Re-create subscriptions for all stored listeners
    this.listeners.forEach((callbacks, destination) => {
      if (callbacks.size > 0 && !this.subscriptions.has(destination)) {
        this.createSubscription(destination, callbacks);
      }
    });
  }

  private createSubscription(destination: string, callbacks: Set<MessageCallback>) {
    if (!this.client?.connected) return;

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        callbacks.forEach((callback) => callback(data));
      } catch (e) {
        if (process.env.NODE_ENV === "development") {
          console.error("Error parsing message:", e);
        }
        callbacks.forEach((callback) => callback(message.body));
      }
    });

    this.subscriptions.set(destination, subscription);
  }

  subscribe(destination: string, callback: MessageCallback): () => void {
    // Ensure destination starts with /topic or /queue
    const normalizedDestination = destination.startsWith("/")
      ? destination
      : `/topic/${destination}`;

    if (!this.listeners.has(normalizedDestination)) {
      this.listeners.set(normalizedDestination, new Set());
    }
    this.listeners.get(normalizedDestination)!.add(callback);

    // Create subscription if connected and not already subscribed
    if (this.client?.connected && !this.subscriptions.has(normalizedDestination)) {
      this.createSubscription(normalizedDestination, this.listeners.get(normalizedDestination)!);
    }

    // Return unsubscribe function
    return () => this.unsubscribe(normalizedDestination, callback);
  }

  unsubscribe(destination: string, callback: MessageCallback) {
    const normalizedDestination = destination.startsWith("/")
      ? destination
      : `/topic/${destination}`;

    const callbacks = this.listeners.get(normalizedDestination);
    if (callbacks) {
      callbacks.delete(callback);
      if (callbacks.size === 0) {
        this.listeners.delete(normalizedDestination);
        const subscription = this.subscriptions.get(normalizedDestination);
        if (subscription) {
          subscription.unsubscribe();
          this.subscriptions.delete(normalizedDestination);
        }
      }
    }
  }

  send(destination: string, body: unknown) {
    if (this.client?.connected) {
      this.client.publish({
        destination: `/app${destination.startsWith("/") ? destination : "/" + destination}`,
        body: JSON.stringify(body),
      });
    } else {
      if (process.env.NODE_ENV === "development") {
        console.warn("STOMP not connected, cannot send:", destination);
      }
    }
  }

  // Subscribe to project task updates
  subscribeToProject(projectId: number, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/projects/${projectId}/tasks`, callback);
  }

  // Subscribe to task updates
  subscribeToTask(taskId: number, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/tasks/${taskId}`, callback);
  }

  // Subscribe to organization updates
  subscribeToOrganization(organizationId: number, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/organizations/${organizationId}`, callback);
  }

  // Subscribe to live session updates
  subscribeToLiveSession(sessionCode: string, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/live/${sessionCode}`, callback);
  }

  // Subscribe to live session chat
  subscribeToLiveChat(sessionCode: string, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/live/${sessionCode}/chat`, callback);
  }

  // Subscribe to live session participants
  subscribeToLiveParticipants(sessionCode: string, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/live/${sessionCode}/participants`, callback);
  }

  // Subscribe to execution logs
  subscribeToExecutionLogs(executionId: number, callback: MessageCallback): () => void {
    return this.subscribe(`/topic/executions/${executionId}/logs`, callback);
  }

  // Send message to project tasks channel
  sendTaskUpdate(projectId: number, data: unknown) {
    this.send(`/tasks/${projectId}/subscribe`, data);
  }

  // Join live session
  joinLiveSession(sessionCode: string) {
    this.send(`/live/${sessionCode}/join`, {});
  }

  // Leave live session
  leaveLiveSession(sessionCode: string) {
    this.send(`/live/${sessionCode}/leave`, {});
  }

  // Send chat message in live session
  sendChatMessage(sessionCode: string, content: string) {
    this.send(`/live/${sessionCode}/chat`, { content });
  }
}

export const socketClient = new StompClient();

// WebSocket event types
export interface TaskUpdatedEvent {
  type: "task_updated";
  taskId: number;
  status?: string;
  data?: Record<string, unknown>;
}

export interface TaskCreatedEvent {
  type: "task_created";
  task: Record<string, unknown>;
}

export interface TaskDeletedEvent {
  type: "task_deleted";
  taskId: number;
}

export interface ExecutionStartedEvent {
  type: "execution_started";
  executionId: number;
  taskId: number;
}

export interface ExecutionCompletedEvent {
  type: "execution_completed";
  executionId: number;
  taskId: number;
  status: string;
}

export interface ExecutionLogEvent {
  type: "execution_log";
  executionId: number;
  level: string;
  message: string;
  timestamp: number;
}

export interface LiveSessionEvent {
  type:
    | "session_created"
    | "session_started"
    | "session_ended"
    | "participant_joined"
    | "participant_left";
  sessionId?: number;
  code?: string;
  userId?: number;
  userName?: string;
  currentViewers?: number;
}

export interface ChatMessageEvent {
  userId: number;
  userName: string;
  content: string;
  timestamp: number;
}

export interface ParticipantEvent {
  action: "joined" | "left";
  userId: number;
  userName: string;
}

// Events with type property (used in generic handlers)
export type SocketEvent =
  | TaskUpdatedEvent
  | TaskCreatedEvent
  | TaskDeletedEvent
  | ExecutionStartedEvent
  | ExecutionCompletedEvent
  | ExecutionLogEvent
  | LiveSessionEvent;
