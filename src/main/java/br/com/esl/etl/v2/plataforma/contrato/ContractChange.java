package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;
import java.util.Optional;

/** Uma diferença estrutural classificada, sem before/after ou valor de payload. */
public record ContractChange(
        Component component,
        Kind kind,
        Severity severity,
        Optional<ContractResponse.FieldScope> responseFieldScope,
        Optional<ContractResponse.Field> approvedResponseField,
        String path,
        ImmutableFingerprint signature) {

    public ContractChange {
        component = Objects.requireNonNull(component, "O componente do drift é obrigatório.");
        kind = Objects.requireNonNull(kind, "A categoria do drift é obrigatória.");
        severity = Objects.requireNonNull(severity, "A severidade do drift é obrigatória.");
        responseFieldScope = responseFieldScope == null ? Optional.empty() : responseFieldScope;
        approvedResponseField =
                approvedResponseField == null ? Optional.empty() : approvedResponseField;
        path = ContractText.changePath(path);
        signature = Objects.requireNonNull(signature, "A assinatura do drift é obrigatória.");
        if (kind.compatible() != (severity == Severity.COMPATIBLE)) {
            throw new IllegalArgumentException(
                    "Categoria e severidade do drift são incompatíveis.");
        }
        if (component == Component.METADATA && responseFieldScope.isPresent()) {
            throw new IllegalArgumentException(
                    "Uma mudança de metadata não possui escopo de resposta.");
        }
        if (component == Component.METADATA && approvedResponseField.isPresent()) {
            throw new IllegalArgumentException(
                    "Uma mudança de metadata não possui shape de resposta aprovado.");
        }
        if (component == Component.RESPONSE
                && responseFieldScope.isEmpty()
                && !path.equals("/root")
                && !path.equals("/key")) {
            throw new IllegalArgumentException(
                    "Uma mudança de field exige escopo de resposta explícito.");
        }
        if (responseFieldScope.orElse(null) == ContractResponse.FieldScope.ENVELOPE
                && !path.startsWith("/envelope/")) {
            throw new IllegalArgumentException(
                    "Uma mudança de envelope exige path reportado de envelope.");
        }
        if (component == Component.RESPONSE
                && severity == Severity.COMPATIBLE
                && approvedResponseField.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uma mudança compatível de resposta exige shape aprovado.");
        }
        if (approvedResponseField.isPresent()) {
            validateApprovedField(
                    responseFieldScope.orElseThrow(), path, approvedResponseField.orElseThrow());
        }
    }

    private static void validateApprovedField(
            final ContractResponse.FieldScope scope,
            final String reportedPath,
            final ContractResponse.Field field) {
        final String expectedPath =
                scope == ContractResponse.FieldScope.ENVELOPE
                        ? "/envelope" + field.path()
                        : field.path();
        if (field.scope() != scope || !reportedPath.equals(expectedPath)) {
            throw new IllegalArgumentException(
                    "O shape aprovado não corresponde ao path e escopo da mudança.");
        }
    }

    @Override
    public String toString() {
        return "ContractChange[component="
                + component
                + ", kind="
                + kind
                + ", severity="
                + severity
                + "]";
    }

    public enum Component {
        METADATA,
        RESPONSE
    }

    public enum Severity {
        BREAKING,
        COMPATIBLE
    }

    public enum Kind {
        FIELD_REMOVED(false),
        FILTER_CHANGED(false),
        APPROVED_DOCUMENT_CHANGED(false),
        DECLARED_TYPE_CHANGED(false),
        ROOT_CHANGED(false),
        KEY_CHANGED(false),
        REQUIRED_FIELD_MISSING(false),
        TYPE_CHANGED(false),
        CARDINALITY_CHANGED(false),
        REQUIRED_OR_UNPROVEN_FIELD_ADDED(false),
        OPTIONAL_FIELD_ADDED(true),
        PREVIOUSLY_UNOBSERVED_SHAPE_OBSERVED(true),
        NULLABILITY_WIDENED(true);

        private final boolean compatible;

        Kind(final boolean compatible) {
            this.compatible = compatible;
        }

        public boolean compatible() {
            return compatible;
        }
    }
}
