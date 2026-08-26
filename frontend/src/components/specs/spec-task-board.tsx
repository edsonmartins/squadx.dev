"use client";

import { useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Bot, ShieldAlert, User } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import {
  SPEC_TASK_STATUS_LABELS,
  SPEC_TASK_UI_TRANSITIONS,
  SpecTaskResponse,
  SpecTaskStatus,
  specTasksApi,
} from "@/lib/api";

const COLUMN_ORDER: SpecTaskStatus[] = [
  "A_FAZER",
  "EM_CURSO",
  "EM_VALIDACAO",
  "CONCLUIDA",
  "BLOQUEADA",
  "AJUSTES",
];

const TARGET_LABEL: Partial<Record<SpecTaskStatus, string>> = {
  EM_CURSO: "Executar",
  EM_VALIDACAO: "Enviar p/ validação",
  BLOQUEADA: "Bloquear",
};

type AssigneeFilter = "ALL" | "HUMAN" | "AGENT";

interface PendingAction {
  task: SpecTaskResponse;
  target: SpecTaskStatus;
}

export function SpecTaskBoard({
  changeId,
  tasks,
}: {
  changeId: number;
  tasks: SpecTaskResponse[];
}) {
  const [filter, setFilter] = useState<AssigneeFilter>("ALL");
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [note, setNote] = useState("");
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const move = useMutation({
    mutationFn: (p: { id: number; status: SpecTaskStatus; note?: string }) =>
      specTasksApi.transition(p.id, p.status, p.note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["spec-tasks", changeId] });
      toast({ title: "Tarefa atualizada" });
      setPending(null);
      setNote("");
    },
    onError: () =>
      toast({
        title: "Transição recusada pela máquina de estados",
        variant: "destructive",
      }),
  });

  const byStatus = useMemo(() => {
    const map = new Map<SpecTaskStatus, SpecTaskResponse[]>();
    for (const status of COLUMN_ORDER) map.set(status, []);
    for (const task of tasks) {
      const list = map.get(task.status);
      if (list) list.push(task);
    }
    return map;
  }, [tasks]);

  const filtered = useMemo(
    () => (filter === "ALL" ? tasks : tasks.filter((t) => t.assignee_type === filter)),
    [tasks, filter]
  );

  const confirm = () => {
    if (!pending) return;
    if (pending.target === "BLOQUEADA" && !note.trim()) return; // motivo obrigatório
    move.mutate({ id: pending.task.id, status: pending.target, note: note.trim() || undefined });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        {(["ALL", "HUMAN", "AGENT"] as AssigneeFilter[]).map((f) => (
          <Button
            key={f}
            size="sm"
            variant={filter === f ? "default" : "outline"}
            onClick={() => setFilter(f)}
          >
            {f === "ALL" ? "Todos" : f === "HUMAN" ? "Humanos" : "IA"}
          </Button>
        ))}
        <span className="ml-auto text-xs text-muted-foreground">
          Concluída/Ajustes são definidos apenas pelo Pass 5
        </span>
      </div>

      <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
        {COLUMN_ORDER.map((status) => {
          const columnTasks = (byStatus.get(status) || []).filter(
            (t) => filter === "ALL" || t.assignee_type === filter
          );
          return (
            <Card key={status} className="flex flex-col">
              <CardHeader className="pb-2">
                <CardTitle className="flex items-center justify-between text-sm font-medium">
                  {SPEC_TASK_STATUS_LABELS[status]}
                  <Badge variant="secondary">{columnTasks.length}</Badge>
                </CardTitle>
              </CardHeader>
              <CardContent className="flex-1 space-y-2">
                {columnTasks.map((task) => (
                  <SpecTaskCard
                    key={task.id}
                    task={task}
                    onAction={(target) => setPending({ task, target })}
                  />
                ))}
                {!columnTasks.length && (
                  <p className="py-4 text-center text-xs text-muted-foreground">—</p>
                )}
              </CardContent>
            </Card>
          );
        })}
        {!filtered.length && (
          <p className="col-span-full py-8 text-center text-sm text-muted-foreground">
            Nenhuma tarefa para o filtro atual.
          </p>
        )}
      </div>

      <Dialog open={pending !== null} onOpenChange={(open) => !open && setPending(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pending?.target === "BLOQUEADA"
                ? "Bloquear tarefa"
                : `Mover para ${pending ? SPEC_TASK_STATUS_LABELS[pending.target] : ""}`}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="transition-note">
              {pending?.target === "BLOQUEADA" ? "Motivo do bloqueio (obrigatório)" : "Nota (opcional)"}
            </Label>
            <Input
              id="transition-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={
                pending?.target === "BLOQUEADA"
                  ? "Ex.: aguardando credenciais do ambiente"
                  : "Contexto desta transição"
              }
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPending(null)}>
              Cancelar
            </Button>
            <Button
              onClick={confirm}
              disabled={move.isPending || (pending?.target === "BLOQUEADA" && !note.trim())}
            >
              Confirmar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SpecTaskCard({
  task,
  onAction,
}: {
  task: SpecTaskResponse;
  onAction: (target: SpecTaskStatus) => void;
}) {
  const targets = SPEC_TASK_UI_TRANSITIONS[task.status];
  return (
    <div className="space-y-2 rounded-lg border p-3 text-sm">
      <div className="flex items-start justify-between gap-2">
        <span className="font-medium leading-snug">{task.title}</span>
        {task.assignee_type === "AGENT" ? (
          <Bot className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-label="Agente" />
        ) : (
          <User className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-label="Humano" />
        )}
      </div>
      <div className="flex flex-wrap items-center gap-1.5">
        {task.requirement_ref && (
          <Badge variant="outline" className="font-normal">
            {task.requirement_ref}
          </Badge>
        )}
        <span className="text-xs text-muted-foreground">
          {task.assignee_type === "AGENT"
            ? task.assigned_agent_name || "agente"
            : task.assigned_user_name || "humano"}
        </span>
      </div>

      {task.status === "EM_VALIDACAO" && (
        <p className="text-xs text-muted-foreground">Aguardando Pass 5…</p>
      )}
      {task.blocker_reason && (
        <p className="flex items-start gap-1 text-xs text-destructive">
          <ShieldAlert className="mt-0.5 h-3 w-3 shrink-0" /> {task.blocker_reason}
        </p>
      )}
      {task.revise_reason && (
        <p className="rounded bg-destructive/10 p-2 text-xs text-destructive">
          Crítica do Pass 5: {task.revise_reason}
        </p>
      )}

      {targets.length > 0 && (
        <div className="flex flex-wrap gap-1.5 pt-1">
          {targets.map((target) => (
            <Button
              key={target}
              size="sm"
              variant="outline"
              className="h-7 px-2 text-xs"
              onClick={() => onAction(target)}
            >
              {TARGET_LABEL[target] ?? SPEC_TASK_STATUS_LABELS[target]}
            </Button>
          ))}
        </div>
      )}
    </div>
  );
}
