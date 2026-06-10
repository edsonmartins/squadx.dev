"use client";

import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { specTasksApi, RequirementResponse } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormModal, FieldError } from "@/components/shared/form-modal";
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
    <FormModal
      open={open}
      onClose={onClose}
      title="Nova tarefa"
      description="A tarefa nasce de um requisito (rastreabilidade)."
      onSubmit={handleSubmit((data) => createMutation.mutate(data))}
      isSubmitting={createMutation.isPending}
      submitLabel="Criar tarefa"
      submittingLabel="Criando..."
      cancelLabel="Cancelar"
    >
      <div className="grid gap-2">
        <Label htmlFor="title">Título</Label>
        <Input id="title" {...register("title")} />
        <FieldError message={errors.title?.message} />
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
    </FormModal>
  );
}
