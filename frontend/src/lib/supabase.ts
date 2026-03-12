/**
 * Supabase client for real-time signaling and live sessions.
 * Used for WebRTC signaling between Python bridge and browser viewers.
 */

import { createClient, RealtimeChannel } from "@supabase/supabase-js";

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

// Lazy singleton — only throws when actually used (safe during SSR/build)
let _supabaseInstance: ReturnType<typeof createClient> | null = null;

function getSupabaseClient() {
  if (!_supabaseInstance) {
    if (!supabaseUrl || !supabaseAnonKey) {
      throw new Error(
        "Missing Supabase environment variables: NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY must be set"
      );
    }
    _supabaseInstance = createClient(supabaseUrl, supabaseAnonKey);
  }
  return _supabaseInstance;
}

export const supabase = new Proxy({} as ReturnType<typeof createClient>, {
  get(_target, prop, receiver) {
    return Reflect.get(getSupabaseClient(), prop, receiver);
  },
});

// Type definitions for live sessions
export interface LiveSession {
  id: string;
  task_id: number;
  host_user_id?: string;
  current_host_id?: string;
  status: "created" | "active" | "paused" | "ended";
  mode: "p2p" | "sfu";
  join_code: string;
  max_controllers: number;
  max_viewers: number;
  settings: Record<string, unknown>;
  host_status: "online" | "reconnecting" | "offline" | "transferred";
  created_at: string;
  started_at?: string;
  ended_at?: string;
}

export interface LiveParticipant {
  id: string;
  session_id: string;
  user_id?: string;
  display_name: string;
  role: "host" | "viewer" | "controller";
  control_state: "view-only" | "requested" | "granted";
  joined_at: string;
  left_at?: string;
  connection_state: "connecting" | "connected" | "disconnected";
}

export interface WebRTCSignal {
  type: "offer" | "answer" | "ice-candidate";
  sender_id: string;
  target_id?: string;
  sdp?: string;
  candidate?: {
    candidate: string;
    sdpMid: string;
    sdpMLineIndex: number;
  };
}

export interface ChatMessage {
  id: string;
  sender_id: string;
  sender_name: string;
  content: string;
  timestamp: string;
}

/**
 * Supabase signaling client for WebRTC.
 * Handles real-time communication for peer connection establishment.
 */
export class SupabaseSignaling {
  private channel: RealtimeChannel | null = null;
  private sessionId: string | null = null;
  private peerId: string;
  private displayName: string = "Anonymous";
  private onSignalCallback?: (signal: WebRTCSignal) => void;
  private onChatCallback?: (message: ChatMessage) => void;

  constructor() {
    this.peerId = crypto.randomUUID();
  }

  get currentPeerId(): string {
    return this.peerId;
  }

  /**
   * Set the display name for chat messages.
   */
  setDisplayName(name: string): void {
    this.displayName = name || "Anonymous";
  }

  /**
   * Join a live session signaling channel.
   */
  async joinSession(sessionId: string): Promise<void> {
    if (this.channel) {
      await this.leaveSession();
    }

    this.sessionId = sessionId;
    this.channel = supabase.channel(`live:${sessionId}`, {
      config: {
        broadcast: { self: false },
        presence: { key: this.peerId },
      },
    });

    // Subscribe to WebRTC signals
    this.channel.on("broadcast", { event: "webrtc-signal" }, ({ payload }) => {
      // Validate required fields before casting
      if (
        payload &&
        typeof payload === "object" &&
        "type" in payload &&
        "sender_id" in payload
      ) {
        const signal = payload as WebRTCSignal;

        // Only process signals targeted to us or broadcast signals
        if (!signal.target_id || signal.target_id === this.peerId) {
          this.onSignalCallback?.(signal);
        }
      }
    });

    // Subscribe to chat messages
    this.channel.on("broadcast", { event: "chat-message" }, ({ payload }) => {
      // Validate required fields before casting
      if (
        payload &&
        typeof payload === "object" &&
        "id" in payload &&
        "sender_id" in payload &&
        "content" in payload
      ) {
        const message = payload as ChatMessage;
        this.onChatCallback?.(message);
      }
    });

    // Track presence
    this.channel.on("presence", { event: "sync" }, () => {
      const state = this.channel?.presenceState() || {};
      // Presence state updated
    });

    await this.channel.subscribe(async (status) => {
      if (status === "SUBSCRIBED") {
        await this.channel?.track({ joined_at: new Date().toISOString() });
        // Successfully joined session
      }
    });
  }

