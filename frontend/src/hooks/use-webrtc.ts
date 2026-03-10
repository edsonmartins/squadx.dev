"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { signaling, WebRTCSignal } from "@/lib/supabase";

const isDev = process.env.NODE_ENV === "development";
const log = (...args: unknown[]) => isDev && console.log(...args);
const logWarn = (...args: unknown[]) => isDev && console.warn(...args);
const logError = (...args: unknown[]) => console.error(...args);

export type ConnectionState =
  | "disconnected"
  | "connecting"
  | "connected"
  | "failed"
  | "closed"
  | "reconnecting";

interface UseWebRTCOptions {
  sessionId: string;
  onTrack?: (stream: MediaStream) => void;
  onConnectionStateChange?: (state: ConnectionState) => void;
  /** Enable automatic reconnection on disconnect (default: true) */
  autoReconnect?: boolean;
  /** Maximum number of reconnection attempts (default: 5) */
  maxReconnectAttempts?: number;
  /** Base delay in ms for exponential backoff (default: 1000) */
  reconnectBaseDelay?: number;
}

interface UseWebRTCReturn {
  connectionState: ConnectionState;
  remoteStream: MediaStream | null;
  connect: () => Promise<void>;
  disconnect: () => void;
  stats: RTCStatsReport | null;
  /** Number of reconnection attempts made */
  reconnectAttempts: number;
}

/**
 * Get ICE servers configuration for WebRTC.
 * Includes STUN servers and optional TURN server for NAT traversal.
 */
function getIceServers(): RTCIceServer[] {
  const servers: RTCIceServer[] = [
    // Google's free STUN servers
    { urls: ["stun:stun.l.google.com:19302"] },
    { urls: ["stun:stun1.l.google.com:19302"] },
  ];

  // Add TURN server if configured via environment variables
  const turnUrl = process.env.NEXT_PUBLIC_TURN_URL;
  if (turnUrl) {
    const turnConfig: RTCIceServer = { urls: [turnUrl] };
    const turnUsername = process.env.NEXT_PUBLIC_TURN_USERNAME;
    const turnCredential = process.env.NEXT_PUBLIC_TURN_CREDENTIAL;
    if (turnUsername) turnConfig.username = turnUsername;
    if (turnCredential) turnConfig.credential = turnCredential;
    servers.push(turnConfig);
  }

  // Add Cloudflare TURN if token is available
  const cloudflareTurnToken = process.env.NEXT_PUBLIC_CLOUDFLARE_TURN_TOKEN;
  if (cloudflareTurnToken) {
    servers.push({
      urls: [
        "turn:turn.cloudflare.com:3478?transport=udp",
        "turn:turn.cloudflare.com:3478?transport=tcp",
        "turns:turn.cloudflare.com:5349?transport=tcp",
      ],
      username: "cf",
      credential: cloudflareTurnToken,
    });
  }

  return servers;
}

/**
 * Hook for managing WebRTC peer connections as a viewer.
 * Connects to a Python WebRTC bridge streaming VNC content.
 * Supports automatic reconnection with exponential backoff.
 */
