"use client";

import * as React from "react";
import { Badge } from "@/components/ui/badge";
import { specTaskEventsApi, type SpecTaskEvent } from "@/lib/api";

interface SpecTaskEventTimelineProps {
  taskId: number;
}

/** Audit-first timeline for append-only task events. */
export function SpecTaskEventTimeline({ taskId }: SpecTaskEventTimelineProps) {
  const [events, setEvents] = React.useState<SpecTaskEvent[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    specTaskEventsApi.list(taskId)
      .then((response) => {
        if (active) setEvents(response ?? []);
      })
      .catch(() => active && setError("Não foi possível carregar o histórico."))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [taskId]);

  if (loading) return <p className="text-sm text-muted-foreground">Carregando histórico…</p>;
  if (error) return <p className="text-sm text-destructive">{error}</p>;
  if (events.length === 0) return <p className="text-sm text-muted-foreground">Nenhum evento registrado.</p>;

  return (
    <ol className="space-y-3" aria-label="Histórico da tarefa">
      {events.map((event) => (
        <li key={event.id} className="border-l-2 border-muted pl-3">
          <div className="flex items-center gap-2">
            <Badge variant="outline">{event.type}</Badge>
            <span className="text-xs text-muted-foreground">{event.source}</span>
          </div>
          <p className="text-xs text-muted-foreground">
            {new Date(event.occurredAt).toLocaleString()}
            {event.sourceRef ? ` · ${event.sourceRef}` : ""}
          </p>
          {event.payload && <p className="mt-1 text-sm">{event.payload}</p>}
        </li>
      ))}
    </ol>
  );
}
