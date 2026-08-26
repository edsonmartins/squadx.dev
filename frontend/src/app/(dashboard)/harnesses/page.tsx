"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Cable, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import { useOrganizationStore } from "@/stores/organization-store";
import { harnessesApi, HarnessRequest } from "@/lib/api";

export default function HarnessesPage() {
  const { selectedOrganization, organizations } = useOrganizationStore();
  const organizationId = selectedOrganization?.id || organizations[0]?.id;
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<HarnessRequest>({ key: "", name: "", vendor: "", models: [] });
  const [models, setModels] = useState("");
  const query = useQuery({
    queryKey: ["harnesses", organizationId],
    queryFn: () => harnessesApi.list(organizationId!),
    enabled: Boolean(organizationId),
  });
  const create = useMutation({
    mutationFn: () => harnessesApi.create(organizationId!, { ...form, models: models.split(",").map((m) => m.trim()).filter(Boolean) }),
    onSuccess: () => {
      setForm({ key: "", name: "", vendor: "", models: [] }); setModels("");
      queryClient.invalidateQueries({ queryKey: ["harnesses", organizationId] });
      toast({ title: "Harness added", description: "The connector is available for agent assignment." });
    },
    onError: () => toast({ title: "Unable to add harness", variant: "destructive" }),
  });
  const remove = useMutation({
    mutationFn: (id: number) => harnessesApi.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["harnesses", organizationId] }),
  });

  return (
    <div className="space-y-6">
      <div><h1 className="text-2xl font-bold">Harness connectors</h1><p className="text-muted-foreground">Configure the runtimes and models available to your agents.</p></div>
      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <Card><CardHeader><CardTitle className="flex items-center gap-2"><Plus className="h-4 w-4" />Add connector</CardTitle></CardHeader><CardContent className="space-y-4">
          {(["key", "name", "vendor"] as const).map((field) => <div key={field} className="space-y-1"><Label>{field[0].toUpperCase() + field.slice(1)}</Label><Input value={form[field] || ""} onChange={(e) => setForm({ ...form, [field]: e.target.value })} placeholder={field === "key" ? "openai" : field === "name" ? "OpenAI API" : "OpenAI"} /></div>)}
          <div className="space-y-1"><Label>Models (comma separated)</Label><Input value={models} onChange={(e) => setModels(e.target.value)} placeholder="gpt-4o, gpt-4o-mini" /></div>
          <Button className="w-full" disabled={!organizationId || !form.key || !form.name || !form.vendor || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Adding..." : "Add harness"}</Button>
        </CardContent></Card>
        <Card><CardHeader><CardTitle className="flex items-center gap-2"><Cable className="h-4 w-4" />Configured connectors</CardTitle></CardHeader><CardContent className="space-y-3">
          {query.isLoading && <p className="text-sm text-muted-foreground">Loading...</p>}
          {!query.isLoading && !query.data?.length && <p className="text-sm text-muted-foreground">No harnesses configured yet.</p>}
          {query.data?.map((h) => <div key={h.id} className="flex items-center justify-between rounded-lg border p-3"><div><p className="font-medium">{h.name} <span className="text-xs text-muted-foreground">({h.key})</span></p><p className="text-sm text-muted-foreground">{h.vendor} · {h.status}{h.models?.length ? ` · ${h.models.join(", ")}` : ""}</p></div><Button variant="ghost" size="icon" onClick={() => remove.mutate(h.id)} aria-label={`Delete ${h.name}`}><Trash2 className="h-4 w-4 text-destructive" /></Button></div>)}
        </CardContent></Card>
      </div>
    </div>
  );
}
