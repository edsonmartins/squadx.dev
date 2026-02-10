package dev.squadx.dto.supabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * DTO for Supabase live_sessions table.
 * Maps to the schema created by the Python client.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupabaseLiveSession {

    private String id;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("host_user_id")
    private String hostUserId;

    @JsonProperty("current_host_id")
    private String currentHostId;

    private String status;

    private String mode;

    @JsonProperty("join_code")
    private String joinCode;

    @JsonProperty("max_controllers")
    private Integer maxControllers;

    @JsonProperty("max_viewers")
    private Integer maxViewers;

    private Object settings;

    @JsonProperty("host_last_seen_at")
    private Instant hostLastSeenAt;

    @JsonProperty("host_status")
    private String hostStatus;

    @JsonProperty("backup_host_id")
    private String backupHostId;

    @JsonProperty("host_transferred_at")
    private Instant hostTransferredAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("ended_at")
    private Instant endedAt;
}
