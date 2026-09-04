package br.com.esl.etl.v2.plataforma.observabilidade;

import java.util.Objects;
import java.util.regex.Pattern;

/** Defesa em profundidade para texto diagnóstico; eventos normais não aceitam texto livre. */
public final class SensitiveValueRedactor {

    private static final String REDACTED = "<redacted>";
    private static final Pattern AUTHORIZATION =
            Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile(
                    "(?i)(?<![A-Za-z0-9_-])\"?(authorization|client[_-]?secret|"
                            + "access[_-]?token|refresh[_-]?token|"
                            + "db[_-]?password|token|password|passwd|pwd|secret|api[_-]?key)"
                            + "\"?\\s*[:=]\\s*(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;]+)");
    private static final Pattern JWT =
            Pattern.compile("\\b[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern JDBC_URL = Pattern.compile("(?i)\\bjdbc:[^\\s]+");
    private static final Pattern HTTP_URL = Pattern.compile("(?i)\\bhttps?://[^\\s]+");
    private static final Pattern EMAIL =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern UUID =
            Pattern.compile(
                    "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern DOCUMENT_NUMBER =
            Pattern.compile(
                    "(?<!\\d)(?:\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}"
                            + "|\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})(?!\\d)");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\t]]");

    private final int maximumInputCharacters;
    private final int maximumOutputCharacters;

    public SensitiveValueRedactor(
            final int maximumInputCharacters, final int maximumOutputCharacters) {
        if (maximumInputCharacters < 1
                || maximumInputCharacters > 65_536
                || maximumOutputCharacters < 1
                || maximumOutputCharacters > maximumInputCharacters) {
            throw new IllegalArgumentException("Os limites do redactor são inválidos.");
        }
        this.maximumInputCharacters = maximumInputCharacters;
        this.maximumOutputCharacters = maximumOutputCharacters;
    }

    public String redact(final String value) {
        Objects.requireNonNull(value, "O texto diagnóstico é obrigatório.");
        String safe = safePrefix(value, maximumInputCharacters);
        safe = CONTROL.matcher(safe).replaceAll(" ");
        safe = AUTHORIZATION.matcher(safe).replaceAll(REDACTED);
        safe = SECRET_ASSIGNMENT.matcher(safe).replaceAll("$1=" + REDACTED);
        safe = JWT.matcher(safe).replaceAll(REDACTED);
        safe = JDBC_URL.matcher(safe).replaceAll("<redacted-jdbc-url>");
        safe = HTTP_URL.matcher(safe).replaceAll("<redacted-url>");
        safe = EMAIL.matcher(safe).replaceAll(REDACTED);
        safe = UUID.matcher(safe).replaceAll(REDACTED);
        safe = DOCUMENT_NUMBER.matcher(safe).replaceAll(REDACTED);
        return safePrefix(safe, maximumOutputCharacters);
    }

    private static String safePrefix(final String value, final int maximumCharacters) {
        if (value.length() <= maximumCharacters) {
            return value;
        }
        int end = maximumCharacters;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
