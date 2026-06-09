"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { specTasksApi, RequirementResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";

interface SpecTaskModalProps {
  open: boolean;
  onClose: () => void;
  changeId: number;
  requirements: RequirementResponse[];
}

export function SpecTaskModal({ open, onClose, changeId, requirements }: SpecTaskModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [title, setTitle] = useState("");
  const [requirementId, setRequirementId] = useState<string>("");

  const mutation = useMutation({
    mutationFn: () =>
      specTasksApi.create({
        change_id: changeId,
        title,
        requirement_id: requirementId ? Number(requirementId) : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-tasks", changeId] });
      queryClient.invalidateQueries({ queryKey: ["cp-where-we-are"] });
      toast({ title: "Tarefa criada" });
      setTitle("");
      setRequirementId("");
      onClose();
    },
    onError: () => toast({ title: "Erro", description: "Não foi possível criar a tarefa.", variant: "destructive" }),
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nova tarefa</DialogTitle>
          <DialogDescription>A tarefa nasce de um requisito (rastreabilidade).</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid gap-2">
            <Label htmlFor="title">Título</Label>
            <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} />
          </div>
          <div className="grid gap-2">
            <Label>Requisito de origem</Label>
            <Select value={requirementId} onValueChange={setRequirementId}>
              <SelectTrigger>
                <SelectValue placeholder="Selecione um requisito (opcional)" />
              </SelectTrigger>
              <SelectContent>
                {requirements.map((r) => (
                  <SelectItem key={r.id} value={String(r.id)}>
                    {r.requirement_id} — {r.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!title.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? "Criando..." : "Criar tarefa"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
