"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Plus } from "lucide-react";
import { controlPanelChangesApi, pass5Api, requirementsApi, specTasksApi } from "@/lib/api";
import { CHANGE_PHASE_LABEL } from "@/lib/control-panel";
import { useControlPanelSocket } from "@/hooks/use-socket";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { RequirementList } from "@/components/control-panel/requirement-list";
import { RequirementModal } from "@/components/control-panel/requirement-modal";
import { SpecTaskBoard } from "@/components/control-panel/spec-task-board";
import { SpecTaskModal } from "@/components/control-panel/spec-task-modal";
import { Pass5Panel } from "@/components/control-panel/pass5-panel";
import { VersionHistory } from "@/components/control-panel/version-history";

export default function ChangeDetailPage() {
  const params = useParams();
  const changeId = Number(params.id);

  const { data: change } = useQuery({
    queryKey: ["cp-change", changeId],
    queryFn: () => controlPanelChangesApi.get(changeId),
  });

  useControlPanelSocket(change?.project_id);

  const { data: requirements } = useQuery({
    queryKey: ["cp-requirements", changeId],
    queryFn: () => requirementsApi.byChange(changeId),
  });

  const { data: tasks } = useQuery({
    queryKey: ["cp-tasks", changeId],
    queryFn: () => specTasksApi.byChange(changeId),
  });

  const { data: pass5Statuses } = useQuery({
    queryKey: ["cp-pass5-change", changeId],
    queryFn: () => pass5Api.byChange(changeId),
  });

  const [reqModalOpen, setReqModalOpen] = useState(false);
  const [taskModalOpen, setTaskModalOpen] = useState(false);

  return (
    <div className="space-y-6 p-6">
      <div>
        <Link
          href="/control-panel"
          className="inline-flex items-center text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-1 h-4 w-4" /> Control Panel
        </Link>
        <div className="mt-2 flex items-center gap-3">
          <h1 className="text-2xl font-bold font-heading">
            {change?.module || `Mudança #${changeId}`}
          </h1>
          {change && (
            <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs">
              {CHANGE_PHASE_LABEL[change.phase]}
            </span>
          )}
        </div>
      </div>

      <Tabs defaultValue="requirements">
        <TabsList>
          <TabsTrigger value="requirements">Requisitos</TabsTrigger>
          <TabsTrigger value="tasks">Tarefas</TabsTrigger>
          <TabsTrigger value="validation">Validação (Pass 5)</TabsTrigger>
          <TabsTrigger value="versions">Versões</TabsTrigger>
        </TabsList>

        <TabsContent value="requirements" className="space-y-3">
          <div className="flex justify-end">
            <Button size="sm" onClick={() => setReqModalOpen(true)}>
              <Plus className="mr-1 h-4 w-4" /> Novo requisito
            </Button>
          </div>
          <RequirementList requirements={requirements ?? []} />
        </TabsContent>

        <TabsContent value="tasks" className="space-y-3">
          <div className="flex justify-end">
            <Button size="sm" onClick={() => setTaskModalOpen(true)}>
              <Plus className="mr-1 h-4 w-4" /> Nova tarefa
            </Button>
          </div>
          <SpecTaskBoard tasks={tasks ?? []} />
        </TabsContent>

        <TabsContent value="validation" className="space-y-3">
          {(tasks ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma tarefa para validar.</p>
          ) : (
            <div className="grid gap-3 lg:grid-cols-2">
              {tasks!.map((task) => (
                <Pass5Panel
                  key={task.id}
                  taskId={task.id}
                  title={task.title}
                  changeId={changeId}
                  status={pass5Statuses?.find((s) => s.spec_task_id === task.id)}
                />
              ))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="versions">
          <VersionHistory changeId={changeId} />
        </TabsContent>
      </Tabs>

      <RequirementModal open={reqModalOpen} onClose={() => setReqModalOpen(false)} changeId={changeId} />
      <SpecTaskModal
        open={taskModalOpen}
        onClose={() => setTaskModalOpen(false)}
        changeId={changeId}
        requirements={requirements ?? []}
      />
    </div>
  );
}
