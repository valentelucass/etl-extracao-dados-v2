package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;
import java.util.Optional;

/** Regra exata para uma única mudança compatível já caracterizada. */
public record ContractAllowance(
        ContractChange.Component component,
        ContractChange.Kind kind,
        Optional<ContractResponse.FieldScope> responseFieldScope,
        Optional<ContractResponse.Field> approvedResponseField,
        String path,
        ImmutableFingerprint changeSignature) {

    public ContractAllowance {
        component = Objects.requireNonNull(component, "O componente permitido é obrigatório.");
        kind = Objects.requireNonNull(kind, "A categoria permitida é obrigatória.");
        if (!kind.compatible()) {
            throw new IllegalArgumentException(
                    "A allowlist não pode liberar uma mudança incompatível.");
        }
        responseFieldScope = responseFieldScope == null ? Optional.empty() : responseFieldScope;
        approvedResponseField =
                approvedResponseField == null ? Optional.empty() : approvedResponseField;
        path = ContractText.changePath(path);
        changeSignature =
                Objects.requireNonNull(
                        changeSignature, "A assinatura permitida de contrato é obrigatória.");
        if (component == ContractChange.Component.METADATA && responseFieldScope.isPresent()) {
            throw new IllegalArgumentException(
                    "Uma permissão de metadata não possui escopo de resposta.");
        }
        if (component == ContractChange.Component.METADATA && approvedResponseField.isPresent()) {
            throw new IllegalArgumentException(
                    "Uma permissão de metadata não possui shape de resposta aprovado.");
        }
        if (component == ContractChange.Component.RESPONSE && responseFieldScope.isEmpty()) {
            throw new IllegalArgumentException("Uma permissão de resposta exige escopo explícito.");
        }
        if (component == ContractChange.Component.RESPONSE && approvedResponseField.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uma permissão de resposta exige shape aprovado explícito.");
        }
        if (responseFieldScope.orElse(null) == ContractResponse.FieldScope.ENVELOPE
                && !path.startsWith("/envelope/")) {
            throw new IllegalArgumentException(
                    "Uma permissão de envelope exige path reportado de envelope.");
        }
        if (approvedResponseField.isPresent()) {
            validateApprovedField(
                    responseFieldScope.orElseThrow(), path, approvedResponseField.orElseThrow());
        }
    }

    public static ContractAllowance forChange(final ContractChange change) {
        Objects.requireNonNull(change, "A mudança compatível é obrigatória.");
        if (change.severity() != ContractChange.Severity.COMPATIBLE) {
            throw new IllegalArgumentException(
                    "A allowlist não pode liberar uma mudança incompatível.");
        }
        return new ContractAllowance(
                change.component(),
                change.kind(),
                change.responseFieldScope(),
                change.approvedResponseField(),
                change.path(),
                change.signature());
    }

    boolean matches(final ContractChange change) {
        return component == change.component()
                && kind == change.kind()
                && responseFieldScope.equals(change.responseFieldScope())
                && approvedResponseField.equals(change.approvedResponseField())
                && path.equals(change.path())
                && changeSignature.equals(change.signature());
    }

    @Override
    public String toString() {
        return "ContractAllowance[component="
                + component
                + ", kind="
                + kind
                + ", responseFieldScope="
                + responseFieldScope
                + "]";
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
                    "O shape aprovado não corresponde ao path e escopo da permissão.");
        }
    }
}
