package br.com.esl.etl.v2.modulos.usuarios.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Registro minimizado de Usuários; valores de identidade e nome nunca integram {@code toString}.
 */
public final class UsuarioStageRecord {

    public static final int MAXIMUM_NAME_CHARACTERS = 255;
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private final int inputOrdinal;
    private final ScopedSourceIdentity.SourceKey sourceKey;
    private final UsuarioNamePresence namePresence;
    private final String name;
    private final UsuarioStageDisposition disposition;
    private final String quarantineReasonCode;

    private UsuarioStageRecord(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final UsuarioNamePresence namePresence,
            final String name,
            final UsuarioStageDisposition disposition,
            final String quarantineReasonCode) {
        if (inputOrdinal < 1 || inputOrdinal > UsuarioStageBatch.MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("O ordinal de Usuários está fora da página.");
        }
        this.inputOrdinal = inputOrdinal;
        this.disposition =
                Objects.requireNonNull(disposition, "A disposição de Usuários é obrigatória.");
        if (disposition == UsuarioStageDisposition.VALID) {
            this.sourceKey =
                    Objects.requireNonNull(sourceKey, "A source key de Usuários é obrigatória.");
            this.namePresence =
                    Objects.requireNonNull(namePresence, "A presença de name é obrigatória.");
            validateName(namePresence, name);
            this.name = name;
            if (quarantineReasonCode != null) {
                throw new IllegalArgumentException(
                        "Registro válido de Usuários não aceita motivo de quarantine.");
            }
            this.quarantineReasonCode = null;
        } else {
            this.sourceKey = sourceKey;
            this.namePresence = null;
            this.name = null;
            if (quarantineReasonCode == null
                    || !REASON_CODE.matcher(quarantineReasonCode).matches()) {
                throw new IllegalArgumentException(
                        "Registro em quarantine exige motivo sanitizado.");
            }
            this.quarantineReasonCode = quarantineReasonCode;
        }
    }

    public static UsuarioStageRecord valid(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final UsuarioNamePresence namePresence,
            final String name) {
        return new UsuarioStageRecord(
                inputOrdinal, sourceKey, namePresence, name, UsuarioStageDisposition.VALID, null);
    }

    public static UsuarioStageRecord quarantine(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final String reasonCode) {
        return new UsuarioStageRecord(
                inputOrdinal,
                sourceKey,
                null,
                null,
                UsuarioStageDisposition.QUARANTINE,
                reasonCode);
    }

    public int inputOrdinal() {
        return inputOrdinal;
    }

    public ScopedSourceIdentity.SourceKey sourceKey() {
        return sourceKey;
    }

    public UsuarioNamePresence namePresence() {
        return namePresence;
    }

    public String name() {
        return name;
    }

    public UsuarioStageDisposition disposition() {
        return disposition;
    }

    public String quarantineReasonCode() {
        return quarantineReasonCode;
    }

    @Override
    public String toString() {
        return "UsuarioStageRecord[inputOrdinal="
                + inputOrdinal
                + ", disposition="
                + disposition
                + ", sourceKey=<redacted>, name=<redacted>]";
    }

    private static void validateName(final UsuarioNamePresence presence, final String value) {
        if (presence == UsuarioNamePresence.VALUE) {
            if (value == null
                    || value.length() > MAXIMUM_NAME_CHARACTERS
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                    || value.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("O atributo name de Usuários é inválido.");
            }
            return;
        }
        if (value != null) {
            throw new IllegalArgumentException(
                    "Name ausente ou nulo não pode transportar um valor textual.");
        }
    }
}
