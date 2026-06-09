"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { controlPanelChangesApi, projectsApi } from "@/lib/api";
import { useOrganizationStore } from "@/stores/organization-store";
import { useControlPanelSocket } from "@/hooks/use-socket";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { WhereWeAreCard } from "@/components/control-panel/where-we-are-card";
import { ChangesList } from "@/components/control-panel/changes-list";
import { ChangeModal } from "@/components/control-panel/change-modal";

export default function ControlPanelPage() {
  const { selectedOrganization } = useOrganizationStore();
  const orgId = selectedOrganization?.id;

  const { data: projects } = useQuery({
    queryKey: ["projects", orgId],
    queryFn: () => projectsApi.list(orgId!),
    enabled: !!orgId,
  });

  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();
  const projectId = selectedProjectId ?? projects?.content?.[0]?.id;
  const [modalOpen, setModalOpen] = useState(false);

  useControlPanelSocket(projectId);

  const { data: whereWeAre } = useQuery({
    queryKey: ["cp-where-we-are", projectId],
    queryFn: () => controlPanelChangesApi.whereWeAre(projectId!),
    enabled: !!projectId,
  });

  const { data: changes } = useQuery({
    queryKey: ["cp-changes", projectId],
    queryFn: () => controlPanelChangesApi.listByProject(projectId!),
    enabled: !!projectId,
  });

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold font-heading">Control Panel</h1>
          <p className="text-sm text-muted-foreground">
            A spec governa o trabalho: requisitos geram tarefas, validadas pelo Pass 5.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select
            value={projectId ? String(projectId) : undefined}
            onValueChange={(v) => setSelectedProjectId(Number(v))}
          >
            <SelectTrigger className="w-[220px]">
              <SelectValue placeholder="Selecione um projeto" />
            </SelectTrigger>
            <SelectContent>
              {projects?.content?.map((p) => (
                <SelectItem key={p.id} value={String(p.id)}>
                  {p.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={() => setModalOpen(true)} disabled={!projectId}>
            <Plus className="mr-2 h-4 w-4" />
            Nova mudança
          </Button>
        </div>
      </div>

      {!projectId ? (
        <p className="text-sm text-muted-foreground">Selecione um projeto para ver o painel.</p>
      ) : (
        <>
          {whereWeAre && <WhereWeAreCard data={whereWeAre} />}
          <div>
            <h2 className="mb-3 text-lg font-semibold font-heading">Mudanças</h2>
            <ChangesList changes={changes?.content ?? []} />
          </div>
        </>
      )}

      {projectId && (
        <ChangeModal open={modalOpen} onClose={() => setModalOpen(false)} projectId={projectId} />
      )}
    </div>
  );
}
