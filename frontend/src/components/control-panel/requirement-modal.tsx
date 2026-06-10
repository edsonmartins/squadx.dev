"use client";

import { useEffect } from "react";
import { Controller, useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { requirementsApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { FormModal, FieldError } from "@/components/shared/form-modal";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";

const schema = z.object({
  type: z.enum(["ADDED", "MODIFIED", "REMOVED"]),
  title: z.string().min(1, "Título é obrigatório").max(255),
  description: z.string().optional(),
  scenarios: z
    .array(
      z.object({
        name: z.string().min(1, "Obrigatório"),
        when: z.string().min(1, "Obrigatório"),
        then: z.string().min(1, "Obrigatório"),
      })
    )
    .min(1, "Ao menos um cenário"),
});
type FormData = z.infer<typeof schema>;

const DEFAULTS: FormData = {
  type: "ADDED",
  title: "",
  description: "",
  scenarios: [{ name: "", when: "", then: "" }],
};

interface RequirementModalProps {
  open: boolean;
  onClose: () => void;
  changeId: number;
}

export function RequirementModal({ open, onClose, changeId }: RequirementModalProps) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema), defaultValues: DEFAULTS });
  const { fields, append, remove } = useFieldArray({ control, name: "scenarios" });

  useEffect(() => {
    if (open) reset(DEFAULTS);
  }, [open, reset]);

  const mutation = useMutation({
    mutationFn: (data: FormData) =>
      requirementsApi.create({
        change_id: changeId,
        type: data.type,
        title: data.title,
        description: data.description || undefined,
        scenarios: data.scenarios,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cp-requirements", changeId] });
      toast({ title: "Requisito criado" });
      onClose();
    },
    onError: () =>
      toast({ title: "Erro", description: "Verifique os campos (todo requisito exige ≥1 cenário).", variant: "destructive" }),
  });

  return (
    <FormModal
      open={open}
      onClose={onClose}
      title="Novo requisito"
      description="Todo requisito precisa de ao menos um cenário de aceite (QUANDO/ENTÃO)."
      onSubmit={handleSubmit((data) => mutation.mutate(data))}
      isSubmitting={mutation.isPending}
      submitLabel="Criar requisito"
      submittingLabel="Criando..."
      cancelLabel="Cancelar"
      contentClassName="max-h-[90vh] overflow-y-auto sm:max-w-[640px]"
    >
      <div className="grid grid-cols-3 gap-3">
        <div className="grid gap-2">
          <Label>Tipo</Label>
          <Controller
            control={control}
            name="type"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADDED">ADDED</SelectItem>
                  <SelectItem value="MODIFIED">MODIFIED</SelectItem>
                  <SelectItem value="REMOVED">REMOVED</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>
        <div className="col-span-2 grid gap-2">
          <Label htmlFor="title">Título</Label>
          <Input id="title" {...register("title")} />
          <FieldError message={errors.title?.message} />
        </div>
      </div>
      <div className="grid gap-2">
        <Label htmlFor="desc">Descrição</Label>
        <Textarea id="desc" {...register("description")} />
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label>Cenários</Label>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => append({ name: "", when: "", then: "" })}
          >
            <Plus className="mr-1 h-3 w-3" /> Cenário
          </Button>
        </div>
        {fields.map((f, i) => (
          <div key={f.id} className="space-y-2 rounded border p-2">
            <div className="flex items-center gap-2">
              <div className="flex-1">
                <Input placeholder="Nome do cenário" {...register(`scenarios.${i}.name`)} />
                <FieldError message={errors.scenarios?.[i]?.name?.message} />
              </div>
              {fields.length > 1 && (
                <Button type="button" size="icon" variant="ghost" onClick={() => remove(i)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              )}
            </div>
            <Input placeholder="QUANDO ..." {...register(`scenarios.${i}.when`)} />
            <FieldError message={errors.scenarios?.[i]?.when?.message} />
            <Input placeholder="ENTÃO ..." {...register(`scenarios.${i}.then`)} />
            <FieldError message={errors.scenarios?.[i]?.then?.message} />
          </div>
        ))}
      </div>
    </FormModal>
  );
}
