package dev.squadx.dto.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRoleResponse {

    private Long id;
    private Long organizationId;
    private String name;
    private String description;
    private List<PermissionResponse> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}
