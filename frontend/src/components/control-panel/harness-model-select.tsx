"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { harnessesApi, HarnessResponse } from "@/lib/api";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";

export function HarnessModelSelect({ harness }: { harness: HarnessResponse }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const mutation = useMutation({
    mutationFn: (model: string) => harnessesApi.selectModel(harness.id, model),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-harnesses", harness.organization_id] });
      toast({ title: "Modelo selecionado" });
    },
    onError: () => toast({ title: "Modelo indisponível para este harness", variant: "destructive" }),
  });

  if (harness.models.length === 0) {
    return <span className="text-xs text-muted-foreground">sem modelos</span>;
  }

  return (
    <Select value={harness.model ?? undefined} onValueChange={(v) => mutation.mutate(v)}>
      <SelectTrigger className="w-[220px]">
        <SelectValue placeholder="Selecionar modelo" />
      </SelectTrigger>
      <SelectContent>
        {harness.models.map((m) => (
          <SelectItem key={m} value={m}>
            {m}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
