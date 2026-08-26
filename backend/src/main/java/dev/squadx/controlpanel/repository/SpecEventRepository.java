package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.SpecEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecEventRepository extends JpaRepository<SpecEvent, Long> {

    /** Eventos de uma tarefa, ordenados por ocorrência (projeção determinística — RFC-0003). */
    List<SpecEvent> findBySpecTaskIdOrderByOccurredAtAscIdAsc(Long specTaskId);

    boolean existsByDedupKey(String dedupKey);

    /**
     * Feed de atividade recente do projeto (mais recentes primeiro), atravessando
     * tarefa → change → projeto. Limite via {@link Pageable}.
     */
    @Query("""
            select e from SpecEvent e
            join e.specTask t
            join t.change c
            where c.project.id = :projectId
            order by e.occurredAt desc, e.id desc
            """)
    List<SpecEvent> findRecentByProject(@Param("projectId") Long projectId, Pageable pageable);
}
