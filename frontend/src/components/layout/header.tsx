"use client";

import { useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Bell, Search, Plus, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const SECTION_LABELS: Record<string, string> = {
  "": "Dashboard",
  projects: "Projects",
  tasks: "Tasks",
  "control-panel": "Control Panel",
  squads: "Squads",
  approvals: "Approvals",
  calendar: "Calendar",
  live: "Live View",
  recordings: "Recordings",
  analytics: "Analytics",
  settings: "Settings",
};

export function Header() {
  const router = useRouter();
  const pathname = usePathname();
  const searchRef = useRef<HTMLInputElement>(null);

  const firstSegment = pathname.split("/")[1] ?? "";
  const sectionLabel = SECTION_LABELS[firstSegment] ?? "Dashboard";

  // ⌘K / Ctrl+K foca a busca
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <header className="topbar flex items-center gap-4 px-6">
      {/* Breadcrumb */}
      <nav aria-label="Breadcrumb" className="hidden items-center gap-1.5 text-[13px] sm:flex">
        <span className="text-muted-foreground">Workspace</span>
        <ChevronRight className="h-3.5 w-3.5 text-muted-foreground/60" aria-hidden="true" />
        <span className="font-heading font-semibold text-foreground">{sectionLabel}</span>
      </nav>

      {/* Search */}
      <div className="relative ml-auto w-full max-w-xs">
        <Search
          className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          ref={searchRef}
          type="search"
          placeholder="Search tasks, projects..."
          aria-label="Buscar tasks, projetos e squads"
          className="h-9 border-transparent bg-muted/60 pl-9 pr-12 transition-colors focus:border-border focus:bg-background"
        />
        <kbd
          className="pointer-events-none absolute right-2.5 top-1/2 hidden -translate-y-1/2 rounded border bg-background px-1.5 py-px font-mono text-[10px] text-muted-foreground md:block"
          aria-hidden="true"
        >
          ⌘K
        </kbd>
      </div>

      {/* Actions */}
      <div className="flex shrink-0 items-center gap-2">
        <Button
          size="sm"
          className="h-9 rounded-lg font-heading"
          onClick={() => router.push("/tasks")}
        >
          <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">New Task</span>
        </Button>

        <Button variant="ghost" size="icon" className="h-9 w-9" aria-label="Notificações">
          <Bell className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </header>
  );
}
