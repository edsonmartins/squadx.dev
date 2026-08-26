package dev.squadx.repository;

import dev.squadx.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<Project> findFirstByRepositoryUrlIn(Collection<String> repositoryUrls);

    @EntityGraph(attributePaths = {"organization", "squad"})
    Page<Project> findByOrganizationId(Long organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "squad"})
    @Query("SELECT p FROM Project p WHERE p.organization.id = :organizationId AND p.isActive = true")
    Page<Project> findActiveByOrganizationId(Long organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "squad"})
    @Query("SELECT p FROM Project p JOIN p.organization o JOIN o.members m " +
           "WHERE m.user.id = :userId AND m.isActive = true AND p.isActive = true")
    Page<Project> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId")
    long countTasksByProjectId(Long projectId);
}
