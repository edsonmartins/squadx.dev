"use client";

import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Save, User, Bell, Shield, Palette, Key, Brain, Search, Trash2 } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useToast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/auth-store";
import { useOrganizationStore } from "@/stores/organization-store";
import {
  api,
  memoryApi,
  preferencesApi,
  type CreateMemorySkillRequest,
  type LiveViewQuality,
  type UpdateUserPreferencesRequest,
} from "@/lib/api";

export default function SettingsPage() {
  const { user, setUser } = useAuthStore();
  const { selectedOrganization, organizations, fetchOrganizations } = useOrganizationStore();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // Profile state
  const [fullName, setFullName] = useState(user?.full_name || "");
  const [email, setEmail] = useState(user?.email || "");

  // Password state
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  // Notification preferences
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [pushNotifications, setPushNotifications] = useState(true);
  const [executionAlerts, setExecutionAlerts] = useState(true);
  const [liveSessionAlerts, setLiveSessionAlerts] = useState(true);

  // Live View settings
  const [autoStartLive, setAutoStartLive] = useState(true);
  const [defaultQuality, setDefaultQuality] = useState<LiveViewQuality>("HD");
  const [maxViewers, setMaxViewers] = useState("5");
  const [memoryQuery, setMemoryQuery] = useState("");
  const [skillTitle, setSkillTitle] = useState("");
  const [skillSummary, setSkillSummary] = useState("");
  const [skillContent, setSkillContent] = useState("");
  const [skillSteps, setSkillSteps] = useState("");

  const organizationId = selectedOrganization?.id || organizations[0]?.id;

  useQuery({
    queryKey: ["organizations", "settings-bootstrap"],
    queryFn: async () => {
      await fetchOrganizations();
      return true;
    },
    enabled: organizations.length === 0,
    staleTime: 60_000,
  });

  const memoryPolicyQuery = useQuery({
    queryKey: ["memory", "policy"],
    queryFn: () => memoryApi.getPolicy(),
  });

  // Per-user preferences (notifications + live view), persisted server-side.
  const preferencesQuery = useQuery({
    queryKey: ["preferences", "me"],
    queryFn: () => preferencesApi.get(),
  });

  // Seed the local form state once the persisted preferences load (and whenever they
  // change server-side), so the toggles reflect what's actually stored.
  const preferences = preferencesQuery.data;
  useEffect(() => {
    if (!preferences) return;
    setEmailNotifications(preferences.email_notifications);
    setPushNotifications(preferences.push_notifications);
    setExecutionAlerts(preferences.execution_alerts);
    setLiveSessionAlerts(preferences.live_session_alerts);
    setAutoStartLive(preferences.auto_start_live);
    setDefaultQuality(preferences.default_quality);
    setMaxViewers(String(preferences.max_viewers));
  }, [preferences]);

  // Full-replace PUT: the backend requires every field, so both the Notifications and
  // Live View "Save" buttons submit the complete current state, not a partial patch.
  const buildPreferencesPayload = (): UpdateUserPreferencesRequest => ({
    email_notifications: emailNotifications,
    push_notifications: pushNotifications,
    execution_alerts: executionAlerts,
    live_session_alerts: liveSessionAlerts,
    auto_start_live: autoStartLive,
    default_quality: defaultQuality,
    max_viewers: Number(maxViewers) || 5,
  });

  const updatePreferencesMutation = useMutation({
    mutationFn: (data: UpdateUserPreferencesRequest) => preferencesApi.update(data),
    onSuccess: (saved) => {
      queryClient.setQueryData(["preferences", "me"], saved);
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to save preferences. Please try again.",
        variant: "destructive",
      });
    },
  });

  const memorySkillsQuery = useQuery({
    queryKey: ["memory", "skills", organizationId],
    queryFn: () => memoryApi.listSkills({ organizationId: organizationId!, limit: 12 }),
    enabled: Boolean(organizationId),
  });

  const memoryHistoryQuery = useQuery({
    queryKey: ["memory", "history", organizationId, memoryQuery],
    queryFn: () => memoryApi.searchHistory({
      organizationId: organizationId!,
      query: memoryQuery || "execution memory history",
      limit: 8,
    }),
    enabled: Boolean(organizationId),
  });

  // Profile update mutation
  const updateProfileMutation = useMutation({
    mutationFn: (data: { full_name: string }) =>
      api.put("/api/v1/auth/me", data),
    onSuccess: () => {
      if (user) {
        setUser({ ...user, full_name: fullName });
      }
      queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
      toast({
        title: "Profile updated",
        description: "Your profile has been updated successfully.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to update profile. Please try again.",
        variant: "destructive",
      });
    },
  });

  // Password change mutation
  const changePasswordMutation = useMutation({
    mutationFn: (data: { current_password: string; new_password: string }) =>
      api.post("/api/v1/auth/me/change-password", data),
    onSuccess: () => {
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      toast({
        title: "Password changed",
        description: "Your password has been updated successfully.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to change password. Check your current password.",
        variant: "destructive",
      });
    },
  });

  const createSkillMutation = useMutation({
    mutationFn: (data: CreateMemorySkillRequest) => memoryApi.createSkill(data),
    onSuccess: () => {
      setSkillTitle("");
      setSkillSummary("");
      setSkillContent("");
      setSkillSteps("");
      queryClient.invalidateQueries({ queryKey: ["memory", "skills", organizationId] });
      queryClient.invalidateQueries({ queryKey: ["memory", "history", organizationId] });
      toast({
        title: "Skill created",
        description: "The procedural memory is now available for future executions.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to create skill.",
        variant: "destructive",
      });
    },
  });

  const deleteSkillMutation = useMutation({
    mutationFn: (memoryId: string) => memoryApi.deleteSkill(memoryId, organizationId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["memory", "skills", organizationId] });
      queryClient.invalidateQueries({ queryKey: ["memory", "history", organizationId] });
      toast({
        title: "Skill deleted",
        description: "The procedural memory has been removed.",
      });
    },
    onError: () => {
      toast({
        title: "Error",
        description: "Failed to delete skill.",
        variant: "destructive",
      });
    },
  });

  const handleSaveProfile = () => {
    updateProfileMutation.mutate({ full_name: fullName });
  };

  const handleChangePassword = () => {
    if (newPassword !== confirmPassword) {
      toast({
        title: "Error",
        description: "New passwords do not match.",
        variant: "destructive",
      });
      return;
    }
    if (newPassword.length < 8) {
      toast({
        title: "Error",
        description: "Password must be at least 8 characters.",
        variant: "destructive",
      });
      return;
    }
    changePasswordMutation.mutate({
      current_password: currentPassword,
      new_password: newPassword,
    });
  };

  const handleSaveNotifications = () => {
    updatePreferencesMutation.mutate(buildPreferencesPayload(), {
      onSuccess: () =>
        toast({
          title: "Notifications updated",
          description: "Your notification preferences have been saved.",
        }),
    });
  };

  const handleSaveLiveView = () => {
    updatePreferencesMutation.mutate(buildPreferencesPayload(), {
      onSuccess: () =>
        toast({
          title: "Live View settings updated",
          description: "Your Live View preferences have been saved.",
        }),
    });
  };

  const handleCreateSkill = () => {
    if (!organizationId || !skillTitle.trim() || !skillSummary.trim()) {
      toast({
        title: "Error",
        description: "Organization, title and summary are required.",
        variant: "destructive",
      });
      return;
    }

    createSkillMutation.mutate({
      organization_id: organizationId,
      title: skillTitle.trim(),
      summary: skillSummary.trim(),
      content: skillContent.trim() || undefined,
      steps: skillSteps
        .split("\n")
        .map((step) => step.trim())
        .filter(Boolean),
    });
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-muted-foreground">
          Manage your account settings and preferences
        </p>
      </div>

      <Tabs defaultValue="profile" className="space-y-4">
        <TabsList>
          <TabsTrigger value="profile" className="gap-2">
            <User className="h-4 w-4" />
            Profile
          </TabsTrigger>
          <TabsTrigger value="notifications" className="gap-2">
            <Bell className="h-4 w-4" />
            Notifications
          </TabsTrigger>
          <TabsTrigger value="live-view" className="gap-2">
            <Palette className="h-4 w-4" />
            Live View
          </TabsTrigger>
          <TabsTrigger value="api-keys" className="gap-2">
            <Key className="h-4 w-4" />
            API Keys
          </TabsTrigger>
          <TabsTrigger value="memory" className="gap-2">
            <Brain className="h-4 w-4" />
            Memory
          </TabsTrigger>
        </TabsList>

        {/* Profile Tab */}
        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>Profile Information</CardTitle>
              <CardDescription>
                Update your personal information and email address.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-2">
                <Label htmlFor="fullName">Full Name</Label>
                <Input
                  id="fullName"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="John Doe"
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="john@example.com"
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="role">Role</Label>
                <Input
                  id="role"
                  value={user?.role || "USER"}
                  disabled
                  className="bg-muted"
                />
              </div>

              <Button onClick={handleSaveProfile} disabled={updateProfileMutation.isPending}>
                <Save className="mr-2 h-4 w-4" />
                {updateProfileMutation.isPending ? "Saving..." : "Save Changes"}
              </Button>
            </CardContent>
          </Card>

          <Card className="mt-4">
            <CardHeader>
              <CardTitle>Change Password</CardTitle>
              <CardDescription>
                Update your password to keep your account secure.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-2">
                <Label htmlFor="currentPassword">Current Password</Label>
                <Input
                  id="currentPassword"
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="newPassword">New Password</Label>
                <Input
                  id="newPassword"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="confirmPassword">Confirm New Password</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                />
              </div>

              <Button
                variant="outline"
                onClick={handleChangePassword}
                disabled={changePasswordMutation.isPending}
              >
                {changePasswordMutation.isPending ? "Updating..." : "Update Password"}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Notifications Tab */}
        <TabsContent value="notifications">
          <Card>
            <CardHeader>
              <CardTitle>Notification Preferences</CardTitle>
              <CardDescription>
                Choose what notifications you want to receive.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Email Notifications</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications via email
                  </p>
                </div>
                <Switch
                  checked={emailNotifications}
                  onCheckedChange={setEmailNotifications}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Push Notifications</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive browser push notifications
                  </p>
                </div>
                <Switch
                  checked={pushNotifications}
                  onCheckedChange={setPushNotifications}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Execution Alerts</Label>
                  <p className="text-sm text-muted-foreground">
                    Notify when executions complete or fail
                  </p>
                </div>
                <Switch
                  checked={executionAlerts}
                  onCheckedChange={setExecutionAlerts}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Live Session Alerts</Label>
                  <p className="text-sm text-muted-foreground">
                    Notify when someone joins your live session
                  </p>
                </div>
                <Switch
                  checked={liveSessionAlerts}
                  onCheckedChange={setLiveSessionAlerts}
                />
              </div>

              <Button
                onClick={handleSaveNotifications}
                disabled={updatePreferencesMutation.isPending || preferencesQuery.isLoading}
              >
                <Save className="mr-2 h-4 w-4" />
                {updatePreferencesMutation.isPending ? "Saving..." : "Save Preferences"}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Live View Tab */}
        <TabsContent value="live-view">
          <Card>
            <CardHeader>
              <CardTitle>Live View Settings</CardTitle>
              <CardDescription>
                Configure your Live View experience.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Auto-start Live View</Label>
                  <p className="text-sm text-muted-foreground">
                    Automatically start live session when execution begins
                  </p>
                </div>
                <Switch
                  checked={autoStartLive}
                  onCheckedChange={setAutoStartLive}
                />
              </div>

              <div className="grid gap-2">
                <Label>Default Quality</Label>
                <Select
                  value={defaultQuality}
                  onValueChange={(v) => setDefaultQuality(v as LiveViewQuality)}
                >
                  <SelectTrigger className="w-[200px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="AUTO">Auto (adapt to connection)</SelectItem>
                    <SelectItem value="HD">HD (1280x720)</SelectItem>
                    <SelectItem value="SD">SD (854x480)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label>Max Viewers per Session</Label>
                <Select value={maxViewers} onValueChange={setMaxViewers}>
                  <SelectTrigger className="w-[200px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="3">3 viewers</SelectItem>
                    <SelectItem value="5">5 viewers</SelectItem>
                    <SelectItem value="10">10 viewers</SelectItem>
                    <SelectItem value="20">20 viewers</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Button
                onClick={handleSaveLiveView}
                disabled={updatePreferencesMutation.isPending || preferencesQuery.isLoading}
              >
                <Save className="mr-2 h-4 w-4" />
                {updatePreferencesMutation.isPending ? "Saving..." : "Save Settings"}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        {/* API Keys Tab */}
        <TabsContent value="api-keys">
          <Card>
            <CardHeader>
              <CardTitle>API Keys</CardTitle>
              <CardDescription>
                Where your LLM provider keys live and how the agents use them.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border p-4 bg-muted/50">
                <div className="flex items-start gap-2">
                  <Shield className="h-5 w-5 text-muted-foreground mt-0.5" />
                  <div className="space-y-2">
                    <p className="text-sm font-medium">Keys stay on your machine</p>
                    <p className="text-sm text-muted-foreground">
                      Provider API keys (OpenAI, Anthropic, …) are configured on the
                      SquadX client daemon and injected into the sandbox environment when
                      an agent runs. They are never sent to or stored on SquadX servers,
                      so there is nothing to enter here.
                    </p>
                    <p className="text-sm text-muted-foreground">
                      Set them in the client&apos;s environment or config file — for example{" "}
                      <code className="rounded bg-background px-1 py-0.5 text-xs">
                        ANTHROPIC_API_KEY
                      </code>{" "}
                      and{" "}
                      <code className="rounded bg-background px-1 py-0.5 text-xs">
                        OPENAI_API_KEY
                      </code>
                      .
                    </p>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="memory" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Memory Policy</CardTitle>
              <CardDescription>
                Inspect the active BrainSentry policy and scope used by agents.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 md:grid-cols-2">
              <div className="rounded-lg border p-4">
                <p className="text-sm text-muted-foreground">Scope</p>
                <p className="mt-1 font-medium">{memoryPolicyQuery.data?.memoryScope || "adaptive"}</p>
              </div>
              <div className="rounded-lg border p-4">
                <p className="text-sm text-muted-foreground">Tenant Mode</p>
                <p className="mt-1 font-medium">{memoryPolicyQuery.data?.tenantMode || "unknown"}</p>
              </div>
              <div className="rounded-lg border p-4">
                <p className="text-sm text-muted-foreground">Procedural Memory</p>
                <p className="mt-1 font-medium">
                  {memoryPolicyQuery.data?.proceduralMemoryEnabled ? "Enabled" : "Disabled"}
                </p>
              </div>
              <div className="rounded-lg border p-4">
                <p className="text-sm text-muted-foreground">Procedural Limit</p>
                <p className="mt-1 font-medium">{memoryPolicyQuery.data?.proceduralLimit || 0}</p>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Managed Skills</CardTitle>
              <CardDescription>
                Procedural memories exposed as reusable, administrable product artifacts.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="grid gap-2">
                  <Label htmlFor="skillTitle">Title</Label>
                  <Input id="skillTitle" value={skillTitle} onChange={(e) => setSkillTitle(e.target.value)} placeholder="Review migration failures" />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="skillSummary">Summary</Label>
                  <Input id="skillSummary" value={skillSummary} onChange={(e) => setSkillSummary(e.target.value)} placeholder="How the agent should approach this pattern" />
                </div>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="skillContent">Guidance</Label>
                <Input id="skillContent" value={skillContent} onChange={(e) => setSkillContent(e.target.value)} placeholder="Extra context, caveats, anti-patterns..." />
              </div>

              <div className="grid gap-2">
                <Label htmlFor="skillSteps">Steps</Label>
                <textarea
                  id="skillSteps"
                  value={skillSteps}
                  onChange={(e) => setSkillSteps(e.target.value)}
                  placeholder={"One step per line\nInspect failing migration\nCheck latest execution logs"}
                  className="min-h-28 rounded-md border border-input bg-background px-3 py-2 text-sm"
                />
              </div>

              <Button onClick={handleCreateSkill} disabled={createSkillMutation.isPending || !organizationId}>
                <Save className="mr-2 h-4 w-4" />
                {createSkillMutation.isPending ? "Saving..." : "Create Skill"}
              </Button>

              <div className="space-y-3">
                {(memorySkillsQuery.data || []).map((skill) => (
                  <div key={skill.id} className="rounded-lg border p-4" data-testid={`memory-skill-${skill.id}`}>
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-2">
                        <div className="flex items-center gap-2">
                          <p className="font-medium">{skill.summary || skill.content}</p>
                          {skill.antipattern ? (
                            <span className="rounded bg-orange-100 px-2 py-0.5 text-xs text-orange-700">Antipattern</span>
                          ) : (
                            <span className="rounded bg-green-100 px-2 py-0.5 text-xs text-green-700">Pattern</span>
                          )}
                        </div>
                        <p className="text-sm text-muted-foreground">{skill.content}</p>
                        {(skill.steps || []).length > 0 && (
                          <div className="text-sm text-muted-foreground">
                            {(skill.steps || []).map((step, index) => (
                              <p key={`${skill.id}-${index}`}>{index + 1}. {step}</p>
                            ))}
                          </div>
                        )}
                      </div>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => deleteSkillMutation.mutate(skill.id)}
                        disabled={deleteSkillMutation.isPending}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}

                {memorySkillsQuery.data?.length === 0 && (
                  <p className="text-sm text-muted-foreground">No managed procedural skills yet for this organization.</p>
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Historical Search</CardTitle>
              <CardDescription>
                Search memories, procedures and execution summaries by organization.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex gap-2">
                <Input
                  value={memoryQuery}
                  onChange={(e) => setMemoryQuery(e.target.value)}
                  placeholder="Search bugs, patterns, tasks or execution summaries..."
                  data-testid="memory-history-query"
                />
                <Button
                  variant="outline"
                  onClick={() => queryClient.invalidateQueries({ queryKey: ["memory", "history", organizationId] })}
                >
                  <Search className="mr-2 h-4 w-4" />
                  Search
                </Button>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-3">
                  <p className="text-sm font-medium">Memories</p>
                  {(memoryHistoryQuery.data?.memories || []).map((memory) => (
                    <div key={memory.id} className="rounded-lg border p-3">
                      <p className="text-sm font-medium">{memory.summary || memory.content}</p>
                      <p className="mt-1 text-xs text-muted-foreground">{memory.category} · {memory.memory_type}</p>
                    </div>
                  ))}
                </div>

                <div className="space-y-3">
                  <p className="text-sm font-medium">Execution History</p>
                  {(memoryHistoryQuery.data?.executions || []).map((execution) => (
                    <div key={execution.execution_id} className="rounded-lg border p-3">
                      <p className="text-sm font-medium">{execution.task_title}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Execution #{execution.execution_id} · {execution.status} · {execution.agent_name || "No agent"}
                      </p>
                      {execution.summary && (
                        <p className="mt-2 text-sm text-muted-foreground">{execution.summary}</p>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
