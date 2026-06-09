package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.Pass5Run;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface Pass5RunRepository extends JpaRepository<Pass5Run, Long> {

    boolean existsBySpecTaskIdAndPrSha(Long specTaskId, String prSha);

    Optional<Pass5Run> findTopBySpecTaskIdOrderByCreatedAtDesc(Long specTaskId);

    /** Runs of several tasks, newest first — for batch status assembly (first per task = latest). */
    List<Pass5Run> findBySpecTaskIdInOrderByCreatedAtDescIdDesc(Collection<Long> specTaskIds);
}
