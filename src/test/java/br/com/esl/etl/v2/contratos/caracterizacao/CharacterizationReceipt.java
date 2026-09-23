package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FoundationOutcome;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ObservationStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ProfileStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Receipt allowlisted: somente status, contagens e fingerprints estruturais. */
public record CharacterizationReceipt(
        String schemaVersion,
        String receiptId,
        String generatedAtUtc,
        FoundationOutcome outcome,
        int profileCount,
        List<ProfileReceipt> profiles) {

    private static final Pattern SYNTHETIC_ID = Pattern.compile("SYNTH_[A-Z0-9_]{3,95}");

    public CharacterizationReceipt {
        if (!"V2_012_RECEIPT_V1".equals(schemaVersion)
                || receiptId == null
                || !SYNTHETIC_ID.matcher(receiptId).matches()) {
            throw new IllegalArgumentException("A identidade sintética do receipt é inválida.");
        }
        try {
            if (generatedAtUtc == null
                    || !Instant.parse(generatedAtUtc).toString().equals(generatedAtUtc)) {
                throw new IllegalArgumentException("O instante do receipt não é canônico.");
            }
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("O instante do receipt é inválido.");
        }
        outcome = Objects.requireNonNull(outcome, "O resultado do receipt é obrigatório.");
        final List<ProfileReceipt> sorted =
                new ArrayList<>(
                        Objects.requireNonNull(profiles, "Os perfis do receipt são obrigatórios."));
        sorted.sort(Comparator.comparing(ProfileReceipt::entity));
        profiles = List.copyOf(sorted);
        if (profileCount != profiles.size()) {
            throw new IllegalArgumentException("A contagem de perfis do receipt diverge.");
        }
        final EnumSet<Entity> entities = EnumSet.noneOf(Entity.class);
        final boolean allEligible =
                profiles.stream()
                        .allMatch(
                                profile ->
                                        entities.add(profile.entity())
                                                && profile.observationStatus()
                                                        == ObservationStatus
                                                                .SYNTHETIC_STRUCTURE_ACCEPTED
                                                && profile.profileStatus()
                                                        == ProfileStatus.PREPARED_NOT_EXECUTED
                                                && profile.gateStatus()
                                                        == GateStatus.ORACLE_REQUIRED
                                                && profile.failClosedReasonCount() == 0);
        final FoundationOutcome derived =
                profiles.size() == Entity.values().length
                                && entities.size() == Entity.values().length
                                && allEligible
                        ? FoundationOutcome.FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES
                        : FoundationOutcome.FOUNDATION_FAIL_CLOSED;
        if (outcome != derived) {
            throw new IllegalArgumentException("O resultado do receipt diverge dos perfis.");
        }
    }

    public record ProfileReceipt(
            Entity entity,
            String profileFingerprint,
            String observationFingerprint,
            String contractFingerprint,
            String fixtureFingerprint,
            ObservationStatus observationStatus,
            ProfileStatus profileStatus,
            GateStatus gateStatus,
            int failClosedReasonCount) {

        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public ProfileReceipt {
            entity = Objects.requireNonNull(entity, "A entidade do receipt é obrigatória.");
            requireSha(profileFingerprint);
            requireSha(observationFingerprint);
            requireSha(contractFingerprint);
            requireSha(fixtureFingerprint);
            observationStatus =
                    Objects.requireNonNull(observationStatus, "O status observado é obrigatório.");
            profileStatus =
                    Objects.requireNonNull(profileStatus, "O status do perfil é obrigatório.");
            gateStatus = Objects.requireNonNull(gateStatus, "O status do gate é obrigatório.");
            if (failClosedReasonCount < 0) {
                throw new IllegalArgumentException("A contagem fail-closed é inválida.");
            }
            if (profileStatus != ProfileStatus.PREPARED_NOT_EXECUTED
                    || gateStatus != GateStatus.ORACLE_REQUIRED
                    || (observationStatus == ObservationStatus.SYNTHETIC_STRUCTURE_ACCEPTED)
                            != (failClosedReasonCount == 0)) {
                throw new IllegalArgumentException("O perfil do receipt é inconsistente.");
            }
        }

        static void requireSha(final String value) {
            if (value == null || !SHA256.matcher(value).matches()) {
                throw new IllegalArgumentException("Um fingerprint do receipt é inválido.");
            }
        }
    }
}
