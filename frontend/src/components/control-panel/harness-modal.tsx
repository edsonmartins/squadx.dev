"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { harnessesApi } from "@/lib/api";
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
import { useToast } from "@/hooks/use-toast";

const schema = z.object({
  key: z.string().min(1, "Chave é obrigatória").max(100),
  name: z.string().min(1, "Nome é obrigatório").max(255),
  vendor: z.string().optional(),
  models: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

interface HarnessModalProps {
  open: boolean;
  onClose: () => void;
  organizationId: number;
}

export function HarnessModal({ open, onClose, organizationId }: HarnessModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { key: "", name: "", vendor: "", models: "" },
  });

  useEffect(() => {
    if (open) reset({ key: "", name: "", vendor: "", models: "" });
  }, [open, reset]);

  const mutation = useMutation({
    mutationFn: (data: FormData) =>
      harnessesApi.register({
        organization_id: organizationId,
        key: data.key,
        name: data.name,
        vendor: data.vendor || undefined,
        models: (data.models ?? "")
          .split(",")
          .map((m) => m.trim())
          .filter(Boolean),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-harnesses", organizationId] });
      toast({ title: "Harness registrado" });
      onClose();
    },
    onError: () => toast({ title: "Erro", description: "Chave duplicada ou inválida.", variant: "destructive" }),
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Registrar harness</DialogTitle>
          <DialogDescription>Todo harness fala o mesmo contrato MCP do workspace.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-2">
                <Label htmlFor="key">Chave</Label>
                <Input id="key" placeholder="claude-code" {...register("key")} />
                {errors.key && <p className="text-sm text-destructive">{errors.key.message}</p>}
              </div>
              <div className="grid gap-2">
                <Label htmlFor="name">Nome</Label>
                <Input id="name" placeholder="Claude Code" {...register("name")} />
                {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="vendor">Fornecedor</Label>
              <Input id="vendor" placeholder="Anthropic" {...register("vendor")} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="models">Modelos (separados por vírgula)</Label>
              <Input id="models" placeholder="claude-opus-4-8, claude-sonnet-4-6" {...register("models")} />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Registrando..." : "Registrar"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
