package dev.squadx.controlpanel.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decisão de origem de uma tarefa (RFC-0007, T-0010-1).
 *
 * @param id       identificador da decisão (ex.: "ADR-0012")
 * @param status   status da decisão (Proposto | Aceito | Supersedido)
 * @param path     caminho do arquivo no repositório
 */
public record Decision(String id, String status, String path) {
}
