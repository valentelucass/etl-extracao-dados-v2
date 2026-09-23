package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.CharacterizationCheck;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FailClosedReason;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ObservationStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ProfileStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Resultado independente de uma entidade, sem autoridade para concluir seu Q-*-01. */
public record CharacterizationResult(
        String profileId,
        Entity entity,
        String profileFingerprint,
        String contractFingerprint,
        String observationFingerprint,
        ObservationStatus observationStatus,
        ProfileStatus profileStatus,
        GateStatus gateStatus,
        Map<CharacterizationCheck, Boolean> checks,
        Set<FailClosedReason> failClosedReasons) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public CharacterizationResult {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("O perfil do resultado é obrigatório.");
        }
        entity = Objects.requireNonNull(entity, "A entidade do resultado é obrigatória.");
        if (!expectedProfileId(entity).equals(profileId)) {
            throw new IllegalArgumentException("O perfil do resultado não corresponde à entidade.");
        }
        if (profileFingerprint == null
                || !SHA256.matcher(profileFingerprint).matches()
                || contractFingerprint == null
                || !SHA256.matcher(contractFingerprint).matches()
                || observationFingerprint == null
                || !SHA256.matcher(observationFingerprint).matches()) {
            throw new IllegalArgumentException("Os fingerprints do resultado são inválidos.");
        }
        observationStatus =
                Objects.requireNonNull(observationStatus, "O status observado é obrigatório.");
        profileStatus = Objects.requireNonNull(profileStatus, "O status do perfil é obrigatório.");
        gateStatus = Objects.requireNonNull(gateStatus, "O status do gate é obrigatório.");
        final EnumMap<CharacterizationCheck, Boolean> normalizedChecks =
                new EnumMap<>(CharacterizationCheck.class);
        normalizedChecks.putAll(Objects.requireNonNull(checks, "Os checks são obrigatórios."));
        if (!normalizedChecks.keySet().equals(EnumSet.allOf(CharacterizationCheck.class))
                || normalizedChecks.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Os checks do resultado estão incompletos.");
        }
        checks = Collections.unmodifiableMap(normalizedChecks);
        final EnumSet<FailClosedReason> reasons = EnumSet.noneOf(FailClosedReason.class);
        reasons.addAll(
                Objects.requireNonNull(
                        failClosedReasons, "Os motivos fail-closed são obrigatórios."));
        failClosedReasons = Collections.unmodifiableSet(reasons);
        final boolean accepted =
                observationStatus == ObservationStatus.SYNTHETIC_STRUCTURE_ACCEPTED;
        if (profileStatus != ProfileStatus.PREPARED_NOT_EXECUTED
                || gateStatus != GateStatus.ORACLE_REQUIRED
                || accepted != failClosedReasons.isEmpty()
                || accepted && !normalizedChecks.values().stream().allMatch(Boolean.TRUE::equals)) {
            throw new IllegalArgumentException("O resultado da fundação é inconsistente.");
        }
    }

    private static String expectedProfileId(final Entity entity) {
        return switch (entity) {
            case COLETAS -> "V2_012_COLETAS_6908";
            case MANIFESTOS -> "V2_012_MANIFESTOS_6399";
            case COTACOES -> "V2_012_COTACOES_6906";
            case USUARIOS -> "V2_012_USUARIOS_INDIVIDUAL";
        };
    }
}
