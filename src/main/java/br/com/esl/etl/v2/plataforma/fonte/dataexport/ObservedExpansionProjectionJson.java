package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Explicit bounded sidecar encoder. The received page bytes never pass through this encoder. */
final class ObservedExpansionProjectionJson {
    private static final JsonFactory FACTORY =
            JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    private ObservedExpansionProjectionJson() {}

    static String serialize(
            final ObservedExpansionCapturePlan plan,
            final List<ObservedExpansionProjectedRecord> records,
            final ObservedExpansionPageProjection.Issue issue) {
        final BoundedWriter output = new BoundedWriter(plan.limits().maximumProjectionBytes() / 2);
        try (JsonGenerator json = FACTORY.createGenerator(output)) {
            json.writeStartObject();
            json.writeStringField("projectionVersion", "observed-expansion-core-v1");
            json.writeStringField("sourceSchemaValidation", "NOT_PERFORMED_BY_CORE");
            json.writeStringField(
                    "mapperId",
                    plan.template().vertical()
                            + "@"
                            + ObservedExpansionCapturePlan.BASELINE_SHA256);
            json.writeNumberField("templateId", plan.template().templateId());
            if (issue == null) {
                json.writeNullField("pageIssue");
            } else {
                json.writeStringField("pageIssue", issue.name());
            }
            json.writeArrayFieldStart("records");
            for (final ObservedExpansionProjectedRecord record : records) {
                json.writeStartObject();
                json.writeNumberField("occurrence", record.occurrence());
                json.writeNumberField("pageOrdinal", record.pageOrdinal());
                json.writeStringField("bindingStatus", "UNBOUND");
                json.writeBooleanField("mapperValid", record.mapperValid());
                json.writeArrayFieldStart("fields");
                for (final ObservedExpansionProjectedField field : record.fields()) {
                    writeField(json, field);
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (final ProjectionOverflow failure) {
            throw new LimitExceeded();
        } catch (final IOException failure) {
            throw new IllegalStateException("OBS_CAPTURE_PROJECTION_ENCODING", failure);
        }
        return output.content();
    }

    private static void writeField(
            final JsonGenerator json, final ObservedExpansionProjectedField field)
            throws IOException {
        json.writeStartObject();
        json.writeStringField("path", field.path());
        json.writeStringField("presence", field.value().presence().name());
        json.writeStringField("wire", field.value().wire().name());
        if (field.value().raw() == null) {
            json.writeNullField("raw");
        } else {
            json.writeStringField("raw", field.value().raw());
        }
        json.writeStringField("typedKind", field.kind().name());
        json.writeFieldName("typed");
        final Object typed = field.value().value();
        if (typed == null) {
            json.writeNull();
        } else {
            switch (field.kind()) {
                case TEXT, INTEGER, IDENTIFIER -> json.writeString((String) typed);
                case DECIMAL -> json.writeString(((BigDecimal) typed).toPlainString());
                case BOOLEAN -> json.writeBoolean((Boolean) typed);
                case DATE, INSTANT, TIME -> json.writeString(typed.toString());
                case STRINGS -> {
                    json.writeStartArray();
                    for (final String item : ((ExpansionStrings) typed).items()) {
                        json.writeString(item);
                    }
                    json.writeEndArray();
                }
            }
        }
        if (field.value().issue() == null) {
            json.writeNullField("issue");
        } else {
            json.writeStringField("issue", field.value().issue());
        }
        json.writeEndObject();
    }

    static final class LimitExceeded extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        LimitExceeded() {
            super("OBS_CAPTURE_PROJECTION_BYTES");
        }
    }

    private static final class ProjectionOverflow extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class BoundedWriter extends Writer {
        private final int maximumCharacters;
        private final StringBuilder content = new StringBuilder();

        BoundedWriter(final int maximumCharacters) {
            this.maximumCharacters = maximumCharacters;
        }

        @Override
        public void write(final char[] chars, final int offset, final int length)
                throws IOException {
            Objects.checkFromIndexSize(offset, length, chars.length);
            ensureSpace(length);
            content.append(chars, offset, length);
        }

        @Override
        public void write(final String text, final int offset, final int length)
                throws IOException {
            Objects.checkFromIndexSize(offset, length, text.length());
            ensureSpace(length);
            content.append(text, offset, offset + length);
        }

        private void ensureSpace(final int length) throws ProjectionOverflow {
            if ((long) content.length() + length > maximumCharacters) {
                throw new ProjectionOverflow();
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        String content() {
            return content.toString();
        }
    }
}
