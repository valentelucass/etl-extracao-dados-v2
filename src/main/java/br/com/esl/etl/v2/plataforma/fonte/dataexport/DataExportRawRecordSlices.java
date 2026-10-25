package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** ROOT_ARRAY only. Called after strict tree/contract validation; never reconstructs a token. */
final class DataExportRawRecordSlices {
    private static final int MAXIMUM_RECORD_CHARACTERS = 1_048_576;
    private static final JsonFactory FACTORY =
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(35)
                                    .maxNameLength(256)
                                    .maxNumberLength(1_000)
                                    .maxStringLength(20_000_000)
                                    .build())
                    .build();

    private final String document;
    private final List<Span> spans;

    private DataExportRawRecordSlices(final String document, final List<Span> spans) {
        this.document = document;
        this.spans = List.copyOf(spans);
    }

    static DataExportRawRecordSlices capture(
            final String document, final int recordCount, final int maximumNodes)
            throws IOException {
        Objects.requireNonNull(document);
        if (maximumNodes < 1
                || maximumNodes > ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES
                || recordCount < 0
                || recordCount > maximumNodes) {
            throw new IllegalArgumentException("RAW_RECORD_NODE_BOUND");
        }
        final var spans = new ArrayList<Span>();
        int depth = 1;
        int nodes = 1;
        int start = -1;
        try (var parser = FACTORY.createParser(document)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("RAW_RECORD_ROOT_ARRAY_REQUIRED");
            }
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (depth == 1) {
                    if (token == JsonToken.END_ARRAY) {
                        depth = 0;
                        break;
                    }
                    if (token != JsonToken.START_OBJECT) {
                        throw new IllegalArgumentException("RAW_RECORD_OBJECT_REQUIRED");
                    }
                    start = Math.toIntExact(parser.currentTokenLocation().getCharOffset());
                }
                if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                    depth++;
                    nodes++;
                } else if (token.isScalarValue()) {
                    nodes++;
                } else if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
                    depth--;
                    if (depth == 1) {
                        final int end = Math.toIntExact(parser.currentLocation().getCharOffset());
                        if (start < 0
                                || end <= start
                                || end > document.length()
                                || end - start > MAXIMUM_RECORD_CHARACTERS
                                || document.charAt(start) != '{'
                                || document.charAt(end - 1) != '}'
                                || (!spans.isEmpty()
                                        && start < spans.get(spans.size() - 1).end())) {
                            throw new IllegalArgumentException("RAW_RECORD_CHARACTER_BOUND");
                        }
                        spans.add(new Span(start, end));
                        if (spans.size() > recordCount) {
                            throw new IllegalArgumentException("RAW_RECORD_COUNT_MISMATCH");
                        }
                    }
                }
                if (nodes > maximumNodes) {
                    throw new IllegalArgumentException("RAW_RECORD_NODE_BOUND");
                }
            }
            if (depth != 0 || parser.nextToken() != null || spans.size() != recordCount) {
                throw new IllegalArgumentException("RAW_RECORD_DOCUMENT_MISMATCH");
            }
        }
        return new DataExportRawRecordSlices(document, spans);
    }

    int size() {
        return spans.size();
    }

    String record(final int index) {
        final Span span = spans.get(index);
        return document.substring(span.start(), span.end());
    }

    private record Span(int start, int end) {}
}
