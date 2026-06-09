"use client";

import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
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

const schema = z.object({
  title: z.string().min(1, "Título é obrigatório").max(255),
  requirement_id: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

interface SpecTaskModalProps {
  open: boolean;
  onClose: () => void;
  changeId: number;
  requirements: RequirementResponse[];
}

export function SpecTaskModal({ open, onClose, changeId, requirements }: SpecTaskModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { title: "", requirement_id: "" },
  });

  useEffect(() => {
    if (open) reset({ title: "", requirement_id: "" });
  }, [open, reset]);

  const createMutation = useMutation({
    mutationFn: (data: FormData) =>
      specTasksApi.create({
        change_id: changeId,
        title: data.title,
        requirement_id: data.requirement_id ? Number(data.requirement_id) : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-tasks", changeId] });
      queryClient.invalidateQueries({ queryKey: ["cp-where-we-are"] });
      toast({ title: "Tarefa criada" });
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
        <form onSubmit={handleSubmit((data) => createMutation.mutate(data))}>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <Label htmlFor="title">Título</Label>
              <Input id="title" {...register("title")} />
              {errors.title && <p className="text-sm text-destructive">{errors.title.message}</p>}
            </div>
            <div className="grid gap-2">
              <Label>Requisito de origem</Label>
              <Controller
                control={control}
                name="requirement_id"
                render={({ field }) => (
                  <Select value={field.value || undefined} onValueChange={field.onChange}>
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
                )}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Criando..." : "Criar tarefa"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
