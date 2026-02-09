"use client";

import { Bell, Search, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/stores/auth-store";

export function Header() {
  const { user } = useAuthStore();

  return (
    <header className="topbar flex items-center justify-between px-6">
      {/* Search */}
      <div className="flex items-center gap-4">
        <div className="relative w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search tasks, projects..."
            className="pl-9 h-9 bg-muted/50 border-transparent focus:border-border focus:bg-background transition-colors"
          />
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-3">
        <Button size="sm" className="h-8 rounded-lg font-heading">
          <Plus className="mr-1.5 h-4 w-4" />
          New Task
        </Button>

        <Button variant="ghost" size="icon" className="relative h-8 w-8">
          <Bell className="h-4 w-4" />
          <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-destructive text-[10px] font-medium text-destructive-foreground">
            3
          </span>
        </Button>

        {/* Divider */}
        <div className="h-6 w-px bg-border" />

        {/* User */}
        <div className="flex items-center gap-2.5">
          <div className="h-7 w-7 rounded-full bg-primary flex items-center justify-center">
            <span className="text-xs font-medium text-primary-foreground">
              {user?.full_name?.charAt(0)?.toUpperCase() || "U"}
            </span>
          </div>
          <div className="hidden md:block">
            <p className="text-sm font-medium font-heading leading-tight">{user?.full_name}</p>
            <p className="text-xs text-muted-foreground leading-tight">{user?.email}</p>
          </div>
        </div>
      </div>
    </header>
  );
}
