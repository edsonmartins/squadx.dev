package dev.squadx.repository;

import dev.squadx.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT o FROM Organization o JOIN o.members m WHERE m.user.id = :userId AND m.isActive = true")
    Page<Organization> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Organization o JOIN o.members m WHERE m.user.id = :userId AND m.isActive = true")
    long countByUserId(Long userId);

    @Query("SELECT COUNT(m) FROM OrganizationMember m WHERE m.organization.id = :orgId AND m.isActive = true")
    long countMembersByOrganizationId(Long orgId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.organization.id = :orgId AND p.isActive = true")
    long countProjectsByOrganizationId(Long orgId);

    @Query("SELECT COUNT(s) FROM Squad s WHERE s.organization.id = :orgId AND s.isActive = true")
    long countSquadsByOrganizationId(Long orgId);
}
