package br.com.esl.etl.v2.contratos.medicao;

import java.util.Objects;

/** Plano fechado para uma única execução sintética multiescala. */
public record MeasurementPlan(StreamerKind streamer, int dataPages, int recordsPerPage) {

    public MeasurementPlan {
        Objects.requireNonNull(streamer, "O streamer da medição é obrigatório.");
        if (dataPages != 16 && dataPages != 256 && dataPages != 4096) {
            throw new IllegalArgumentException("A escala de medição não pertence à allowlist.");
        }
        if (recordsPerPage != 8) {
            throw new IllegalArgumentException(
                    "A medição exige exatamente oito registros por página.");
        }
    }

    public static MeasurementPlan dataExport(final int dataPages) {
        return new MeasurementPlan(StreamerKind.DATA_EXPORT, dataPages, 8);
    }

    public static MeasurementPlan graphQl(final int dataPages) {
        return new MeasurementPlan(StreamerKind.GRAPHQL, dataPages, 8);
    }

    public String streamerName() {
        return streamer.displayName();
    }

    public int expectedFetchedPages() {
        return streamer == StreamerKind.DATA_EXPORT ? Math.incrementExact(dataPages) : dataPages;
    }

    public int expectedConsumedPages() {
        return dataPages;
    }

    public long expectedRecords() {
        return Math.multiplyExact((long) dataPages, recordsPerPage);
    }

    /** Únicos streamers produtivos exercitados pela fundação local. */
    public enum StreamerKind {
        DATA_EXPORT("DataExportPageStreamer"),
        GRAPHQL("GraphQlPageStreamer");

        private final String displayName;

        StreamerKind(final String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }
}
