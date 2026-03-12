"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { supabase } from "@/lib/supabase";
import type { RealtimeChannel } from "@supabase/supabase-js";

const isDev = process.env.NODE_ENV === "development";
const log = (...args: unknown[]) => isDev && console.log("[VoiceVideo]", ...args);
const logError = (...args: unknown[]) => console.error("[VoiceVideo]", ...args);

// ---------- Types ----------

export interface VoiceParticipant {
  peerId: string;
  userId: string;
  userName: string;
  isAudioEnabled: boolean;
  isVideoEnabled: boolean;
  isSpeaking: boolean;
}

interface VoiceSignal {
  type: "offer" | "answer" | "ice-candidate";
  sender_id: string;
  sender_user_id: string;
  sender_user_name: string;
  target_id: string;
  sdp?: string;
  candidate?: {
    candidate: string;
    sdpMid: string;
    sdpMLineIndex: number;
  };
}

interface VoicePresence {
  peerId: string;
  userId: string;
  userName: string;
  isAudioEnabled: boolean;
  isVideoEnabled: boolean;
}

export interface UseVoiceVideoOptions {
  sessionCode: string;
  userId: string;
  userName: string;
  enabled: boolean;
}

export interface UseVoiceVideoReturn {
  isAudioEnabled: boolean;
  isVideoEnabled: boolean;
  toggleAudio: () => void;
  toggleVideo: () => void;
  localStream: MediaStream | null;
  remoteStreams: Map<string, MediaStream>;
  participants: VoiceParticipant[];
  isPushToTalk: boolean;
  setPushToTalk: (enabled: boolean) => void;
  startPushToTalk: () => void;
  stopPushToTalk: () => void;
  audioLevel: number;
  availableDevices: MediaDeviceInfo[];
  selectedAudioDevice: string;
  selectedVideoDevice: string;
  setSelectedAudioDevice: (deviceId: string) => void;
  setSelectedVideoDevice: (deviceId: string) => void;
}

// ---------- ICE Servers ----------

