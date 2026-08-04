"use client";

import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";

import LiveViewEmbed from "@/components/live/LiveViewEmbed";
import { Button } from "@/components/ui/button";
import { liveViewApi } from "@/lib/api";

export default function LiveStreamPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = use(params);
  const router = useRouter();
  const { data: session, isLoading, error } = useQuery({
    queryKey: ["live-session", code],
    queryFn: () => liveViewApi.getByCode(code),
    retry: false,
  });

  if (isLoading) {
    return <div className="flex h-[calc(100vh-4rem)] items-center justify-center">Carregando sessão…</div>;
  }

  const externalCode = session?.external_join_code;
  if (error || !externalCode) {
    return (
      <div className="flex h-[calc(100vh-4rem)] flex-col items-center justify-center gap-4">
        <h1 className="text-2xl font-bold">Sessão indisponível</h1>
        <p className="text-muted-foreground">
          A sessão não existe, terminou ou ainda não foi publicada no SquadX Live.
        </p>
        <Button onClick={() => router.push("/live")}>
          <ArrowLeft className="mr-2 h-4 w-4" /> Voltar
        </Button>
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-4rem)] min-h-[32rem] bg-black">
      <LiveViewEmbed sessionCode={externalCode} className="h-full" />
    </div>
  );
}
