package dev.squadx.repository;

import dev.squadx.model.Squad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SquadRepository extends JpaRepository<Squad, Long> {

    Page<Squad> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT s FROM Squad s WHERE s.organization.id = :organizationId AND s.isActive = true")
    Page<Squad> findActiveByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Squad s WHERE s.organization.id = :organizationId")
    long countByOrganizationId(Long organizationId);
}
