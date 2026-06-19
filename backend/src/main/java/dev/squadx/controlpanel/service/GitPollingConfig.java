package dev.squadx.controlpanel.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o agendamento Spring SOMENTE quando o poll de comentários de PR está ligado
 * ({@code squadx.git.poll-enabled=true}). No default (desligado) nada muda — nenhum {@code @Scheduled}
 * é ativado. Atenção: ligar esta flag ativa o scheduler global, incluindo outros {@code @Scheduled}.
 */
@Configuration
@ConditionalOnProperty(prefix = "squadx.git", name = "poll-enabled", havingValue = "true")
@EnableScheduling
public class GitPollingConfig {
}
