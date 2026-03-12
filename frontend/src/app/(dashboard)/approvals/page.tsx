"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { CheckCircle, XCircle, Clock, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import {
  approvalsApi,
  ApprovalResponse,
  ApprovalStatus,
  ApprovalType,
} from "@/lib/api";

const statusColors: Record<ApprovalStatus, string> = {
  PENDING: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
  APPROVED: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  REJECTED: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  EXPIRED: "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200",
  CANCELLED: "bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200",
};

const typeColors: Record<ApprovalType, string> = {
  COMMIT: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  DEPLOY: "bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200",
  DELETE: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  MERGE: "bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200",
  CONFIGURATION: "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200",
};

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function ApprovalsPage() {
  const [activeTab, setActiveTab] = useState<string>("PENDING");
  const [reviewTarget, setReviewTarget] = useState<ApprovalResponse | null>(null);
  const [reviewAction, setReviewAction] = useState<"approve" | "reject" | null>(null);
  const [reviewComment, setReviewComment] = useState("");
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const statusParam = activeTab === "ALL" ? undefined : (activeTab as ApprovalStatus);

  const { data: approvalsData, isLoading } = useQuery({
    queryKey: ["approvals", activeTab],
    queryFn: () =>
      activeTab === "PENDING"
        ? approvalsApi.getPending()
        : approvalsApi.list(statusParam),
  });

  const approvals = approvalsData?.content || [];

  const reviewMutation = useMutation({
    mutationFn: ({ id, approved, comment }: { id: number; approved: boolean; comment: string }) =>
      approvalsApi.review(id, { approved, review_comment: comment || undefined }),
    onMutate: async ({ id, approved }) => {
      await queryClient.cancelQueries({ queryKey: ["approvals"] });
      const previousData = queryClient.getQueryData(["approvals", activeTab]);
      queryClient.setQueryData(["approvals", activeTab], (old: typeof approvalsData) => {
        if (!old) return old;
        return {
          ...old,
          content: old.content.map((a: ApprovalResponse) =>
            a.id === id ? { ...a, status: approved ? "APPROVED" : "REJECTED" } : a
          ),
        };
      });
      return { previousData };
    },
    onError: (_err, _vars, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(["approvals", activeTab], context.previousData);
      }
      toast({
        title: "Error",
        description: "Failed to submit review. Please try again.",
        variant: "destructive",
      });
    },
    onSuccess: (_data, { approved }) => {
      queryClient.invalidateQueries({ queryKey: ["approvals"] });
      toast({
        title: approved ? "Approved" : "Rejected",
        description: `The approval has been ${approved ? "approved" : "rejected"} successfully.`,
      });
      closeReviewDialog();
    },
  });

  const openReviewDialog = (approval: ApprovalResponse, action: "approve" | "reject") => {
    setReviewTarget(approval);
    setReviewAction(action);
    setReviewComment("");
  };

  const closeReviewDialog = () => {
    setReviewTarget(null);
    setReviewAction(null);
    setReviewComment("");
  };

  const confirmReview = () => {
    if (!reviewTarget || !reviewAction) return;
    reviewMutation.mutate({
      id: reviewTarget.id,
      approved: reviewAction === "approve",
      comment: reviewComment,
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Approvals</h1>
        <p className="text-muted-foreground">
          Review and manage approval requests
        </p>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="PENDING">Pending</TabsTrigger>
          <TabsTrigger value="APPROVED">Approved</TabsTrigger>
          <TabsTrigger value="REJECTED">Rejected</TabsTrigger>
          <TabsTrigger value="ALL">All</TabsTrigger>
        </TabsList>

        <TabsContent value={activeTab} className="mt-4">
          {approvals.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-64 gap-4">
              <ShieldCheck className="h-12 w-12 text-muted-foreground" />
              <p className="text-muted-foreground">
                {activeTab === "PENDING"
                  ? "No pending approvals"
                  : `No ${activeTab.toLowerCase()} approvals`}
              </p>
            </div>
          ) : (
            <div className="grid gap-4">
              {approvals.map((approval) => (
                <Card key={approval.id}>
                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between">
                      <div className="space-y-1">
                        <CardTitle className="text-base">
                          {approval.title}
                        </CardTitle>
                        <p className="text-sm text-muted-foreground">
                          Task: {approval.task_title}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="secondary"
                          className={typeColors[approval.approval_type]}
                        >
                          {approval.approval_type}
                        </Badge>
                        <Badge
                          variant="secondary"
                          className={statusColors[approval.status]}
                        >
                          {approval.status}
                        </Badge>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4 text-sm text-muted-foreground">
                        <span>Requested by {approval.requested_by_name}</span>
                        <span className="flex items-center gap-1">
                          <Clock className="h-3.5 w-3.5" />
                          {formatDate(approval.created_at)}
                        </span>
                        {approval.description && (
                          <span className="max-w-md truncate">
                            {approval.description}
                          </span>
                        )}
                      </div>
                      {approval.status === "PENDING" && (
                        <div className="flex items-center gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            className="text-red-600 hover:text-red-700 hover:bg-red-50"
                            onClick={() => openReviewDialog(approval, "reject")}
                          >
                            <XCircle className="mr-1.5 h-4 w-4" />
                            Reject
                          </Button>
                          <Button
                            size="sm"
                            className="bg-green-600 hover:bg-green-700"
                            onClick={() => openReviewDialog(approval, "approve")}
                          >
                            <CheckCircle className="mr-1.5 h-4 w-4" />
                            Approve
                          </Button>
                        </div>
                      )}
                      {approval.status !== "PENDING" && approval.reviewer_name && (
                        <span className="text-sm text-muted-foreground">
                          Reviewed by {approval.reviewer_name}
                        </span>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* Review Confirmation Dialog */}
      <Dialog open={!!reviewTarget} onOpenChange={() => closeReviewDialog()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {reviewAction === "approve" ? "Approve" : "Reject"} Request
            </DialogTitle>
            <DialogDescription>
              {reviewAction === "approve"
                ? `Are you sure you want to approve "${reviewTarget?.title}"?`
                : `Are you sure you want to reject "${reviewTarget?.title}"?`}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="review-comment">Comment (optional)</Label>
            <Textarea
              id="review-comment"
              placeholder="Add a review comment..."
              value={reviewComment}
              onChange={(e) => setReviewComment(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeReviewDialog}>
              Cancel
            </Button>
            <Button
              onClick={confirmReview}
              disabled={reviewMutation.isPending}
              className={
                reviewAction === "approve"
                  ? "bg-green-600 hover:bg-green-700"
                  : "bg-red-600 hover:bg-red-700"
              }
            >
              {reviewMutation.isPending
                ? "Submitting..."
                : reviewAction === "approve"
                ? "Approve"
                : "Reject"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
