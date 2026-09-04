package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Metadados seguros de uma tentativa HTTP, sem URI, headers ou corpo. */
public record DataExportHttpAttempt(int templateId, String operation) {

    public DataExportHttpAttempt {
        if (templateId <= 0) {
            throw new IllegalArgumentException("O template Data Export deve ser positivo.");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("A operação HTTP é obrigatória.");
        }
        operation = operation.trim();
    }
}
