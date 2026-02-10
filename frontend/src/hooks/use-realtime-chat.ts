"use client";

import { useCallback, useEffect, useState, useRef } from "react";
import { signaling, ChatMessage } from "@/lib/supabase";

interface UseRealtimeChatOptions {
  /** Session code (join code) for the live session */
  sessionCode: string;
  /** Display name for the current user */
  displayName?: string;
  /** Maximum number of messages to keep in history */
  maxMessages?: number;
}

interface UseRealtimeChatReturn {
  /** List of chat messages */
  messages: ChatMessage[];
  /** Send a chat message */
  sendMessage: (content: string) => Promise<void>;
  /** Clear chat history */
  clearMessages: () => void;
  /** Whether the chat is connected */
  isConnected: boolean;
}

/**
 * Hook for real-time chat in live sessions using Supabase Realtime.
 */
export function useRealtimeChat({
  sessionCode,
  displayName = "Anonymous",
  maxMessages = 100,
}: UseRealtimeChatOptions): UseRealtimeChatReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const isSetupRef = useRef(false);

  // Set display name
  useEffect(() => {
    signaling.setDisplayName(displayName);
  }, [displayName]);

  // Setup chat message handler
  useEffect(() => {
    if (!sessionCode || isSetupRef.current) return;

    const handleChatMessage = (message: ChatMessage) => {
      setMessages((prev) => {
        // Avoid duplicates
        if (prev.some((m) => m.id === message.id)) {
          return prev;
        }

        // Add new message and trim if over limit
        const newMessages = [...prev, message];
        if (newMessages.length > maxMessages) {
          return newMessages.slice(-maxMessages);
        }
        return newMessages;
      });
    };

    signaling.onChat(handleChatMessage);
    isSetupRef.current = true;
    setIsConnected(true);

    return () => {
      isSetupRef.current = false;
      setIsConnected(false);
    };
  }, [sessionCode, maxMessages]);

  // Send a message
  const sendMessage = useCallback(
    async (content: string) => {
      if (!content.trim()) return;

      try {
        await signaling.sendChatMessage(content.trim());

        // Add own message to local state immediately (optimistic update)
        const ownMessage: ChatMessage = {
          id: crypto.randomUUID(),
          sender_id: signaling.currentPeerId,
          sender_name: displayName,
          content: content.trim(),
          timestamp: new Date().toISOString(),
        };

        setMessages((prev) => {
          const newMessages = [...prev, ownMessage];
          if (newMessages.length > maxMessages) {
            return newMessages.slice(-maxMessages);
          }
          return newMessages;
        });
      } catch (error) {
        console.error("[Chat] Error sending message:", error);
      }
    },
    [displayName, maxMessages]
  );

  // Clear messages
  const clearMessages = useCallback(() => {
    setMessages([]);
  }, []);

  return {
    messages,
    sendMessage,
    clearMessages,
    isConnected,
  };
}
