package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Indica que a resposta excedeu o teto de bytes antes de ser desserializada. */
public final class DataExportResponseLimitExceededException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final int templateId;
    private final long maxResponseBytes;
    private final long observedBytes;

    public DataExportResponseLimitExceededException(
            final int templateId, final long maxResponseBytes, final long observedBytes) {
        super(message(templateId, maxResponseBytes, observedBytes));
        this.templateId = validateTemplateId(templateId);
        this.maxResponseBytes =
                validatePositive(maxResponseBytes, "O limite de resposta deve ser positivo.");
        this.observedBytes =
                validatePositive(observedBytes, "O tamanho observado deve ser positivo.");
    }

    public int templateId() {
        return templateId;
    }

    public long maxResponseBytes() {
        return maxResponseBytes;
    }

    public long observedBytes() {
        return observedBytes;
    }

    private static String message(
            final int templateId, final long maxResponseBytes, final long observedBytes) {
        validateTemplateId(templateId);
        validatePositive(maxResponseBytes, "O limite de resposta deve ser positivo.");
        validatePositive(observedBytes, "O tamanho observado deve ser positivo.");
        return "A resposta Data Export do template "
                + templateId
                + " excedeu o limite de "
                + maxResponseBytes
                + " bytes.";
    }

    private static long validatePositive(final long value, final String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static int validateTemplateId(final int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("O template Data Export deve ser positivo.");
        }
        return value;
    }
}
