import { SpecTaskStatus } from "@/lib/api";
import { SPEC_TASK_STATUS_BADGE, SPEC_TASK_STATUS_LABEL } from "@/lib/control-panel";
import { cn } from "@/lib/utils";

export function StatusBadge({ status, className }: { status: SpecTaskStatus; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        SPEC_TASK_STATUS_BADGE[status],
        className
      )}
    >
      {SPEC_TASK_STATUS_LABEL[status]}
    </span>
  );
}
