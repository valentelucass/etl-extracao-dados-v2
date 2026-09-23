package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Wire v1: comprimento UTF-16 em bytes, sem ambiguidades de separadores. Não é um permit. */
public final class RuntimeRecoveryMaterial {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private RuntimeRecoveryMaterial() {}

    public static String identity(final ControlPlaneStart start, final ImmutableFingerprint plan) {
        final var p = start.partition();
        if (p.partitionStart().getNano() % 1_000_000 != 0
                || p.partitionEndExclusive().getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("RUNTIME_RECOVERY_WINDOW_PRECISION_INVALID");
        }
        return fields(
                start.executionId(),
                start.cycleId(),
                plan.version(),
                plan.sha256(),
                p.environment(),
                p.sourceInstance(),
                p.tenantScope(),
                p.entity(),
                p.mode(),
                TIME.format(p.partitionStart()),
                TIME.format(p.partitionEndExclusive()),
                start.windowStrategy(),
                start.idempotencyKey(),
                start.replayOfExecutionId().map(Object::toString).orElse(""),
                start.contract().version(),
                start.contract().sha256(),
                start.configuration().version(),
                start.configuration().sha256());
    }

    public static String contract(final ContractExecutionBinding binding) {
        return fields(
                binding.sourceKind(),
                binding.documentReference(),
                binding.contractVersion(),
                binding.metadataFingerprint().version(),
                binding.metadataFingerprint().sha256(),
                binding.responseFingerprint().version(),
                binding.responseFingerprint().sha256(),
                binding.runtimeConfigurationFingerprint().version(),
                binding.runtimeConfigurationFingerprint().sha256());
    }

    public static String contract(final ContractPromotionPermit permit) {
        return fields(
                permit.sourceKind(),
                permit.documentReference(),
                permit.contractVersion(),
                permit.metadataFingerprint().version(),
                permit.metadataFingerprint().sha256(),
                permit.responseFingerprint().version(),
                permit.responseFingerprint().sha256(),
                permit.runtimeConfigurationFingerprint().version(),
                permit.runtimeConfigurationFingerprint().sha256());
    }

    private static String fields(final Object... values) {
        final StringBuilder result = new StringBuilder("runtime-recovery-v1|");
        for (final Object value : values) {
            final String text = value.toString();
            result.append(text.length() * 2).append(':').append(text).append('|');
        }
        return result.toString();
    }
}
