package dev.squadx.model;

import dev.squadx.model.enums.AgentMessageType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_agent_id", nullable = false)
    private Agent fromAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_agent_id")
    private Agent toAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private AgentMessageType messageType = AgentMessageType.TASK_UPDATE;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read")
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "is_broadcast")
    @Builder.Default
    private boolean isBroadcast = false;
}
