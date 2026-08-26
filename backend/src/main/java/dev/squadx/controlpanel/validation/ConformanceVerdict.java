package dev.squadx.controlpanel.validation;

/**
 * Veredito da revisão de conformidade comportamental (RFC-0004 §7). {@code diverges=true} quando o
 * código não corresponde aos cenários; {@code critique} traz o porquê.
 */
public record ConformanceVerdict(boolean diverges, String critique) {

    public static ConformanceVerdict ok() {
        return new ConformanceVerdict(false, null);
    }

    public static ConformanceVerdict diverges(String critique) {
        return new ConformanceVerdict(true, critique);
    }
}

