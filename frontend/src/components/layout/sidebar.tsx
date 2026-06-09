"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  FolderKanban,
  ListTodo,
  Users,
  Monitor,
  BarChart3,
  ShieldCheck,
  CalendarDays,
  Video,
  Zap,
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
import { useAuthStore } from "@/stores/auth-store";
import { useUIStore } from "@/stores/ui-store";

const navigation = [
  { name: "Dashboard", href: "/", icon: LayoutDashboard },
  { name: "Projects", href: "/projects", icon: FolderKanban },
  { name: "Tasks", href: "/tasks", icon: ListTodo },
  { name: "Squads", href: "/squads", icon: Users },
  { name: "Autopilots", href: "/autopilots", icon: Zap },
  { name: "Approvals", href: "/approvals", icon: ShieldCheck },
  { name: "Calendar", href: "/calendar", icon: CalendarDays },
  { name: "Live View", href: "/live", icon: Monitor },
  { name: "Recordings", href: "/recordings", icon: Video },
  { name: "Analytics", href: "/analytics", icon: BarChart3 },
];

const bottomNavigation = [
  { name: "Settings", href: "/settings", icon: Settings },
];

interface NavLinkProps {
  href: string;
  icon: React.ElementType;
  name: string;
  isActive: boolean;
  isCollapsed: boolean;
}

function NavLink({ href, icon: Icon, name, isActive, isCollapsed }: NavLinkProps) {
  const linkContent = (
    <Link
      href={href}
      className={cn(
        "sidebar-link font-medium",
        isActive && "active",
        isCollapsed && "justify-center px-2"
      )}
    >
      <Icon className="h-4 w-4 shrink-0" />
      {!isCollapsed && <span className="font-heading truncate">{name}</span>}
    </Link>
  );

  if (isCollapsed) {
    return (
      <Tooltip delayDuration={0}>
        <TooltipTrigger asChild>{linkContent}</TooltipTrigger>
        <TooltipContent side="right" className="font-heading">
          {name}
        </TooltipContent>
      </Tooltip>
    );
  }

  return linkContent;
}

export function Sidebar() {
  const pathname = usePathname();
  const { logout } = useAuthStore();
  const { sidebarCollapsed, toggleSidebar } = useUIStore();

  return (
    <TooltipProvider>
      <div
        className={cn(
          "sidebar flex h-full flex-col border-r bg-card transition-all duration-300 ease-in-out",
          sidebarCollapsed ? "w-[60px]" : "w-[220px]"
        )}
      >
        {/* Logo */}
        <div
          className={cn(
            "flex h-[52px] items-center border-b transition-all duration-300",
            sidebarCollapsed ? "justify-center px-2" : "gap-2.5 px-4"
          )}
        >
          <div className="h-7 w-7 rounded-lg bg-primary flex items-center justify-center shrink-0">
            <span className="text-xs font-bold text-primary-foreground">SX</span>
          </div>
          {!sidebarCollapsed && (
            <span className="text-base font-bold font-heading whitespace-nowrap overflow-hidden">
              SquadX.dev
            </span>
          )}
        </div>

        {/* Navigation */}
        <nav className="flex-1 space-y-0.5 p-2">
          {navigation.map((item) => {
            const isActive =
              pathname === item.href || pathname.startsWith(item.href + "/");
            return (
              <NavLink
                key={item.name}
                href={item.href}
                icon={item.icon}
                name={item.name}
                isActive={isActive}
                isCollapsed={sidebarCollapsed}
              />
            );
          })}
        </nav>

        {/* Bottom Navigation */}
        <div className="border-t p-2 space-y-0.5">
          {bottomNavigation.map((item) => {
            const isActive = pathname === item.href;
            return (
              <NavLink
                key={item.name}
                href={item.href}
                icon={item.icon}
                name={item.name}
                isActive={isActive}
                isCollapsed={sidebarCollapsed}
              />
            );
          })}

          {/* Logout button */}
          {sidebarCollapsed ? (
            <Tooltip delayDuration={0}>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="w-full justify-center px-2 h-9 text-muted-foreground hover:text-foreground hover:bg-muted"
                  onClick={logout}
                >
                  <LogOut className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="right" className="font-heading">
                Sign out
              </TooltipContent>
            </Tooltip>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              className="w-full justify-start gap-3 px-3 h-9 text-muted-foreground hover:text-foreground hover:bg-muted"
              onClick={logout}
            >
              <LogOut className="h-4 w-4" />
              <span className="font-heading">Sign out</span>
            </Button>
          )}

          {/* Collapse toggle */}
          <Button
            variant="ghost"
            size="sm"
            className={cn(
              "w-full h-9 text-muted-foreground hover:text-foreground hover:bg-muted mt-1",
              sidebarCollapsed ? "justify-center px-2" : "justify-start gap-3 px-3"
            )}
            onClick={toggleSidebar}
          >
            {sidebarCollapsed ? (
              <ChevronRight className="h-4 w-4" />
            ) : (
              <>
                <ChevronLeft className="h-4 w-4" />
                <span className="font-heading">Collapse</span>
              </>
            )}
          </Button>
        </div>
      </div>
    </TooltipProvider>
  );
}
