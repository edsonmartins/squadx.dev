"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { requirementsApi, RequirementType, ScenarioInput } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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

interface RequirementModalProps {
  open: boolean;
  onClose: () => void;
  changeId: number;
}

const emptyScenario = (): ScenarioInput => ({ name: "", when: "", then: "" });

export function RequirementModal({ open, onClose, changeId }: RequirementModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [type, setType] = useState<RequirementType>("ADDED");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [scenarios, setScenarios] = useState<ScenarioInput[]>([emptyScenario()]);

  const reset = () => {
    setType("ADDED");
    setTitle("");
    setDescription("");
    setScenarios([emptyScenario()]);
  };

  const mutation = useMutation({
    mutationFn: () =>
      requirementsApi.create({
        change_id: changeId,
        type,
        title,
        description: description || undefined,
        scenarios,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-requirements", changeId] });
      toast({ title: "Requisito criado" });
      reset();
      onClose();
    },
    onError: () => toast({ title: "Erro", description: "Verifique os campos (todo requisito exige ≥1 cenário).", variant: "destructive" }),
  });

  const updateScenario = (i: number, field: keyof ScenarioInput, value: string) =>
    setScenarios((prev) => prev.map((s, idx) => (idx === i ? { ...s, [field]: value } : s)));

  const valid = title.trim() && scenarios.every((s) => s.name.trim() && s.when.trim() && s.then.trim());

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[640px]">
        <DialogHeader>
          <DialogTitle>Novo requisito</DialogTitle>
          <DialogDescription>Todo requisito precisa de ao menos um cenário de aceite (QUANDO/ENTÃO).</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-3 gap-3">
            <div className="grid gap-2">
              <Label>Tipo</Label>
              <Select value={type} onValueChange={(v) => setType(v as RequirementType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADDED">ADDED</SelectItem>
                  <SelectItem value="MODIFIED">MODIFIED</SelectItem>
                  <SelectItem value="REMOVED">REMOVED</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="col-span-2 grid gap-2">
              <Label htmlFor="title">Título</Label>
              <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="desc">Descrição</Label>
            <Textarea id="desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label>Cenários</Label>
              <Button type="button" size="sm" variant="outline" onClick={() => setScenarios((p) => [...p, emptyScenario()])}>
                <Plus className="mr-1 h-3 w-3" /> Cenário
              </Button>
            </div>
            {scenarios.map((sc, i) => (
              <div key={i} className="space-y-2 rounded border p-2">
                <div className="flex items-center gap-2">
                  <Input
                    placeholder="Nome do cenário"
                    value={sc.name}
                    onChange={(e) => updateScenario(i, "name", e.target.value)}
                  />
                  {scenarios.length > 1 && (
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      onClick={() => setScenarios((p) => p.filter((_, idx) => idx !== i))}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
                <Input placeholder="QUANDO ..." value={sc.when} onChange={(e) => updateScenario(i, "when", e.target.value)} />
                <Input placeholder="ENTÃO ..." value={sc.then} onChange={(e) => updateScenario(i, "then", e.target.value)} />
              </div>
            ))}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!valid || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? "Criando..." : "Criar requisito"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
