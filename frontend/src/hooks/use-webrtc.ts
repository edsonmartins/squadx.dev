"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { signaling, WebRTCSignal } from "@/lib/supabase";

export type ConnectionState =
  | "disconnected"
  | "connecting"
  | "connected"
  | "failed"
  | "closed";

interface UseWebRTCOptions {
  sessionId: string;
  onTrack?: (stream: MediaStream) => void;
  onConnectionStateChange?: (state: ConnectionState) => void;
}

interface UseWebRTCReturn {
  connectionState: ConnectionState;
  remoteStream: MediaStream | null;
  connect: () => Promise<void>;
  disconnect: () => void;
  stats: RTCStatsReport | null;
}

// ICE servers for NAT traversal
const ICE_SERVERS: RTCIceServer[] = [
  { urls: ["stun:stun.l.google.com:19302"] },
  { urls: ["stun:stun1.l.google.com:19302"] },
];

/**
 * Hook for managing WebRTC peer connections as a viewer.
 * Connects to a Python WebRTC bridge streaming VNC content.
 */
export function useWebRTC({
  sessionId,
  onTrack,
  onConnectionStateChange,
}: UseWebRTCOptions): UseWebRTCReturn {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>("disconnected");
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [stats, setStats] = useState<RTCStatsReport | null>(null);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const hostIdRef = useRef<string | null>(null);
  const statsIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Update connection state and notify callback
  const updateConnectionState = useCallback(
    (state: ConnectionState) => {
      setConnectionState(state);
      onConnectionStateChange?.(state);
    },
    [onConnectionStateChange]
  );

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
                console.warn("[WebRTC] Error adding ICE candidate:", e);
              }
            }
            break;
        }
      } catch (error) {
        console.error("[WebRTC] Error handling signal:", error);
      }
    },
    []
  );

  // Create peer connection
  const createPeerConnection = useCallback(() => {
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    // Handle ICE candidates
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        signaling.sendIceCandidate(event.candidate, hostIdRef.current || undefined);
      }
    };

    // Handle connection state changes
    pc.onconnectionstatechange = () => {
      const state = pc.connectionState;
      console.log("[WebRTC] Connection state:", state);

      switch (state) {
        case "connecting":
          updateConnectionState("connecting");
          break;
        case "connected":
          updateConnectionState("connected");
          // Start collecting stats
          startStatsCollection(pc);
          break;
        case "failed":
          updateConnectionState("failed");
          break;
        case "closed":
        case "disconnected":
          updateConnectionState("disconnected");
          break;
      }
    };

    // Handle ICE connection state
    pc.oniceconnectionstatechange = () => {
      console.log("[WebRTC] ICE state:", pc.iceConnectionState);
      if (pc.iceConnectionState === "failed") {
        // Try ICE restart
        pc.restartIce();
      }
    };

    // Handle incoming tracks (video stream)
    pc.ontrack = (event) => {
      console.log("[WebRTC] Received track:", event.track.kind);
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

  // Connect to the session
  const connect = useCallback(async () => {
    if (pcRef.current) {
      console.log("[WebRTC] Already connected or connecting");
      return;
    }

    updateConnectionState("connecting");

    try {
      // Create peer connection
      pcRef.current = createPeerConnection();

      // Set up signaling handler
      signaling.onSignal(handleSignal);

      // Join signaling channel
      await signaling.joinSession(sessionId);

      // Create offer to initiate connection
      const offer = await pcRef.current.createOffer();
      await pcRef.current.setLocalDescription(offer);

      // Send offer to host (broadcast)
      await signaling.sendOffer(offer.sdp!);

      console.log("[WebRTC] Sent offer, waiting for answer...");
    } catch (error) {
      console.error("[WebRTC] Connection error:", error);
      updateConnectionState("failed");
      disconnect();
    }
  }, [sessionId, createPeerConnection, handleSignal, updateConnectionState]);

  // Disconnect from the session
  const disconnect = useCallback(() => {
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

    // Leave signaling channel
    signaling.leaveSession();

    // Reset state
    setRemoteStream(null);
    setStats(null);
    hostIdRef.current = null;
    updateConnectionState("disconnected");
  }, [updateConnectionState]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      disconnect();
    };
  }, [disconnect]);

  return {
    connectionState,
    remoteStream,
    connect,
    disconnect,
    stats,
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
