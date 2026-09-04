package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Forma sanitizada de {@code /info} ou da seleção de uma query GraphQL estática aprovada. */
public record ContractMetadata(
        List<Element> elements, Optional<ApprovedGraphQlDocument> approvedDocument) {

    public static final int MAXIMUM_ELEMENTS = 4096;

    public ContractMetadata {
        Objects.requireNonNull(elements, "Os elementos de metadata são obrigatórios.");
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("A metadata deve declarar ao menos um elemento.");
        }
        if (elements.size() > MAXIMUM_ELEMENTS) {
            throw new IllegalArgumentException("A metadata excede o limite de elementos.");
        }
        final List<Element> sorted = new ArrayList<>(elements.size());
        final Set<String> identities = new HashSet<>();
        for (final Element element : elements) {
            final Element required =
                    Objects.requireNonNull(element, "A metadata não pode conter elemento nulo.");
            final String identity =
                    required.kind().name() + ':' + required.path().toLowerCase(Locale.ROOT);
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "A metadata contém path técnico duplicado ou ambíguo.");
            }
            sorted.add(required);
        }
        sorted.sort(Comparator.comparing(Element::kind).thenComparing(Element::path));
        elements = List.copyOf(sorted);
        approvedDocument = approvedDocument == null ? Optional.empty() : approvedDocument;
    }

    public Optional<Element> find(final ElementKind kind, final String path) {
        Objects.requireNonNull(kind, "A categoria de metadata é obrigatória.");
        Objects.requireNonNull(path, "O path de metadata é obrigatório.");
        return elements.stream()
                .filter(element -> element.kind() == kind && element.path().equals(path))
                .findFirst();
    }

    @Override
    public String toString() {
        return "ContractMetadata[elementCount="
                + elements.size()
                + ", approvedDocument="
                + approvedDocument.isPresent()
                + "]";
    }

    /** Um nome/path estrutural e seu tipo fechado, sem label ou valor remoto. */
    public record Element(
            ElementKind kind,
            String path,
            DeclaredType declaredType,
            Optional<ImmutableFingerprint> declaredTypeFingerprint) {

        public Element(final ElementKind kind, final String path, final DeclaredType declaredType) {
            this(
                    kind,
                    path,
                    declaredType,
                    declaredType == DeclaredType.UNDECLARED
                            ? Optional.empty()
                            : Optional.of(
                                    ContractCanonicalizer.declaredTypeText(declaredType.name())));
        }

        public Element {
            kind = Objects.requireNonNull(kind, "A categoria de metadata é obrigatória.");
            path =
                    switch (kind) {
                        case DATA_FIELD, DATA_FILTER -> ContractText.technicalName(path);
                        case GRAPHQL_SELECTION, GRAPHQL_ARGUMENT -> ContractText.pointer(path);
                    };
            declaredType =
                    Objects.requireNonNull(
                            declaredType, "O tipo declarado de metadata é obrigatório.");
            declaredTypeFingerprint =
                    declaredTypeFingerprint == null ? Optional.empty() : declaredTypeFingerprint;
            if ((declaredType == DeclaredType.UNDECLARED) == declaredTypeFingerprint.isPresent()) {
                throw new IllegalArgumentException(
                        "O tipo declarado exige fingerprint técnico exato.");
            }
        }

        public static Element fromDeclaredType(
                final ElementKind kind,
                final String path,
                final Optional<String> declaredTypeText) {
            final DeclaredType normalized = DeclaredType.from(declaredTypeText);
            return new Element(
                    kind,
                    path,
                    normalized,
                    declaredTypeText.isPresent()
                            ? Optional.of(
                                    ContractCanonicalizer.declaredTypeText(
                                            declaredTypeText.orElseThrow()))
                            : Optional.empty());
        }
    }

    public enum ElementKind {
        DATA_FIELD,
        DATA_FILTER,
        GRAPHQL_SELECTION,
        GRAPHQL_ARGUMENT
    }

    /** Tipos fechados, acompanhados do hash do texto declarado exato quando ele existe. */
    public enum DeclaredType {
        UNDECLARED,
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        DATE,
        DATE_TIME,
        ARRAY,
        OBJECT,
        OTHER;

        public static DeclaredType from(final Optional<String> declaredType) {
            Objects.requireNonNull(declaredType, "O tipo declarado é obrigatório.");
            if (declaredType.isEmpty()) {
                return UNDECLARED;
            }
            final String raw = declaredType.orElseThrow();
            if (raw.length() > 256
                    || raw.isBlank()
                    || raw.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("O tipo declarado de metadata é inválido.");
            }
            final String normalized = raw.strip().toLowerCase(Locale.ROOT);
            if (normalized.matches("(?:datetime|date_time|timestamp)(?:\\([0-9]+\\))?")) {
                return DATE_TIME;
            }
            if (normalized.equals("date")) {
                return DATE;
            }
            if (normalized.matches("(?:bool|boolean|bit)")) {
                return BOOLEAN;
            }
            if (normalized.matches("(?:tinyint|smallint|int|integer|bigint|long)")) {
                return INTEGER;
            }
            if (normalized.matches(
                    "(?:decimal|float|double|number|numeric|real)(?:\\([0-9]+(?:,[0-9]+)?\\))?")) {
                return NUMBER;
            }
            if (normalized.matches("(?:array|list)(?:<[^<>]+>)?")) {
                return ARRAY;
            }
            if (normalized.matches("(?:object|json|jsonb)")) {
                return OBJECT;
            }
            if (normalized.matches(
                    "(?:string|text|char|nchar|varchar|nvarchar)(?:\\([0-9]+|max\\))?")) {
                return STRING;
            }
            return OTHER;
        }
    }
}
