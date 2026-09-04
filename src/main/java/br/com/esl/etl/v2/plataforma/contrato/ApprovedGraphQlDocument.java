package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fingerprint de uma query GraphQL estática, sem conservar o documento em memória. */
public final class ApprovedGraphQlDocument {

    public static final int MAXIMUM_UTF8_BYTES = 65_536;

    private final ImmutableFingerprint fingerprint;

    private ApprovedGraphQlDocument(final ImmutableFingerprint fingerprint) {
        this.fingerprint = fingerprint;
    }

    public static ApprovedGraphQlDocument approve(final String document) {
        Objects.requireNonNull(document, "O documento GraphQL é obrigatório.");
        if (document.length() > MAXIMUM_UTF8_BYTES + 1) {
            throw rejected();
        }
        final String normalized = normalize(document);
        if (normalized.isBlank()
                || ContractText.utf8Length(normalized) > MAXIMUM_UTF8_BYTES
                || containsForbiddenControl(normalized)) {
            throw rejected();
        }
        final List<String> tokens = tokenize(normalized);
        new RestrictedParser(tokens).parse();
        return new ApprovedGraphQlDocument(
                ContractCanonicalizer.graphQlDocument(String.join(" ", tokens)));
    }

    public ImmutableFingerprint fingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof ApprovedGraphQlDocument document
                && fingerprint.equals(document.fingerprint);
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode();
    }

    @Override
    public String toString() {
        return "ApprovedGraphQlDocument[fingerprintVersion=" + fingerprint.version() + "]";
    }

    private static String normalize(final String document) {
        final String withoutBom =
                !document.isEmpty() && document.charAt(0) == '\uFEFF'
                        ? document.substring(1)
                        : document;
        return withoutBom.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean containsForbiddenControl(final String document) {
        return document.chars()
                .anyMatch(
                        character ->
                                Character.isISOControl(character)
                                        && character != '\n'
                                        && character != '\t');
    }

    private static List<String> tokenize(final String document) {
        final List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < document.length()) {
            final char current = document.charAt(index);
            if (current == ' ' || current == '\t' || current == '\n') {
                index++;
                continue;
            }
            if (isNameStart(current)) {
                final int start = index++;
                while (index < document.length() && isNamePart(document.charAt(index))) {
                    index++;
                }
                final String name = document.substring(start, index);
                if (name.startsWith("__") || isForbiddenWord(name)) {
                    throw rejected();
                }
                tokens.add(name);
                continue;
            }
            if ("{}()[]!$:,@".indexOf(current) >= 0) {
                tokens.add(Character.toString(current));
                index++;
                continue;
            }
            throw rejected();
        }
        return List.copyOf(tokens);
    }

    private static boolean isNameStart(final char value) {
        return value == '_' || value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static boolean isNamePart(final char value) {
        return isNameStart(value) || value >= '0' && value <= '9';
    }

    private static boolean isForbiddenWord(final String value) {
        return "mutation".equals(value)
                || "subscription".equals(value)
                || "fragment".equals(value)
                || "true".equals(value)
                || "false".equals(value)
                || "null".equals(value);
    }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException("O documento GraphQL estático não é aprovável.");
    }

    /** Parser deliberadamente menor que GraphQL: query nomeada, variáveis e seleções read-only. */
    private static final class RestrictedParser {

        private static final int MAXIMUM_SELECTION_DEPTH = 32;

        private final List<String> tokens;
        private final Set<String> declaredVariables = new HashSet<>();
        private final Set<String> usedVariables = new HashSet<>();
        private int position;

        private RestrictedParser(final List<String> tokens) {
            this.tokens = tokens;
        }

        private void parse() {
            expect("query");
            readName();
            if (peek("(")) {
                parseVariableDefinitions();
            }
            parseSelectionSet(0);
            if (position != tokens.size() || !declaredVariables.containsAll(usedVariables)) {
                throw rejected();
            }
        }

        private void parseVariableDefinitions() {
            expect("(");
            parseVariableDefinition();
            while (accept(",")) {
                parseVariableDefinition();
            }
            expect(")");
        }

        private void parseVariableDefinition() {
            expect("$");
            final String variable = readName();
            if (!declaredVariables.add(variable)) {
                throw rejected();
            }
            expect(":");
            parseTypeReference();
        }

        private void parseTypeReference() {
            if (accept("[")) {
                readName();
                accept("!");
                expect("]");
                accept("!");
                return;
            }
            readName();
            accept("!");
        }

        private void parseSelectionSet(final int depth) {
            if (depth > MAXIMUM_SELECTION_DEPTH) {
                throw rejected();
            }
            expect("{");
            if (peek("}")) {
                throw rejected();
            }
            do {
                parseField(depth);
            } while (!peek("}"));
            expect("}");
        }

        private void parseField(final int depth) {
            readName();
            if (peek("(")) {
                parseArguments();
            }
            while (accept("@")) {
                readName();
                if (peek("(")) {
                    parseArguments();
                }
            }
            if (peek("{")) {
                parseSelectionSet(depth + 1);
            }
        }

        private void parseArguments() {
            expect("(");
            parseArgument();
            while (accept(",")) {
                parseArgument();
            }
            expect(")");
        }

        private void parseArgument() {
            readName();
            expect(":");
            expect("$");
            usedVariables.add(readName());
        }

        private String readName() {
            if (position >= tokens.size()) {
                throw rejected();
            }
            final String token = tokens.get(position);
            if (token.isEmpty() || !isNameStart(token.charAt(0))) {
                throw rejected();
            }
            position++;
            return token;
        }

        private boolean peek(final String token) {
            return position < tokens.size() && token.equals(tokens.get(position));
        }

        private boolean accept(final String token) {
            if (!peek(token)) {
                return false;
            }
            position++;
            return true;
        }

        private void expect(final String token) {
            if (!accept(token)) {
                throw rejected();
            }
        }
    }
}