  /**
   * Leave the current session.
   */
  async leaveSession(): Promise<void> {
    if (this.channel) {
      await this.channel.untrack();
      await supabase.removeChannel(this.channel);
      this.channel = null;
      this.sessionId = null;
    }
  }

  /**
   * Set callback for incoming signals.
   */
  onSignal(callback: (signal: WebRTCSignal) => void): void {
    this.onSignalCallback = callback;
  }

  /**
   * Set callback for incoming chat messages.
   */
  onChat(callback: (message: ChatMessage) => void): void {
    this.onChatCallback = callback;
  }

  /**
   * Send a chat message to the session.
   */
  async sendChatMessage(content: string): Promise<void> {
    if (!this.channel) {
      console.error("[Supabase] Not connected to a session");
      return;
    }

    const message: ChatMessage = {
      id: crypto.randomUUID(),
      sender_id: this.peerId,
      sender_name: this.displayName,
      content,
      timestamp: new Date().toISOString(),
    };

    try {
      await this.channel.send({
        type: "broadcast",
        event: "chat-message",
        payload: message,
      });
    } catch (error) {
      console.error("[Supabase] Failed to send chat message:", error);
    }
  }

  /**
   * Send a WebRTC offer to the host.
   */
  async sendOffer(sdp: string, targetId?: string): Promise<void> {
    await this.sendSignal({
      type: "offer",
      sender_id: this.peerId,
      target_id: targetId,
      sdp,
    });
  }

  /**
   * Send a WebRTC answer.
   */
  async sendAnswer(sdp: string, targetId: string): Promise<void> {
    await this.sendSignal({
      type: "answer",
      sender_id: this.peerId,
      target_id: targetId,
      sdp,
    });
  }

  /**
   * Send an ICE candidate.
   */
  async sendIceCandidate(
    candidate: RTCIceCandidate,
    targetId?: string
  ): Promise<void> {
    await this.sendSignal({
      type: "ice-candidate",
      sender_id: this.peerId,
      target_id: targetId,
      candidate: {
        candidate: candidate.candidate,
        sdpMid: candidate.sdpMid || "",
        sdpMLineIndex: candidate.sdpMLineIndex || 0,
      },
    });
  }

  private async sendSignal(signal: WebRTCSignal): Promise<void> {
    if (!this.channel) {
      console.error("[Supabase] Not connected to a session");
      return;
    }

    try {
      await this.channel.send({
        type: "broadcast",
        event: "webrtc-signal",
        payload: signal,
      });
    } catch (error) {
      console.error("[Supabase] Failed to send WebRTC signal:", error);
    }
  }

  /**
   * Get session by join code.
   */
  async getSessionByCode(code: string): Promise<LiveSession | null> {
    const { data, error } = await supabase
      .from("live_sessions")
      .select("*")
      .eq("join_code", code)
      .single();

    if (error) {
      console.error("[Supabase] Error fetching session:", error);
      return null;
    }

    return data as LiveSession;
  }

  /**
   * Get participants for a session.
   */
  async getParticipants(sessionId: string): Promise<LiveParticipant[]> {
    const { data, error } = await supabase
      .from("live_participants")
      .select("*")
      .eq("session_id", sessionId)
      .is("left_at", null);

    if (error) {
      console.error("[Supabase] Error fetching participants:", error);
      return [];
    }

    return data as LiveParticipant[];
  }

  /**
   * Subscribe to session changes.
   */
  subscribeToSession(
    sessionId: string,
    callback: (session: LiveSession) => void
  ): () => void {
    const channel = supabase
      .channel(`session-changes:${sessionId}`)
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "live_sessions",
          filter: `id=eq.${sessionId}`,
        },
        (payload) => {
          if (
            payload.new &&
            typeof payload.new === "object" &&
            "id" in payload.new &&
            "status" in payload.new
          ) {
            callback(payload.new as LiveSession);
          }
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }
}

// Singleton instance
export const signaling = new SupabaseSignaling();
