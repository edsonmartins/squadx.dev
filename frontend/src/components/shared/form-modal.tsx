"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

interface FormModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  /** Resultado de handleSubmit(onSubmit) do react-hook-form. */
  onSubmit: (e: React.FormEvent<HTMLFormElement>) => void;
  isSubmitting?: boolean;
  submitLabel: string;
  submittingLabel?: string;
  cancelLabel?: string;
  /** Largura do conteúdo, ex.: "sm:max-w-[600px]". */
  contentClassName?: string;
  /** data-testid do DialogContent (usado pelos testes e2e). */
  contentTestId?: string;
  /** data-testid do botão de submit (usado pelos testes e2e). */
  submitTestId?: string;
  children: React.ReactNode;
}

/**
 * Shell padrão dos modais de formulário (Dialog + header + form + footer).
 * Os campos e a lógica de mutation continuam no modal específico.
 */
export function FormModal({
  open,
  onClose,
  title,
  description,
  onSubmit,
  isSubmitting = false,
  submitLabel,
  submittingLabel = "Saving...",
  cancelLabel = "Cancel",
  contentClassName,
  contentTestId,
  submitTestId,
  children,
}: FormModalProps) {
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent
        className={cn("sm:max-w-[500px]", contentClassName)}
        data-testid={contentTestId}
      >
        <DialogHeader>
          <DialogTitle className="font-heading">{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <form onSubmit={onSubmit}>
          <div className="grid gap-4 py-4">{children}</div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {cancelLabel}
            </Button>
            <Button type="submit" disabled={isSubmitting} data-testid={submitTestId}>
              {isSubmitting ? submittingLabel : submitLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Mensagem de erro de campo no padrão dos formulários. */
export function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p className="text-sm text-destructive">{message}</p>;
}
