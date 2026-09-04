package br.com.esl.etl.v2.plataforma.contrato;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Validação fechada dos únicos textos técnicos admitidos nos artefatos sanitizados. */
final class ContractText {

    private static final int MAXIMUM_POINTER_LENGTH = 1_990;
    private static final int MAXIMUM_CHANGE_POINTER_LENGTH = 2_000;

    private static final Pattern DOCUMENT_REFERENCE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:+-]{0,127}");
    private static final Pattern TECHNICAL_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_.:~/-]{0,255}");
    private static final Pattern POINTER_SEGMENT =
            Pattern.compile("(?:[A-Za-z_][A-Za-z0-9_.:~/-]{0,255}|\\*)");

    private ContractText() {}

    static String documentReference(final String value) {
        return matching(
                value,
                DOCUMENT_REFERENCE,
                "A referência técnica do documento de contrato é inválida.");
    }

    static String version(final String value) {
        return matching(value, VERSION, "A versão do contrato é inválida.");
    }

    static String technicalName(final String value) {
        return matching(value, TECHNICAL_NAME, "O nome técnico do contrato é inválido.");
    }

    static String pointer(final String value) {
        return pointer(value, MAXIMUM_POINTER_LENGTH);
    }

    private static String pointer(final String value, final int maximumLength) {
        if (value == null
                || value.isEmpty()
                || value.charAt(0) != '/'
                || value.length() > maximumLength) {
            throw new IllegalArgumentException("O path técnico do contrato é inválido.");
        }
        final String[] segments = value.substring(1).split("/", -1);
        for (final String encoded : segments) {
            if (encoded.isEmpty()) {
                throw new IllegalArgumentException("O path técnico do contrato é inválido.");
            }
            final String decoded = decodePointerSegment(encoded);
            if (!POINTER_SEGMENT.matcher(decoded).matches()) {
                throw new IllegalArgumentException("O path técnico do contrato é inválido.");
            }
        }
        return value;
    }

    static String appendPointer(final String parent, final String rawSegment) {
        if (parent != null && !parent.isEmpty()) {
            pointer(parent);
        }
        final String segment = technicalName(rawSegment);
        final String encoded = segment.replace("~", "~0").replace("/", "~1");
        return pointer((parent == null ? "" : parent) + "/" + encoded);
    }

    static String appendArrayElement(final String arrayPath) {
        return pointer(pointer(arrayPath) + "/*");
    }

    static String changePath(final String value) {
        if ("/root".equals(value) || "/key".equals(value)) {
            return value;
        }
        return value != null && value.startsWith("/")
                ? pointer(value, MAXIMUM_CHANGE_POINTER_LENGTH)
                : technicalName(value);
    }

    static String[] pointerSegments(final String value) {
        pointer(value);
        final String[] encoded = value.substring(1).split("/", -1);
        final String[] decoded = new String[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            decoded[index] = decodePointerSegment(encoded[index]);
        }
        return decoded;
    }

    static String parentPointer(final String value) {
        pointer(value);
        final int separator = value.lastIndexOf('/');
        return separator == 0 ? "" : value.substring(0, separator);
    }

    static int utf8Length(final String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String matching(
            final String value, final Pattern pattern, final String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String decodePointerSegment(final String encoded) {
        final StringBuilder decoded = new StringBuilder(encoded.length());
        for (int index = 0; index < encoded.length(); index++) {
            final char current = encoded.charAt(index);
            if (current != '~') {
                decoded.append(current);
                continue;
            }
            if (index + 1 >= encoded.length()) {
                throw new IllegalArgumentException("O path técnico do contrato é inválido.");
            }
            final char escaped = encoded.charAt(++index);
            if (escaped == '0') {
                decoded.append('~');
            } else if (escaped == '1') {
                decoded.append('/');
            } else {
                throw new IllegalArgumentException("O path técnico do contrato é inválido.");
            }
        }
        return decoded.toString();
    }
}