function getIceServers(): RTCIceServer[] {
  const servers: RTCIceServer[] = [
    { urls: ["stun:stun.l.google.com:19302"] },
    { urls: ["stun:stun1.l.google.com:19302"] },
  ];

  const turnUrl = process.env.NEXT_PUBLIC_TURN_URL;
  if (turnUrl) {
    const turnConfig: RTCIceServer = { urls: [turnUrl] };
    const turnUsername = process.env.NEXT_PUBLIC_TURN_USERNAME;
    const turnCredential = process.env.NEXT_PUBLIC_TURN_CREDENTIAL;
    if (turnUsername) turnConfig.username = turnUsername;
    if (turnCredential) turnConfig.credential = turnCredential;
    servers.push(turnConfig);
  }

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

// ---------- Hook ----------

export function useVoiceVideo({
  sessionCode,
  userId,
  userName,
  enabled,
}: UseVoiceVideoOptions): UseVoiceVideoReturn {
  // Media state
  const [isAudioEnabled, setIsAudioEnabled] = useState(true);
  const [isVideoEnabled, setIsVideoEnabled] = useState(false);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStreams, setRemoteStreams] = useState<Map<string, MediaStream>>(new Map());
  const [participants, setParticipants] = useState<VoiceParticipant[]>([]);
  const [isPushToTalk, setIsPushToTalk] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0);

  // Device selection
  const [availableDevices, setAvailableDevices] = useState<MediaDeviceInfo[]>([]);
  const [selectedAudioDevice, setSelectedAudioDeviceState] = useState("");
  const [selectedVideoDevice, setSelectedVideoDeviceState] = useState("");

  // Refs
  const channelRef = useRef<RealtimeChannel | null>(null);
  const peerConnectionsRef = useRef<Map<string, RTCPeerConnection>>(new Map());
  const localStreamRef = useRef<MediaStream | null>(null);
  const peerIdRef = useRef(crypto.randomUUID());
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const audioLevelIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const isAudioEnabledRef = useRef(true);
  const isPushToTalkRef = useRef(false);

  // Keep refs in sync
  useEffect(() => { isAudioEnabledRef.current = isAudioEnabled; }, [isAudioEnabled]);
  useEffect(() => { isPushToTalkRef.current = isPushToTalk; }, [isPushToTalk]);

  // ---------- Device enumeration ----------

  const enumerateDevices = useCallback(async () => {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      setAvailableDevices(devices);
    } catch (err) {
      logError("Failed to enumerate devices:", err);
    }
  }, []);

  // ---------- Audio level monitoring ----------

  const startAudioLevelMonitoring = useCallback((stream: MediaStream) => {
    try {
      const audioCtx = new AudioContext();
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 256;
      const source = audioCtx.createMediaStreamSource(stream);
      source.connect(analyser);

      audioContextRef.current = audioCtx;
      analyserRef.current = analyser;

      const dataArray = new Uint8Array(analyser.frequencyBinCount);
      audioLevelIntervalRef.current = setInterval(() => {
        analyser.getByteFrequencyData(dataArray);
        const average = dataArray.reduce((a, b) => a + b, 0) / dataArray.length;
        setAudioLevel(Math.round((average / 255) * 100));
      }, 100);
    } catch (err) {
      logError("Failed to start audio monitoring:", err);
    }
  }, []);

  const stopAudioLevelMonitoring = useCallback(() => {
    if (audioLevelIntervalRef.current) {
      clearInterval(audioLevelIntervalRef.current);
      audioLevelIntervalRef.current = null;
    }
    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }
    analyserRef.current = null;
    setAudioLevel(0);
  }, []);

  // ---------- Peer connection management ----------

  const createPeerConnection = useCallback(
    (remotePeerId: string): RTCPeerConnection => {
      const iceServers = getIceServers();
      const pc = new RTCPeerConnection({ iceServers });

      // Send ICE candidates via signaling
      pc.onicecandidate = (event) => {
        if (event.candidate && channelRef.current) {
          channelRef.current.send({
            type: "broadcast",
            event: "voice-signal",
            payload: {
              type: "ice-candidate",
              sender_id: peerIdRef.current,
              sender_user_id: userId,
              sender_user_name: userName,
              target_id: remotePeerId,
              candidate: {
                candidate: event.candidate.candidate,
                sdpMid: event.candidate.sdpMid || "",
                sdpMLineIndex: event.candidate.sdpMLineIndex || 0,
              },
            } satisfies VoiceSignal,
          });
        }
      };

      // Handle remote tracks
      pc.ontrack = (event) => {
        log("Received track from", remotePeerId, event.track.kind);
        if (event.streams[0]) {
          setRemoteStreams((prev) => {
            const next = new Map(prev);
            next.set(remotePeerId, event.streams[0]);
            return next;
          });
        }
      };

      pc.onconnectionstatechange = () => {
        log(`Peer ${remotePeerId} connection state: ${pc.connectionState}`);
        if (pc.connectionState === "failed" || pc.connectionState === "closed") {
          peerConnectionsRef.current.delete(remotePeerId);
          setRemoteStreams((prev) => {
            const next = new Map(prev);
            next.delete(remotePeerId);
            return next;
          });
        }
      };

      // Add local tracks to peer connection
      if (localStreamRef.current) {
        localStreamRef.current.getTracks().forEach((track) => {
          pc.addTrack(track, localStreamRef.current!);
        });
      }

      peerConnectionsRef.current.set(remotePeerId, pc);
      return pc;
    },
    [userId, userName]
  );

  // ---------- Signaling handlers ----------

  const handleVoiceSignal = useCallback(
    async (signal: VoiceSignal) => {
      if (signal.target_id !== peerIdRef.current) return;

      try {
        switch (signal.type) {
          case "offer": {
            log("Received offer from", signal.sender_id);
            let pc = peerConnectionsRef.current.get(signal.sender_id);
            if (!pc) {
              pc = createPeerConnection(signal.sender_id);
            }
            await pc.setRemoteDescription(
              new RTCSessionDescription({ type: "offer", sdp: signal.sdp })
            );
            const answer = await pc.createAnswer();
            await pc.setLocalDescription(answer);

            channelRef.current?.send({
              type: "broadcast",
              event: "voice-signal",
              payload: {
                type: "answer",
                sender_id: peerIdRef.current,
                sender_user_id: userId,
                sender_user_name: userName,
                target_id: signal.sender_id,
                sdp: answer.sdp,
              } satisfies VoiceSignal,
            });
            break;
          }

          case "answer": {
            log("Received answer from", signal.sender_id);
            const pc = peerConnectionsRef.current.get(signal.sender_id);
            if (pc && pc.signalingState === "have-local-offer") {
              await pc.setRemoteDescription(
                new RTCSessionDescription({ type: "answer", sdp: signal.sdp })
              );
            }
            break;
          }

          case "ice-candidate": {
            const pc = peerConnectionsRef.current.get(signal.sender_id);
            if (pc && signal.candidate && pc.remoteDescription) {
              await pc.addIceCandidate(
                new RTCIceCandidate({
                  candidate: signal.candidate.candidate,
                  sdpMid: signal.candidate.sdpMid,
                  sdpMLineIndex: signal.candidate.sdpMLineIndex,
                })
              );
            }
            break;
          }
        }
      } catch (err) {
        logError("Error handling voice signal:", err);
      }
    },
    [createPeerConnection, userId, userName]
  );

  // ---------- Start / Stop negotiation with a new peer ----------

  const initiateConnection = useCallback(
    async (remotePeerId: string) => {
      if (peerConnectionsRef.current.has(remotePeerId)) return;

      log("Initiating connection to", remotePeerId);
      const pc = createPeerConnection(remotePeerId);

      try {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);

        channelRef.current?.send({
          type: "broadcast",
          event: "voice-signal",
          payload: {
            type: "offer",
            sender_id: peerIdRef.current,
            sender_user_id: userId,
            sender_user_name: userName,
            target_id: remotePeerId,
            sdp: offer.sdp,
          } satisfies VoiceSignal,
        });
      } catch (err) {
        logError("Failed to initiate connection:", err);
      }
    },
    [createPeerConnection, userId, userName]
  );

  // ---------- Main enable / disable lifecycle ----------

  useEffect(() => {
    if (!enabled) return;

    let isCancelled = false;

    const start = async () => {
      // 1. Acquire local media (audio only by default)
      try {
        const constraints: MediaStreamConstraints = {
          audio: selectedAudioDevice
            ? { deviceId: { exact: selectedAudioDevice } }
            : true,
          video: false,
        };
        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        if (isCancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        localStreamRef.current = stream;
        setLocalStream(stream);
        startAudioLevelMonitoring(stream);
        await enumerateDevices();

        // If push-to-talk, start muted
        if (isPushToTalkRef.current) {
          stream.getAudioTracks().forEach((t) => (t.enabled = false));
          setIsAudioEnabled(false);
        }
      } catch (err) {
        logError("Failed to get user media:", err);
        return;
      }

      // 2. Join voice signaling channel
      const channel = supabase.channel(`voice:${sessionCode}`, {
        config: {
          broadcast: { self: false },
          presence: { key: peerIdRef.current },
        },
      });

      // Listen for voice signaling
      channel.on("broadcast", { event: "voice-signal" }, ({ payload }) => {
        if (
          payload &&
          typeof payload === "object" &&
          "type" in payload &&
          "sender_id" in payload
        ) {
          handleVoiceSignal(payload as VoiceSignal);
        }
      });

      // Presence tracking for participant list
      channel.on("presence", { event: "sync" }, () => {
        const state = channel.presenceState();
        const peers: VoiceParticipant[] = [];
        for (const [, presences] of Object.entries(state)) {
          for (const p of presences as unknown as VoicePresence[]) {
            if (p.peerId !== peerIdRef.current) {
              peers.push({
                peerId: p.peerId,
                userId: p.userId,
                userName: p.userName,
                isAudioEnabled: p.isAudioEnabled,
                isVideoEnabled: p.isVideoEnabled,
                isSpeaking: false,
              });
            }
          }
        }
        setParticipants(peers);
      });

      // When new peer joins, initiate connection if our id is "greater" (tiebreaker)
      channel.on("presence", { event: "join" }, ({ key }) => {
        if (key && key !== peerIdRef.current && peerIdRef.current > key) {
          initiateConnection(key);
        }
      });

      // When peer leaves, clean up
      channel.on("presence", { event: "leave" }, ({ key }) => {
        if (key) {
          const pc = peerConnectionsRef.current.get(key);
          if (pc) {
            pc.close();
            peerConnectionsRef.current.delete(key);
          }
          setRemoteStreams((prev) => {
            const next = new Map(prev);
            next.delete(key);
            return next;
          });
        }
      });

      await channel.subscribe(async (status) => {
        if (status === "SUBSCRIBED") {
          await channel.track({
            peerId: peerIdRef.current,
            userId,
            userName,
            isAudioEnabled: isAudioEnabledRef.current,
            isVideoEnabled: false,
          } satisfies VoicePresence);
        }
      });

      channelRef.current = channel;
    };

    start();

    return () => {
      isCancelled = true;

      // Tear down all peer connections
      peerConnectionsRef.current.forEach((pc) => pc.close());
      peerConnectionsRef.current.clear();

      // Stop local media
      localStreamRef.current?.getTracks().forEach((t) => t.stop());
      localStreamRef.current = null;
      setLocalStream(null);
      setRemoteStreams(new Map());
      setParticipants([]);

      stopAudioLevelMonitoring();

      // Leave channel
      if (channelRef.current) {
        channelRef.current.untrack();
        supabase.removeChannel(channelRef.current);
        channelRef.current = null;
      }
    };
  }, [
    enabled,
    sessionCode,
    userId,
    userName,
    selectedAudioDevice,
    handleVoiceSignal,
    initiateConnection,
    startAudioLevelMonitoring,
    stopAudioLevelMonitoring,
    enumerateDevices,
  ]);

  // ---------- Toggle audio ----------

  const toggleAudio = useCallback(() => {
    if (localStreamRef.current) {
      const newState = !isAudioEnabledRef.current;
      localStreamRef.current.getAudioTracks().forEach((t) => (t.enabled = newState));
      setIsAudioEnabled(newState);

      // Update presence
      channelRef.current?.track({
        peerId: peerIdRef.current,
        userId,
        userName,
        isAudioEnabled: newState,
        isVideoEnabled,
      });
    }
  }, [userId, userName, isVideoEnabled]);

  // ---------- Toggle video ----------

  const toggleVideo = useCallback(async () => {
    if (!localStreamRef.current) return;

    if (isVideoEnabled) {
      // Turn off video: remove video tracks
      localStreamRef.current.getVideoTracks().forEach((t) => {
        t.stop();
        localStreamRef.current!.removeTrack(t);
      });

      // Remove video track from all peer connections
      peerConnectionsRef.current.forEach((pc) => {
        pc.getSenders().forEach((sender) => {
          if (sender.track?.kind === "video") {
            pc.removeTrack(sender);
          }
        });
      });

      setIsVideoEnabled(false);
    } else {
      // Turn on video: add video track
      try {
        const videoStream = await navigator.mediaDevices.getUserMedia({
          video: selectedVideoDevice
            ? { deviceId: { exact: selectedVideoDevice } }
            : true,
        });
        const videoTrack = videoStream.getVideoTracks()[0];
        localStreamRef.current.addTrack(videoTrack);

        // Add to all existing peer connections
        peerConnectionsRef.current.forEach((pc) => {
          pc.addTrack(videoTrack, localStreamRef.current!);
        });

        setIsVideoEnabled(true);
        setLocalStream(localStreamRef.current);
      } catch (err) {
        logError("Failed to enable video:", err);
        return;
      }
    }

    // Update presence
    channelRef.current?.track({
      peerId: peerIdRef.current,
      userId,
      userName,
      isAudioEnabled,
      isVideoEnabled: !isVideoEnabled,
    });
  }, [isVideoEnabled, isAudioEnabled, userId, userName, selectedVideoDevice]);

  // ---------- Push-to-talk ----------

  const handleSetPushToTalk = useCallback(
    (enabled: boolean) => {
      setIsPushToTalk(enabled);
      if (enabled && localStreamRef.current) {
        // Mute immediately when enabling PTT
        localStreamRef.current.getAudioTracks().forEach((t) => (t.enabled = false));
        setIsAudioEnabled(false);
      }
    },
    []
  );

  const startPushToTalk = useCallback(() => {
    if (isPushToTalkRef.current && localStreamRef.current) {
      localStreamRef.current.getAudioTracks().forEach((t) => (t.enabled = true));
      setIsAudioEnabled(true);
    }
  }, []);

  const stopPushToTalk = useCallback(() => {
    if (isPushToTalkRef.current && localStreamRef.current) {
      localStreamRef.current.getAudioTracks().forEach((t) => (t.enabled = false));
      setIsAudioEnabled(false);
    }
  }, []);

  // ---------- Device selection ----------

  const setSelectedAudioDevice = useCallback((deviceId: string) => {
    setSelectedAudioDeviceState(deviceId);
  }, []);

  const setSelectedVideoDevice = useCallback((deviceId: string) => {
    setSelectedVideoDeviceState(deviceId);
  }, []);

  return {
    isAudioEnabled,
    isVideoEnabled,
    toggleAudio,
    toggleVideo,
    localStream,
    remoteStreams,
    participants,
    isPushToTalk,
    setPushToTalk: handleSetPushToTalk,
    startPushToTalk,
    stopPushToTalk,
    audioLevel,
    availableDevices,
    selectedAudioDevice,
    selectedVideoDevice,
    setSelectedAudioDevice,
    setSelectedVideoDevice,
  };
}
