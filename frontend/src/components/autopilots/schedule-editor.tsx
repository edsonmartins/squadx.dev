"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const PRESETS: { value: string; label: string }[] = [
  { value: "0 9 * * *", label: "Every day at 9:00 AM" },
  { value: "0 9 * * 1-5", label: "Weekdays at 9:00 AM" },
  { value: "0 9 * * 1", label: "Every Monday at 9:00 AM" },
  { value: "0 * * * *", label: "Every hour" },
  { value: "*/15 * * * *", label: "Every 15 minutes" },
  { value: "0 9 1 * *", label: "First day of month at 9:00 AM" },
];

const CUSTOM = "__custom__";

/** Best-effort human-readable description of a cron expression. */
export function describeCron(cron: string): string {
  const preset = PRESETS.find((p) => p.value === cron);
  if (preset) return preset.label;
  return `Custom schedule: ${cron}`;
}

interface ScheduleEditorProps {
  value: string;
  onChange: (cron: string) => void;
}

export function ScheduleEditor({ value, onChange }: ScheduleEditorProps) {
  const isPreset = PRESETS.some((p) => p.value === value);
  const selectValue = isPreset ? value : CUSTOM;

  return (
    <div className="grid gap-2">
      <Label htmlFor="schedule">Schedule</Label>
      <Select
        value={selectValue}
        onValueChange={(v) => {
          if (v === CUSTOM) {
            // Keep the current value but switch to custom editing.
            if (isPreset) onChange("");
          } else {
            onChange(v);
          }
        }}
      >
        <SelectTrigger id="schedule">
          <SelectValue placeholder="Choose a schedule" />
        </SelectTrigger>
        <SelectContent>
          {PRESETS.map((p) => (
            <SelectItem key={p.value} value={p.value}>
              {p.label}
            </SelectItem>
          ))}
          <SelectItem value={CUSTOM}>Custom (cron expression)</SelectItem>
        </SelectContent>
      </Select>

      {!isPreset && (
        <Input
          aria-label="Cron expression"
          placeholder="0 9 * * 1-5"
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      )}

      <p className="text-xs text-muted-foreground">
        {value
          ? describeCron(value)
          : "Standard 5-field cron (minute hour day month weekday)."}
      </p>
    </div>
  );
}
