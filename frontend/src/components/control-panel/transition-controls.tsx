"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { specTasksApi, SpecTaskResponse, SpecTaskStatus } from "@/lib/api";
import { manualTransitions, transitionActionLabel } from "@/lib/control-panel";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";

export function TransitionControls({ task }: { task: SpecTaskResponse }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [blockOpen, setBlockOpen] = useState(false);
  const [reason, setReason] = useState("");

  const mutation = useMutation({
    mutationFn: (vars: { status: SpecTaskStatus; note?: string }) =>
      specTasksApi.transition(task.id, vars),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-tasks", task.change_id] });
      queryClient.invalidateQueries({ queryKey: ["cp-where-we-are"] });
      setBlockOpen(false);
      setReason("");
    },
    onError: () => {
      toast({ title: "Transição inválida", description: "Verifique o estado da tarefa.", variant: "destructive" });
    },
  });

  const targets = manualTransitions(task.status);
  if (targets.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-1">
      {targets.map((target) =>
        target === "BLOQUEADA" ? (
          <Button key={target} size="sm" variant="outline" onClick={() => setBlockOpen(true)}>
            {transitionActionLabel(target)}
          </Button>
        ) : (
          <Button
            key={target}
            size="sm"
            variant="outline"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate({ status: target })}
          >
            {transitionActionLabel(target)}
          </Button>
        )
      )}

      <Dialog open={blockOpen} onOpenChange={setBlockOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Bloquear tarefa</DialogTitle>
            <DialogDescription>Todo bloqueio exige um motivo.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-2 py-2">
            <Label htmlFor="reason">Motivo</Label>
            <Textarea
              id="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="ex.: aguardando definição da API"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBlockOpen(false)}>
              Cancelar
            </Button>
            <Button
              disabled={!reason.trim() || mutation.isPending}
              onClick={() => mutation.mutate({ status: "BLOQUEADA", note: reason })}
            >
              Bloquear
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
