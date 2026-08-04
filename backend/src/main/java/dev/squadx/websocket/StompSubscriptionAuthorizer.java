package dev.squadx.websocket;

import dev.squadx.model.User;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.ProjectRepository;
import dev.squadx.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Enforces per-destination authorization on STOMP SUBSCRIBE frames.
 *
 * <p>The broker exposes tenant-scoped broadcast topics — {@code /topic/organizations/{id}},
 * {@code /topic/projects/{id}/tasks}, {@code /topic/tasks/{id}}, {@code /topic/executions/{id}/logs},
 * {@code /topic/live/{code}} — which, unlike {@code /user/**} destinations, are not keyed to the
 * session principal. Without a check any authenticated user could subscribe to another organization's
 * topic and receive its events (threat-model #4, cross-tenant subscription).
 *
 * <p>Each subscription's destination is resolved to an organization id and validated against the
 * caller's membership. Non-tenant destinations (e.g. {@code /user/**}, unknown topics) are allowed;
 * a scoped destination whose resource is missing/unparseable, or to which the caller does not belong,
 * is denied.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StompSubscriptionAuthorizer {

    private static final String TOPIC_PREFIX = "/topic/";

    private final OrganizationMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;

    /**
     * @throws AccessDeniedException if the caller may not subscribe to this destination
     */
    public void authorize(String destination, User user) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return; // /user/** is per-principal; non-topic destinations are not tenant-scoped
        }
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated STOMP SUBSCRIBE to " + destination);
        }

        String[] segments = destination.substring(TOPIC_PREFIX.length()).split("/");
        Long organizationId = resolveOrganizationId(segments, destination);
        if (organizationId == null) {
            return; // not a tenant-scoped topic — allow
        }

        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            log.warn("stomp_subscribe_denied user={} destination={} org={}",
                    user.getId(), destination, organizationId);
            throw new AccessDeniedException("Not authorized to subscribe to " + destination);
        }
    }

    /**
     * Resolve the organization owning the destination, or {@code null} when the destination is not a
     * tenant-scoped topic. A scoped destination that cannot be resolved (bad id, missing entity) is a
     * denial, not an allow — returning {@code null} there would leak by default.
     */
    private Long resolveOrganizationId(String[] segments, String destination) {
        if (segments.length < 2) {
            return null;
        }
        String scope = segments[0];
        String id = segments[1];
        return switch (scope) {
            case "organizations" -> parseId(id, destination);
            case "projects" -> projectRepository.findById(parseId(id, destination))
                    .map(p -> p.getOrganization().getId())
                    .orElseThrow(() -> denied(destination));
            case "tasks" -> taskRepository.findById(parseId(id, destination))
                    .map(t -> t.getProject().getOrganization().getId())
                    .orElseThrow(() -> denied(destination));
            case "executions" -> executionRepository.findById(parseId(id, destination))
                    .map(e -> e.getTask().getProject().getOrganization().getId())
                    .orElseThrow(() -> denied(destination));
            default -> null; // unknown topic namespace — not tenant-scoped
        };
    }

    private Long parseId(String raw, String destination) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw denied(destination);
        }
    }

    private AccessDeniedException denied(String destination) {
        return new AccessDeniedException("Not authorized to subscribe to " + destination);
    }
}
