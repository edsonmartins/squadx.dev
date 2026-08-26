"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Cable, Pencil, Plus, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import { useOrganizationStore } from "@/stores/organization-store";
import { harnessesApi, HarnessRequest, HarnessResponse } from "@/lib/api";

const EMPTY_FORM: HarnessRequest = { key: "", name: "", vendor: "", model: "", models: [] };

export default function HarnessesPage() {
  const { selectedOrganization, organizations } = useOrganizationStore();
  const organizationId = selectedOrganization?.id || organizations[0]?.id;
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<HarnessRequest>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [modelsInput, setModelsInput] = useState("");

  const query = useQuery({
    queryKey: ["harnesses", organizationId],
    queryFn: () => harnessesApi.list(organizationId!),
    enabled: Boolean(organizationId),
  });

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setModelsInput("");
    setEditingId(null);
  };

  const optionChange = (field: "key" | "name" | "vendor", value: string) =>
    setForm((f) => ({ ...f, [field]: value }));

  const openEdit = (h: HarnessResponse) => {
    setEditingId(h.id);
    setForm({ key: h.key, name: h.name, vendor: h.vendor, model: h.model ?? "", models: h.models ?? [] });
    setModelsInput((h.models ?? []).join(", "));
  };

  const save = useMutation({
    mutationFn: () => {
      // Quando edita, o `id` no form identifica a alteração; na criação via POST.
      const payload: HarnessRequest = {
        ...form,
        models: modelsInput.split(",").map((m) => m.trim()).filter(Boolean),
      };
      return editingId !== null
        ? harnessesApi.update(editingId, payload)
        : harnessesApi.create(organizationId!, payload);
    },
    onSuccess: () => {
      resetForm();
      queryClient.invalidateQueries({ queryKey: ["harnesses", organizationId] });
      toast({ title: editingId !== null ? "Harness updated" : "Harness added" });
    },
    onError: () => toast({ title: "Unable to save harness", variant: "destructive" }),
  });

  const remove = useMutation({
    mutationFn: (id: number) => harnessesApi.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["harnesses", organizationId] }),
  });

  useEffect(() => {
    resetForm();
  }, [organizationId]);

  const canSubmit = form.key && form.name && form.vendor && !save.isPending;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Harness connectors</h1>
        <p className="text-muted-foreground">
          Configure runtimes, models disponíveis e o modelo default que os agentes herdam.
        </p>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {editingId !== null ? <Pencil className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
              {editingId !== null ? "Edit connector" : "Add connector"}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {(["key", "name", "vendor"] as const).map((field) => (
              <div key={field} className="space-y-1">
                <Label>{field[0].toUpperCase() + field.slice(1)}</Label>
                <Input
                  value={form[field] ?? ""}
                  onChange={(e) => optionChange(field, e.target.value)}
                  placeholder={field === "key" ? "openai" : field === "name" ? "OpenAI API" : "OpenAI"}
                />
              </div>
            ))}
            <div className="space-y-1">
              <Label>Models (comma separated)</Label>
              <Input
                value={modelsInput}
                onChange={(e) => setModelsInput(e.target.value)}
                placeholder="gpt-4o, gpt-4o-mini"
              />
            </div>
            <div className="space-y-1">
              <Label>Default model</Label>
              <Input
                value={form.model ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, model: e.target.value }))}
                placeholder="gpt-4o"
              />
              <p className="text-xs text-muted-foreground">
                Model herdado pelos agentes que escolherem este harness (seleção em agentes).
              </p>
            </div>
            <div className="flex gap-2">
              <Button className="w-full" disabled={!canSubmit} onClick={() => save.mutate()}>
                {save.isPending ? "Saving..." : editingId !== null ? "Save changes" : "Add harness"}
              </Button>
              {editingId !== null && (
                <Button variant="outline" onClick={resetForm}>Cancel</Button>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Cable className="h-4 w-4" /> Configured connectors
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {query.isLoading && <p className="text-sm text-muted-foreground">Loading...</p>}
            {!query.isLoading && !query.data?.length && (
              <p className="text-sm text-muted-foreground">No harnesses configured yet.</p>
            )}
            {query.data?.map((h) => (
              <div key={h.id} className="rounded-lg border p-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-medium">{h.name} <span className="text-xs text-muted-foreground">({h.key})</span></p>
                    <p className="truncate text-sm text-muted-foreground">
                      {h.vendor}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <Badge variant={h.status === "AVAILABLE" || h.status === "CONECTADO" ? "default" : "secondary"}>
                      {h.status?.toLowerCase() ?? "disponível"}
                    </Badge>
                    <Button variant="ghost" size="icon" onClick={() => openEdit(h)} aria-label={`Edit ${h.name}`}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => remove.mutate(h.id)} aria-label={`Delete ${h.name}`}>
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </div>
                {h.model && (
                  <p className="mt-1 text-xs">
                    <span className="text-muted-foreground">Default model: </span>
                    <code className="rounded bg-muted px-1 py-0.5 font-mono">{h.model}</code>
                  </p>
                )}
                {h.models?.length ? (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Disponível: {h.models.join(", ")}
                  </p>
                ) : null}
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}