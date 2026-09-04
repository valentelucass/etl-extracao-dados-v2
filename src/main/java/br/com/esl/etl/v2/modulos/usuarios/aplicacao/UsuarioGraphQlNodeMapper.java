package br.com.esl.etl.v2.modulos.usuarios.aplicacao;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioNamePresence;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityCatalog;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.IdentityQuarantineException;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Mapeia um node por vez, preservando tipo da chave e presença de {@code name}. */
public final class UsuarioGraphQlNodeMapper {

    private static final FirstWaveIdentityContract IDENTITY_CONTRACT =
            FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.USUARIOS);

    public UsuarioGraphQlNodeMapper() {}

    public UsuarioStageRecord map(final int inputOrdinal, final JsonNode node) {
        Objects.requireNonNull(node, "O node de Usuários é obrigatório.");
        final ScopedSourceIdentity.SourceKey sourceKey;
        try {
            sourceKey =
                    ScopedSourceIdentity.SourceKey.fromJson(
                            IDENTITY_CONTRACT.sourceKey().wireTypes(), node.get("id"));
        } catch (final IdentityQuarantineException exception) {
            return UsuarioStageRecord.quarantine(inputOrdinal, null, exception.reason().name());
        }

        if (!node.has("name")) {
            return UsuarioStageRecord.valid(
                    inputOrdinal, sourceKey, UsuarioNamePresence.ABSENT, null);
        }
        final JsonNode rawName = node.get("name");
        if (rawName == null || rawName.isNull()) {
            return UsuarioStageRecord.valid(
                    inputOrdinal, sourceKey, UsuarioNamePresence.NULL, null);
        }
        if (!rawName.isTextual()) {
            return UsuarioStageRecord.quarantine(inputOrdinal, sourceKey, "INVALID_NAME_TYPE");
        }
        final String name = rawName.textValue();
        if (name.length() > UsuarioStageRecord.MAXIMUM_NAME_CHARACTERS
                || !StandardCharsets.UTF_8.newEncoder().canEncode(name)
                || name.codePoints().anyMatch(Character::isISOControl)) {
            return UsuarioStageRecord.quarantine(inputOrdinal, sourceKey, "INVALID_NAME_VALUE");
        }
        return UsuarioStageRecord.valid(inputOrdinal, sourceKey, UsuarioNamePresence.VALUE, name);
    }
}
