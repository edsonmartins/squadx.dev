"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { controlPanelChangesApi } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormModal } from "@/components/shared/form-modal";
import { useToast } from "@/hooks/use-toast";

const schema = z.object({ module: z.string().max(255).optional() });
type FormData = z.infer<typeof schema>;

interface ChangeModalProps {
  open: boolean;
  onClose: () => void;
  projectId: number;
}

export function ChangeModal({ open, onClose, projectId }: ChangeModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { register, handleSubmit, reset } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { module: "" },
  });

  useEffect(() => {
    if (open) reset({ module: "" });
  }, [open, reset]);

  const createMutation = useMutation({
    mutationFn: (data: FormData) =>
      controlPanelChangesApi.create({ project_id: projectId, module: data.module || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-changes", projectId] });
      toast({ title: "Mudança criada", description: "A mudança foi criada com sucesso." });
      onClose();
    },
    onError: () => {
      toast({ title: "Erro", description: "Não foi possível criar a mudança.", variant: "destructive" });
    },
  });

  return (
    <FormModal
      open={open}
      onClose={onClose}
      title="Nova mudança"
      description="Crie uma mudança (change) para começar a especificar."
      onSubmit={handleSubmit((data) => createMutation.mutate(data))}
      isSubmitting={createMutation.isPending}
      submitLabel="Criar mudança"
      submittingLabel="Criando..."
      cancelLabel="Cancelar"
    >
      <div className="grid gap-2">
        <Label htmlFor="module">Módulo</Label>
        <Input id="module" placeholder="ex.: auth, billing" {...register("module")} />
      </div>
    </FormModal>
  );
}
