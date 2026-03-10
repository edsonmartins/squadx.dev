import { useState, useCallback } from "react";
import {
  recordingsApi,
  RecordingResponse,
  CompleteRecordingRequest,
} from "@/lib/api";

interface UseRecordingReturn {
  recording: RecordingResponse | null;
  recordings: RecordingResponse[];
  isLoading: boolean;
  error: string | null;
  startRecording: (sessionId: number) => Promise<RecordingResponse | null>;
  completeRecording: (
    id: number,
    data: CompleteRecordingRequest
  ) => Promise<RecordingResponse | null>;
  getPlaybackUrl: (id: number) => Promise<RecordingResponse | null>;
  listRecordings: (sessionId: number) => Promise<void>;
}

export function useRecording(): UseRecordingReturn {
  const [recording, setRecording] = useState<RecordingResponse | null>(null);
  const [recordings, setRecordings] = useState<RecordingResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startRecording = useCallback(
    async (sessionId: number): Promise<RecordingResponse | null> => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await recordingsApi.start({ session_id: sessionId });
        setRecording(response);
        return response;
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to start recording";
        setError(message);
        return null;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  const completeRecording = useCallback(
    async (
      id: number,
      data: CompleteRecordingRequest
    ): Promise<RecordingResponse | null> => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await recordingsApi.complete(id, data);
        setRecording(response);
        return response;
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to complete recording";
        setError(message);
        return null;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  const getPlaybackUrl = useCallback(
    async (id: number): Promise<RecordingResponse | null> => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await recordingsApi.getUrl(id);
        setRecording(response);
        return response;
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to get playback URL";
        setError(message);
        return null;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  const listRecordings = useCallback(async (sessionId: number) => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await recordingsApi.listBySession(sessionId);
      setRecordings(response);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to list recordings";
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  return {
    recording,
    recordings,
    isLoading,
    error,
    startRecording,
    completeRecording,
    getPlaybackUrl,
    listRecordings,
  };
}
