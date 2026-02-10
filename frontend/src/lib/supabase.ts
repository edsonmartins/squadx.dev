/**
 * Supabase client for real-time signaling and live sessions.
 * Used for WebRTC signaling between Python bridge and browser viewers.
 */

import { createClient, RealtimeChannel } from "@supabase/supabase-js";

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || "";
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || "";

export const supabase = createClient(supabaseUrl, supabaseAnonKey);

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

/**
 * Supabase signaling client for WebRTC.
 * Handles real-time communication for peer connection establishment.
 */
export class SupabaseSignaling {
  private channel: RealtimeChannel | null = null;
  private sessionId: string | null = null;
  private peerId: string;
  private onSignalCallback?: (signal: WebRTCSignal) => void;

  constructor() {
    this.peerId = crypto.randomUUID();
  }

  get currentPeerId(): string {
    return this.peerId;
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
      const signal = payload as WebRTCSignal;

      // Only process signals targeted to us or broadcast signals
      if (!signal.target_id || signal.target_id === this.peerId) {
        this.onSignalCallback?.(signal);
      }
    });

    // Track presence
    this.channel.on("presence", { event: "sync" }, () => {
      const state = this.channel?.presenceState() || {};
      console.log("[Supabase] Presence sync:", Object.keys(state).length, "peers");
    });

    await this.channel.subscribe(async (status) => {
      if (status === "SUBSCRIBED") {
        await this.channel?.track({ joined_at: new Date().toISOString() });
        console.log("[Supabase] Joined session:", sessionId);
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

    await this.channel.send({
      type: "broadcast",
      event: "webrtc-signal",
      payload: signal,
    });
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
          callback(payload.new as LiveSession);
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
