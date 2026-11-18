package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;

/** Root's independent differential runner supplies only exact tagged inputs. */
public final class Original129JavaPort implements Original129Differential.JavaPort {
    public Original129JavaPort() {}

    @Override
    public JsonNode evaluate(final String operation, final JsonNode exactTaggedArguments)
            throws Exception {
        return QualifiedColetasModel.evaluate(operation, exactTaggedArguments);
    }
}
