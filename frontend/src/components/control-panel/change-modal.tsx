"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { controlPanelChangesApi } from "@/lib/api";
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

interface ChangeModalProps {
  open: boolean;
  onClose: () => void;
  projectId: number;
}

export function ChangeModal({ open, onClose, projectId }: ChangeModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [module, setModule] = useState("");

  const createMutation = useMutation({
    mutationFn: () => controlPanelChangesApi.create({ project_id: projectId, module: module || undefined }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-changes", projectId] });
      toast({ title: "Mudança criada", description: "A mudança foi criada com sucesso." });
      setModule("");
      onClose();
    },
    onError: () => {
      toast({ title: "Erro", description: "Não foi possível criar a mudança.", variant: "destructive" });
    },
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nova mudança</DialogTitle>
          <DialogDescription>Crie uma mudança (change) para começar a especificar.</DialogDescription>
        </DialogHeader>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createMutation.mutate();
          }}
        >
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="module">Módulo</Label>
              <Input
                id="module"
                placeholder="ex.: auth, billing"
                value={module}
                onChange={(e) => setModule(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Criando..." : "Criar mudança"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
