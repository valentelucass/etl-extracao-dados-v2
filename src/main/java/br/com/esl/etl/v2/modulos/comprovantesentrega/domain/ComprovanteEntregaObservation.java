package br.com.esl.etl.v2.modulos.comprovantesentrega.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.util.Map;

/** Physical row of an archive and its freight, retaining nullable and literal source values. */
public record ComprovanteEntregaObservation(Map<String, ExpansionValue<?>> fields)
        implements ExpansionObservation {
    public ComprovanteEntregaObservation {
        fields = Map.copyOf(fields);
    }

    @Override
    public String vertical() {
        return "COMPROVANTES_ENTREGA";
    }

    @Override
    public boolean valid() {
        final var archive = fields.get("sequence_code");
        return archive != null
                && archive.value() instanceof String number
                && number.matches("[1-9][0-9]{0,37}")
                && fields.values().stream().allMatch(ExpansionValue::valid);
    }

    @Override
    public String toString() {
        return "ComprovanteEntregaObservation[redacted]";
    }
}