export function useWebRTC({
  sessionId,
  onTrack,
  onConnectionStateChange,
  autoReconnect = true,
  maxReconnectAttempts = 5,
  reconnectBaseDelay = 1000,
}: UseWebRTCOptions): UseWebRTCReturn {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>("disconnected");
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [stats, setStats] = useState<RTCStatsReport | null>(null);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const hostIdRef = useRef<string | null>(null);
  const statsIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const isIntentionalDisconnect = useRef(false);
  const wasEverConnected = useRef(false);

  // Refs for breaking circular dependencies
  const scheduleReconnectRef = useRef<() => void>(() => {});
  const performConnectRef = useRef<() => Promise<void>>(async () => {});
  const cleanupConnectionRef = useRef<() => void>(() => {});

  // Update connection state and notify callback
  const updateConnectionState = useCallback(
    (state: ConnectionState) => {
      setConnectionState(state);
      onConnectionStateChange?.(state);
    },
    [onConnectionStateChange]
  );

  // Schedule a reconnection attempt with exponential backoff
  const scheduleReconnect = useCallback(() => {
    if (!autoReconnect || isIntentionalDisconnect.current) {
      return;
    }

    if (reconnectAttempts >= maxReconnectAttempts) {
      log("[WebRTC] Max reconnection attempts reached");
      updateConnectionState("failed");
      return;
    }

    // Clear any pending reconnection
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }

    // Calculate delay with exponential backoff + jitter
    const delay = Math.min(
      reconnectBaseDelay * Math.pow(2, reconnectAttempts) + Math.random() * 1000,
      30000 // Max 30 seconds
    );

    log(
      `[WebRTC] Scheduling reconnection attempt ${reconnectAttempts + 1}/${maxReconnectAttempts} in ${Math.round(delay)}ms`
    );

    updateConnectionState("reconnecting");

    reconnectTimeoutRef.current = setTimeout(() => {
      setReconnectAttempts((prev) => prev + 1);
      // Clean up current connection before reconnecting
      cleanupConnectionRef.current();
      // Attempt to reconnect
      performConnectRef.current();
    }, delay);
  }, [
    autoReconnect,
    reconnectAttempts,
    maxReconnectAttempts,
    reconnectBaseDelay,
    updateConnectionState,
  ]);

  // Update ref when scheduleReconnect changes
  useEffect(() => {
    scheduleReconnectRef.current = scheduleReconnect;
  }, [scheduleReconnect]);

  // Clean up connection resources without leaving signaling channel
  const cleanupConnection = useCallback(() => {
    // Stop stats collection
    if (statsIntervalRef.current) {
      clearInterval(statsIntervalRef.current);
      statsIntervalRef.current = null;
    }

    // Close peer connection
    if (pcRef.current) {
      pcRef.current.close();
      pcRef.current = null;
    }

    // Reset stream (but don't disconnect from signaling yet)
    setRemoteStream(null);
    setStats(null);
    hostIdRef.current = null;
  }, []);

  // Update ref when cleanupConnection changes
  useEffect(() => {
    cleanupConnectionRef.current = cleanupConnection;
  }, [cleanupConnection]);

  // Handle incoming WebRTC signals
  const handleSignal = useCallback(
    async (signal: WebRTCSignal) => {
      const pc = pcRef.current;
      if (!pc) return;

      try {
        switch (signal.type) {
          case "offer":
            // Host is sending us an offer
            hostIdRef.current = signal.sender_id;
            await pc.setRemoteDescription(
              new RTCSessionDescription({ type: "offer", sdp: signal.sdp })
            );

            // Create and send answer
            const answer = await pc.createAnswer();
            await pc.setLocalDescription(answer);
            await signaling.sendAnswer(answer.sdp!, signal.sender_id);
            break;

          case "answer":
            // Host is responding to our offer
            if (pc.signalingState === "have-local-offer") {
              await pc.setRemoteDescription(
                new RTCSessionDescription({ type: "answer", sdp: signal.sdp })
              );
            }
            break;

          case "ice-candidate":
            // ICE candidate from host
            if (signal.candidate && pc.remoteDescription) {
              try {
                await pc.addIceCandidate(
                  new RTCIceCandidate({
                    candidate: signal.candidate.candidate,
                    sdpMid: signal.candidate.sdpMid,
                    sdpMLineIndex: signal.candidate.sdpMLineIndex,
                  })
                );
              } catch (e) {
                logWarn("[WebRTC] Error adding ICE candidate:", e);
              }
            }
            break;
        }
      } catch (error) {
        logError("[WebRTC] Error handling signal:", error);
      }
    },
    []
  );

  // Create peer connection
  const createPeerConnection = useCallback(() => {
    const iceServers = getIceServers();
    log(`[WebRTC] Using ${iceServers.length} ICE servers`);
    const pc = new RTCPeerConnection({ iceServers });

    // Handle ICE candidates
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        signaling.sendIceCandidate(event.candidate, hostIdRef.current || undefined);
      }
    };

    // Handle connection state changes
    pc.onconnectionstatechange = () => {
      const state = pc.connectionState;
      log("[WebRTC] Connection state:", state);

      switch (state) {
        case "connecting":
          updateConnectionState("connecting");
          break;
        case "connected":
          updateConnectionState("connected");
          wasEverConnected.current = true;
          // Reset reconnection counter on successful connection
          setReconnectAttempts(0);
          // Start collecting stats
          startStatsCollection(pc);
          break;
        case "failed":
          // Try to reconnect if we were previously connected
          if (wasEverConnected.current && !isIntentionalDisconnect.current) {
            scheduleReconnectRef.current();
          } else {
            updateConnectionState("failed");
          }
          break;
        case "closed":
        case "disconnected":
          // Try to reconnect if we were previously connected
          if (wasEverConnected.current && !isIntentionalDisconnect.current) {
            scheduleReconnectRef.current();
          } else {
            updateConnectionState("disconnected");
          }
          break;
      }
    };

    // Handle ICE connection state
    pc.oniceconnectionstatechange = () => {
      log("[WebRTC] ICE state:", pc.iceConnectionState);
      if (pc.iceConnectionState === "failed") {
        // Try ICE restart
        pc.restartIce();
      }
    };

    // Handle incoming tracks (video stream)
    pc.ontrack = (event) => {
      log("[WebRTC] Received track:", event.track.kind);
      if (event.streams && event.streams[0]) {
        const stream = event.streams[0];
        setRemoteStream(stream);
        onTrack?.(stream);
      }
    };

    // Add transceivers for receiving video
    pc.addTransceiver("video", { direction: "recvonly" });

    return pc;
  }, [onTrack, updateConnectionState]);

  // Start collecting WebRTC stats
  const startStatsCollection = useCallback((pc: RTCPeerConnection) => {
    if (statsIntervalRef.current) {
      clearInterval(statsIntervalRef.current);
    }

    statsIntervalRef.current = setInterval(async () => {
      if (pc.connectionState === "connected") {
        const report = await pc.getStats();
        setStats(report);
      }
    }, 2000);
  }, []);

  // Internal function to perform connection (used by connect and reconnect)
  const performConnect = useCallback(async () => {
    if (pcRef.current) {
      log("[WebRTC] Peer connection already exists");
      return;
    }

    updateConnectionState("connecting");

    try {
      // Create peer connection
      pcRef.current = createPeerConnection();

      // Set up signaling handler
      signaling.onSignal(handleSignal);

      // Join signaling channel (if not already joined)
      await signaling.joinSession(sessionId);

      // Create offer to initiate connection
      const offer = await pcRef.current.createOffer();
      await pcRef.current.setLocalDescription(offer);

      // Send offer to host (broadcast)
      await signaling.sendOffer(offer.sdp!);

      log("[WebRTC] Sent offer, waiting for answer...");
    } catch (error) {
      logError("[WebRTC] Connection error:", error);
      // On error, try to reconnect if auto-reconnect is enabled
      if (autoReconnect && !isIntentionalDisconnect.current) {
        scheduleReconnectRef.current();
      } else {
        updateConnectionState("failed");
      }
    }
  }, [
    sessionId,
    createPeerConnection,
    handleSignal,
    updateConnectionState,
    autoReconnect,
  ]);

  // Update ref when performConnect changes
  useEffect(() => {
    performConnectRef.current = performConnect;
  }, [performConnect]);

  // Connect to the session (public API)
  const connect = useCallback(async () => {
    if (pcRef.current) {
      log("[WebRTC] Already connected or connecting");
      return;
    }

    // Reset state for new connection
    isIntentionalDisconnect.current = false;
    wasEverConnected.current = false;
    setReconnectAttempts(0);

    await performConnect();
  }, [performConnect]);

  // Disconnect from the session (public API - intentional disconnect)
  const disconnect = useCallback(() => {
    // Mark as intentional disconnect to prevent auto-reconnect
    isIntentionalDisconnect.current = true;

    // Cancel any pending reconnection
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    // Clean up connection resources
    cleanupConnection();

    // Leave signaling channel
    signaling.leaveSession();

    // Reset reconnection state
    setReconnectAttempts(0);
    wasEverConnected.current = false;

    updateConnectionState("disconnected");
  }, [cleanupConnection, updateConnectionState]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      // Cancel any pending reconnection
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      disconnect();
    };
  }, [disconnect]);

  return {
    connectionState,
    remoteStream,
    connect,
    disconnect,
    stats,
    reconnectAttempts,
  };
}

/**
 * Parse WebRTC stats for display.
 */
export function parseWebRTCStats(stats: RTCStatsReport | null): {
  bitrate: number;
  framerate: number;
  resolution: string;
  packetsLost: number;
  jitter: number;
} {
  if (!stats) {
    return {
      bitrate: 0,
      framerate: 0,
      resolution: "",
      packetsLost: 0,
      jitter: 0,
    };
  }

  let bitrate = 0;
  let framerate = 0;
  let resolution = "";
  let packetsLost = 0;
  let jitter = 0;

  stats.forEach((report) => {
    if (report.type === "inbound-rtp" && report.kind === "video") {
      framerate = report.framesPerSecond || 0;
      packetsLost = report.packetsLost || 0;
      jitter = report.jitter || 0;

      if (report.frameWidth && report.frameHeight) {
        resolution = `${report.frameWidth}x${report.frameHeight}`;
      }
    }

    if (report.type === "candidate-pair" && report.state === "succeeded") {
      bitrate = report.availableIncomingBitrate
        ? Math.round(report.availableIncomingBitrate / 1000)
        : 0;
    }
  });

  return { bitrate, framerate, resolution, packetsLost, jitter };
}
