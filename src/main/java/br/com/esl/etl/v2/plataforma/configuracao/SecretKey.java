package br.com.esl.etl.v2.plataforma.configuracao;

/** Referências fixas a segredos de runtime; o valor nunca entra no arquivo de configuração. */
public enum SecretKey {
    DATA_EXPORT_TOKEN("V2_DATAEXPORT_TOKEN"),
    GRAPHQL_TOKEN("V2_GRAPHQL_TOKEN");

    private final String environmentVariable;

    SecretKey(final String environmentVariable) {
        this.environmentVariable = environmentVariable;
    }

    String environmentVariable() {
        return environmentVariable;
    }
}
