package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.Change;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChangeRepository extends JpaRepository<Change, Long> {

    Page<Change> findByProjectId(Long projectId, Pageable pageable);
}
