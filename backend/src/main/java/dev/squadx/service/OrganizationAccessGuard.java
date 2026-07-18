package dev.squadx.service;

import dev.squadx.exception.ForbiddenException;
import dev.squadx.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single place that answers "may this user act within this organization?".
 *
 * <p>Access control in this codebase is enforced in the service layer (there are no
 * {@code @PreAuthorize} annotations on controllers). Historically each service carried
 * its own private {@code validateUserAccess} copy, and the peripheral resources that
 * were added without that copy became cross-tenant IDOR holes — a user could act on
 * another organization's billing, branding, meetings, agent messages, etc. purely by
 * passing a different id.
 *
 * <p>This guard centralises the membership check so a fixed resource cannot silently
 * drift back to unguarded, and so the check is tested in one place. Scope is always the
 * organization: resolve the resource's owning organization, then call
 * {@link #requireMember(Long, Long)} before reading or mutating it.
 */
@Component
@RequiredArgsConstructor
public class OrganizationAccessGuard {

    private final OrganizationMemberRepository memberRepository;

    /**
     * Throw {@link ForbiddenException} unless the user is a member of the organization.
     * A null organization id is treated as no-access rather than open-access, so a
     * failure to resolve the owning org can never read as permission granted.
     */
    public void requireMember(Long organizationId, Long userId) {
        if (organizationId == null || userId == null
                || !memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }
}
