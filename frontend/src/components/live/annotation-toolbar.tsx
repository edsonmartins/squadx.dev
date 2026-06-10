"use client";

import {
  MousePointer2,
  Square,
  MoveUpRight,
  Pencil,
  Type,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  TooltipProvider,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { AnnotationTool } from "@/hooks/use-annotations";
import { ANNOTATION_PRESET_COLORS } from "@/lib/design/semantics";

const PRESET_COLORS = ANNOTATION_PRESET_COLORS;

const TOOLS: { tool: AnnotationTool; icon: typeof MousePointer2; label: string }[] = [
  { tool: "pointer", icon: MousePointer2, label: "Pointer" },
  { tool: "rectangle", icon: Square, label: "Rectangle" },
  { tool: "arrow", icon: MoveUpRight, label: "Arrow" },
  { tool: "freehand", icon: Pencil, label: "Freehand" },
  { tool: "text", icon: Type, label: "Text" },
];

interface AnnotationToolbarProps {
  activeTool: AnnotationTool;
  activeColor: string;
  onToolChange: (tool: AnnotationTool) => void;
  onColorChange: (color: string) => void;
  onClear: () => void;
}

export function AnnotationToolbar({
  activeTool,
  activeColor,
  onToolChange,
  onColorChange,
  onClear,
}: AnnotationToolbarProps) {
  return (
    <TooltipProvider>
      <div className="absolute top-4 left-4 z-20 flex items-center gap-1 rounded-lg bg-black/60 backdrop-blur-sm p-1">
        {/* Tool buttons */}
        {TOOLS.map(({ tool, icon: Icon, label }) => (
          <Tooltip key={tool}>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className={cn(
                  "h-8 w-8 text-white hover:bg-white/20",
                  activeTool === tool && "bg-white/25 ring-1 ring-white/50"
                )}
                onClick={() => onToolChange(tool)}
                aria-label={label}
              >
                <Icon className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent side="bottom">
              <p>{label}</p>
            </TooltipContent>
          </Tooltip>
        ))}

        {/* Separator */}
        <div className="mx-1 h-6 w-px bg-white/20" />

        {/* Color swatches */}
        {PRESET_COLORS.map((color) => (
          <Tooltip key={color}>
            <TooltipTrigger asChild>
              <button
                className={cn(
                  "h-6 w-6 rounded-full border-2 transition-transform",
                  activeColor === color
                    ? "border-white scale-110"
                    : "border-transparent hover:border-white/50"
                )}
                style={{ backgroundColor: color }}
                onClick={() => onColorChange(color)}
              />
            </TooltipTrigger>
            <TooltipContent side="bottom">
              <p>{color}</p>
            </TooltipContent>
          </Tooltip>
        ))}

        {/* Separator */}
        <div className="mx-1 h-6 w-px bg-white/20" />

        {/* Clear button */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-white hover:bg-danger/30"
              onClick={onClear}
              aria-label="Clear all annotations"
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent side="bottom">
            <p>Clear All</p>
          </TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );
}
