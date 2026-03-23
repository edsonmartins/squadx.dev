package dev.squadx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Buffers WebSocket events for disconnected users.
 * Events are drained when the user reconnects or polls via REST.
 */
@Service
@Slf4j
public class EventBufferService {

    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<BufferedEvent>> buffers = new ConcurrentHashMap<>();

    @Value("${squadx.event-buffer.max-events-per-user:100}")
    private int maxEventsPerUser;

    @Value("${squadx.event-buffer.ttl-minutes:5}")
    private int ttlMinutes;

    public record BufferedEvent(Map<String, Object> payload, Instant createdAt) {}

    public void bufferEvent(Long userId, Map<String, Object> event) {
        if (userId == null || event == null) return;

        var queue = buffers.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>());

        // Add first, then trim — avoids race condition between size check and add.
        // Worst case: queue briefly exceeds max by 1, immediately trimmed.
        queue.add(new BufferedEvent(event, Instant.now()));
        while (queue.size() > maxEventsPerUser) {
            queue.poll();
        }

        log.debug("Buffered event for user={}, queue size={}", userId, queue.size());
    }

    public List<Map<String, Object>> drainEvents(Long userId) {
        var queue = buffers.remove(userId);
        if (queue == null) return List.of();

        Instant cutoff = Instant.now().minusSeconds(ttlMinutes * 60L);
        List<Map<String, Object>> events = new ArrayList<>();

        for (BufferedEvent be : queue) {
            if (be.createdAt().isAfter(cutoff)) {
                events.add(be.payload());
            }
        }

        log.debug("Drained {} events for user={}", events.size(), userId);
        return events;
    }

    public int pendingCount(Long userId) {
        var queue = buffers.get(userId);
        return queue != null ? queue.size() : 0;
    }

    public boolean hasBufferedEvents(Long userId) {
        var queue = buffers.get(userId);
        return queue != null && !queue.isEmpty();
    }

    /**
     * Evict expired events from all buffers.
     * Called periodically by BackgroundJobService.
     */
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(ttlMinutes * 60L);
        buffers.forEach((userId, queue) -> {
            queue.removeIf(be -> be.createdAt().isBefore(cutoff));
            if (queue.isEmpty()) {
                buffers.remove(userId);
            }
        });
    }
}
