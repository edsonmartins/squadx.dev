"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  LayoutDashboard,
  FolderKanban,
  ListTodo,
  ClipboardList,
  Users,
  Monitor,
  BarChart3,
  ShieldCheck,
  CalendarDays,
  Video,
  Settings,
  LogOut,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { approvalsApi, liveViewApi } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { useUIStore } from "@/stores/ui-store";

interface NavItem {
  name: string;
  href: string;
  icon: React.ElementType;
  badge?: "approvals" | "live";
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const navigationGroups: NavGroup[] = [
  {
    label: "Workspace",
    items: [
      { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
      { name: "Projects", href: "/projects", icon: FolderKanban },
      { name: "Tasks", href: "/tasks", icon: ListTodo },
      { name: "Control Panel", href: "/control-panel", icon: ClipboardList },
      { name: "Squads", href: "/squads", icon: Users },
    ],
  },
  {
    label: "Operação",
    items: [
      { name: "Approvals", href: "/approvals", icon: ShieldCheck, badge: "approvals" },
      { name: "Live View", href: "/live", icon: Monitor, badge: "live" },
      { name: "Recordings", href: "/recordings", icon: Video },
      { name: "Calendar", href: "/calendar", icon: CalendarDays },
    ],
  },
  {
    label: "Insights",
    items: [{ name: "Analytics", href: "/analytics", icon: BarChart3 }],
  },
];

interface NavLinkProps {
  item: NavItem;
  isActive: boolean;
  isCollapsed: boolean;
  pendingApprovals: number;
  hasLiveSessions: boolean;
}

function NavLink({ item, isActive, isCollapsed, pendingApprovals, hasLiveSessions }: NavLinkProps) {
  const Icon = item.icon;
  const showApprovalsBadge = item.badge === "approvals" && pendingApprovals > 0;
  const showLiveDot = item.badge === "live" && hasLiveSessions;

  const linkContent = (
    <Link
      href={item.href}
      className={cn(
        "sidebar-link font-medium",
        isActive && "active",
        isCollapsed && "justify-center px-2"
      )}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      {!isCollapsed && <span className="truncate">{item.name}</span>}
      {!isCollapsed && showApprovalsBadge && (
        <span className="sidebar-count alert" role="status" aria-label={`${pendingApprovals} aprovações pendentes`}>
          {pendingApprovals}
        </span>
      )}
      {!isCollapsed && showLiveDot && (
        <span
          className="live-dot ml-auto h-[7px] w-[7px] shrink-0"
          role="status"
          aria-label="Sessões ao vivo ativas"
        />
      )}
      {isCollapsed && (showApprovalsBadge || showLiveDot) && (
        <span
          className={cn(
            "absolute right-1.5 top-1.5 h-[7px] w-[7px] rounded-full",
            showLiveDot ? "live-dot" : "bg-warn"
          )}
          aria-hidden="true"
        />
      )}
    </Link>
  );

  if (isCollapsed) {
    return (
      <Tooltip delayDuration={0}>
        <TooltipTrigger asChild>{linkContent}</TooltipTrigger>
        <TooltipContent side="right" className="font-heading">
          {item.name}
          {showApprovalsBadge && ` (${pendingApprovals})`}
        </TooltipContent>
      </Tooltip>
    );
  }

  return linkContent;
}

export function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuthStore();
  const { sidebarCollapsed, toggleSidebar } = useUIStore();

  // Badges operacionais — falham em silêncio para nunca quebrar a navegação
  const { data: pendingApprovalsData } = useQuery({
    queryKey: ["sidebar-pending-approvals"],
    queryFn: () => approvalsApi.getPending(),
    refetchInterval: 30000,
    retry: false,
  });

  const { data: liveSessions } = useQuery({
    queryKey: ["sidebar-live-sessions"],
    queryFn: () => liveViewApi.supabase.getActive(),
    refetchInterval: 30000,
    retry: false,
  });

  const pendingApprovals = pendingApprovalsData?.total_elements ?? 0;
  const hasLiveSessions = (liveSessions?.length ?? 0) > 0;

  const initials =
    user?.full_name
      ?.split(" ")
      .map((part) => part.charAt(0))
      .slice(0, 2)
      .join("")
      .toUpperCase() || "U";

  return (
    <TooltipProvider>
      <div
        className={cn(
          "sidebar flex h-full flex-col transition-all duration-300 ease-in-out",
          sidebarCollapsed ? "w-[60px]" : "w-[236px]"
        )}
      >
        {/* Logo */}
        <div
          className={cn(
            "flex h-[56px] shrink-0 items-center border-b border-[hsl(var(--sidebar-border))] transition-all duration-300",
            sidebarCollapsed ? "justify-center px-2" : "gap-2.5 px-4"
          )}
        >
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-[hsl(260_70%_56%)] shadow-[0_2px_8px_hsl(var(--primary)/0.45)]">
            <span className="font-heading text-xs font-extrabold text-white">SX</span>
          </div>
          {!sidebarCollapsed && (
            <span className="overflow-hidden whitespace-nowrap font-heading text-[15px] font-bold text-white">
              SquadX<span className="text-[hsl(var(--sidebar-label))]">.dev</span>
            </span>
          )}
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto p-2" aria-label="Navegação principal">
          {navigationGroups.map((group, groupIndex) => (
            <div key={group.label} className={cn(groupIndex > 0 && "mt-4")}>
              {!sidebarCollapsed ? (
                <div className="sidebar-group-label">{group.label}</div>
              ) : (
                groupIndex > 0 && (
                  <div className="mx-2 mb-2 border-t border-[hsl(var(--sidebar-border))]" />
                )
              )}
              <div className="space-y-0.5">
                {group.items.map((item) => {
                  const isActive =
                    pathname === item.href ||
                    (item.href !== "/" && pathname.startsWith(item.href + "/"));
                  return (
                    <NavLink
                      key={item.name}
                      item={item}
                      isActive={isActive}
                      isCollapsed={sidebarCollapsed}
                      pendingApprovals={pendingApprovals}
                      hasLiveSessions={hasLiveSessions}
                    />
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        {/* Footer: settings + user + collapse */}
        <div className="shrink-0 space-y-0.5 border-t border-[hsl(var(--sidebar-border))] p-2">
          <NavLink
            item={{ name: "Settings", href: "/settings", icon: Settings }}
            isActive={pathname === "/settings"}
            isCollapsed={sidebarCollapsed}
            pendingApprovals={0}
            hasLiveSessions={false}
          />

          {/* User card */}
          {!sidebarCollapsed ? (
            <div className="flex items-center gap-2.5 rounded-lg px-2.5 py-2">
              <div className="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-[hsl(260_70%_56%)]">
                <span className="font-heading text-[11px] font-bold text-white">{initials}</span>
              </div>
              <div className="min-w-0 flex-1 leading-tight">
                <p className="truncate text-[13px] font-semibold text-white">{user?.full_name}</p>
                <p className="truncate text-[11px] text-[hsl(var(--sidebar-label))]">{user?.email}</p>
              </div>
              <Tooltip delayDuration={0}>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7 shrink-0 text-[hsl(var(--sidebar-fg))] hover:bg-[hsl(var(--sidebar-hover))] hover:text-white"
                    onClick={logout}
                    aria-label="Sair"
                  >
                    <LogOut className="h-3.5 w-3.5" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="right" className="font-heading">
                  Sign out
                </TooltipContent>
              </Tooltip>
            </div>
          ) : (
            <>
              <Tooltip delayDuration={0}>
                <TooltipTrigger asChild>
                  <div className="flex justify-center py-1.5">
                    <div className="flex h-[30px] w-[30px] items-center justify-center rounded-full bg-gradient-to-br from-primary to-[hsl(260_70%_56%)]">
                      <span className="font-heading text-[11px] font-bold text-white">{initials}</span>
                    </div>
                  </div>
                </TooltipTrigger>
                <TooltipContent side="right" className="font-heading">
                  {user?.full_name}
                  {user?.email ? ` · ${user.email}` : ""}
                </TooltipContent>
              </Tooltip>
              <Tooltip delayDuration={0}>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-9 w-full justify-center px-2 text-[hsl(var(--sidebar-fg))] hover:bg-[hsl(var(--sidebar-hover))] hover:text-white"
                    onClick={logout}
                    aria-label="Sair"
                  >
                    <LogOut className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="right" className="font-heading">
                  Sign out
                </TooltipContent>
              </Tooltip>
            </>
          )}

          {/* Collapse toggle */}
          <Button
            variant="ghost"
            size="sm"
            className={cn(
              "h-8 w-full text-[hsl(var(--sidebar-label))] hover:bg-[hsl(var(--sidebar-hover))] hover:text-white",
              sidebarCollapsed ? "justify-center px-2" : "justify-start gap-2.5 px-2.5"
            )}
            onClick={toggleSidebar}
            aria-label={sidebarCollapsed ? "Expandir menu" : "Recolher menu"}
          >
            {sidebarCollapsed ? (
              <ChevronRight className="h-4 w-4" />
            ) : (
              <>
                <ChevronLeft className="h-4 w-4" />
                <span className="text-xs">Collapse</span>
              </>
            )}
          </Button>
        </div>
      </div>
    </TooltipProvider>
  );
}
