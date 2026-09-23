package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.put;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import java.util.Map;
import java.util.TreeMap;

final class UsuariosProjection {
    private UsuariosProjection() {}

    static Map<String, String> observe(final UsuarioStageRecord record) {
        final Map<String, String> values = new TreeMap<>();
        put(
                values,
                "/quarantine",
                record.quarantineReasonCode() == null ? "NONE" : record.quarantineReasonCode());
        put(values, "/disposition", record.disposition());
        put(
                values,
                "/id/typed",
                record.sourceKey() == null ? null : record.sourceKey().storageValue());
        put(
                values,
                "/id/wireType",
                record.sourceKey() == null ? "ABSENT" : record.sourceKey().wireType());
        put(values, "/name/presence", record.namePresence());
        put(values, "/name/value", record.name());
        return values;
    }
}
