package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.SpecEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecEventRepository extends JpaRepository<SpecEvent, Long> {

    /** Eventos de uma tarefa, ordenados por ocorrência (projeção determinística — RFC-0003). */
    List<SpecEvent> findBySpecTaskIdOrderByOccurredAtAscIdAsc(Long specTaskId);

    boolean existsByDedupKey(String dedupKey);
}
