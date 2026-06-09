"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Plus } from "lucide-react";
import { harnessesApi } from "@/lib/api";
import { useOrganizationStore } from "@/stores/organization-store";
import { Button } from "@/components/ui/button";
import { HarnessList } from "@/components/control-panel/harness-list";
import { HarnessModal } from "@/components/control-panel/harness-modal";

export default function ConnectorsPage() {
  const { selectedOrganization } = useOrganizationStore();
  const orgId = selectedOrganization?.id;
  const [modalOpen, setModalOpen] = useState(false);

  const { data: harnesses } = useQuery({
    queryKey: ["cp-harnesses", orgId],
    queryFn: () => harnessesApi.list(orgId!),
    enabled: !!orgId,
  });

  return (
    <div className="space-y-6 p-6">
      <div>
        <Link
          href="/control-panel"
          className="inline-flex items-center text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-1 h-4 w-4" /> Control Panel
        </Link>
        <div className="mt-2 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold font-heading">Conectores</h1>
            <p className="text-sm text-muted-foreground">
              Harnesses de agentes e o modelo LLM de cada um.
            </p>
          </div>
          <Button onClick={() => setModalOpen(true)} disabled={!orgId}>
            <Plus className="mr-2 h-4 w-4" /> Registrar harness
          </Button>
        </div>
      </div>

      {!orgId ? (
        <p className="text-sm text-muted-foreground">Selecione uma organização.</p>
      ) : (
        <HarnessList harnesses={harnesses ?? []} />
      )}

      {orgId && <HarnessModal open={modalOpen} onClose={() => setModalOpen(false)} organizationId={orgId} />}
    </div>
  );
}
