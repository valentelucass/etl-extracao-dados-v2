package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Test-only access to the exact parser used by the Data Export gateway. */
public final class CharacterizationParserAccess {
    private CharacterizationParserAccess() {}

    public static JsonNode parse(final String document) throws JsonProcessingException {
        return DataExportStrictJsonParser.readTree(document, 4_096);
    }

    /**
     * Called only after strict validation; slices preserve numeric wire lexemes per bounded page.
     */
    public static List<String> rowDocuments(final String document) throws IOException {
        final List<String> rows = new ArrayList<>();
        try (var parser = new JsonFactory().createParser(document)) {
            parser.nextToken();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                final String field = parser.currentName();
                parser.nextToken();
                if ("data".equals(field)) {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        final int start =
                                Math.toIntExact(parser.currentTokenLocation().getCharOffset());
                        parser.skipChildren();
                        parser.finishToken();
                        final int end = Math.toIntExact(parser.currentLocation().getCharOffset());
                        rows.add(document.substring(start, end));
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }
        return List.copyOf(rows);
    }
}
