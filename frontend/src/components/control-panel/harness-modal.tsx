"use client";

import { useState } from "react";
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

interface HarnessModalProps {
  open: boolean;
  onClose: () => void;
  organizationId: number;
}

export function HarnessModal({ open, onClose, organizationId }: HarnessModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [key, setKey] = useState("");
  const [name, setName] = useState("");
  const [vendor, setVendor] = useState("");
  const [models, setModels] = useState("");

  const reset = () => {
    setKey("");
    setName("");
    setVendor("");
    setModels("");
  };

  const mutation = useMutation({
    mutationFn: () =>
      harnessesApi.register({
        organization_id: organizationId,
        key,
        name,
        vendor: vendor || undefined,
        models: models
          .split(",")
          .map((m) => m.trim())
          .filter(Boolean),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-harnesses", organizationId] });
      toast({ title: "Harness registrado" });
      reset();
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
        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="key">Chave</Label>
              <Input id="key" placeholder="claude-code" value={key} onChange={(e) => setKey(e.target.value)} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="name">Nome</Label>
              <Input id="name" placeholder="Claude Code" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="vendor">Fornecedor</Label>
            <Input id="vendor" placeholder="Anthropic" value={vendor} onChange={(e) => setVendor(e.target.value)} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="models">Modelos (separados por vírgula)</Label>
            <Input
              id="models"
              placeholder="claude-opus-4-8, claude-sonnet-4-6"
              value={models}
              onChange={(e) => setModels(e.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!key.trim() || !name.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? "Registrando..." : "Registrar"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
