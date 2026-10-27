package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Four pinned observed wire profiles, without a source key or operational contract permit. */
public final class ObservedExpansionSchemaCatalog {
    private static final int MAXIMUM_RESOURCE_BYTES = 65_536;

    private ObservedExpansionSchemaCatalog() {}

    public static final class Selection {
        private final ObservedExpansionCaptureTemplate template;
        private final ObservedExpansionSchemaPins.Pin pin;

        private Selection(final ObservedExpansionCaptureTemplate template) {
            this.template = Objects.requireNonNull(template);
            this.pin = ObservedExpansionSchemaPins.pin(template);
        }

        public ObservedExpansionCaptureTemplate template() {
            return template;
        }

        public int templateId() {
            return pin.templateId();
        }

        public String schemaRelease() {
            return pin.release();
        }

        public String schemaResourceSha256() {
            return pin.sha256();
        }

        public String resource() {
            return pin.resource();
        }

        @Override
        public String toString() {
            return "ObservedExpansionSchemaSelection[template="
                    + templateId()
                    + ",release="
                    + schemaRelease()
                    + ",scope=WIRE_ONLY]";
        }
    }

    public static Selection select(final ObservedExpansionCaptureTemplate template) {
        return new Selection(template);
    }

    enum Wire {
        NULL,
        STRING,
        BOOLEAN,
        INTEGER,
        NUMBER,
        ARRAY
    }

    record Field(
            String path,
            boolean required,
            Set<Wire> wireTypes,
            boolean unknownNonNull,
            Set<Wire> arrayItemTypes,
            boolean historicalGenericNumber) {
        Field {
            Objects.requireNonNull(path);
            wireTypes = Set.copyOf(wireTypes);
            arrayItemTypes = Set.copyOf(arrayItemTypes);
            if (!path.matches("[a-z][a-z0-9_]{0,127}")
                    || wireTypes.isEmpty()
                    || unknownNonNull != wireTypes.equals(Set.of(Wire.NULL))
                    || wireTypes.contains(Wire.ARRAY) != !arrayItemTypes.isEmpty()
                    || (!arrayItemTypes.isEmpty() && !arrayItemTypes.equals(Set.of(Wire.STRING)))
                    || (historicalGenericNumber
                            && !(wireTypes.contains(Wire.INTEGER)
                                    && wireTypes.contains(Wire.NUMBER)))) {
                throw new IllegalStateException("OBS_EXP_SCHEMA_FIELD_UNCLOSED");
            }
        }
    }

    static final class Schema {
        private final Selection selection;
        private final ContractMetadata metadata;
        private final Map<String, Field> fields;

        Schema(
                final Selection selection,
                final ContractMetadata metadata,
                final Map<String, Field> fields) {
            this.selection = selection;
            this.metadata = metadata;
            this.fields = Map.copyOf(fields);
        }

        Selection selection() {
            return selection;
        }

        ContractMetadata metadata() {
            return metadata;
        }

        Map<String, Field> fields() {
            return fields;
        }
    }

    static Schema schema(final Selection selection) {
        Objects.requireNonNull(selection);
        try (InputStream input =
                ObservedExpansionSchemaCatalog.class.getResourceAsStream(selection.resource())) {
            return read(selection, input);
        } catch (final IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("OBS_EXP_SCHEMA_RESOURCE_IO", failure);
        }
    }

    /** Offline injection point: expected version/hash always come from code-owned pins. */
    static Schema read(final Selection selection, final InputStream input)
            throws IOException, NoSuchAlgorithmException {
        Objects.requireNonNull(selection);
        if (input == null) {
            throw new IllegalStateException("OBS_EXP_SCHEMA_RESOURCE_MISSING");
        }
        final var pin = ObservedExpansionSchemaPins.pin(selection.template());
        final byte[] bytes = input.readNBytes(MAXIMUM_RESOURCE_BYTES + 1);
        if (bytes.length > MAXIMUM_RESOURCE_BYTES
                || !pin.sha256()
                        .equals(
                                HexFormat.of()
                                        .formatHex(
                                                MessageDigest.getInstance("SHA-256")
                                                        .digest(bytes)))) {
            throw new IllegalStateException("OBS_EXP_SCHEMA_RESOURCE_HASH");
        }
        final String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
        final JsonNode root = DataExportStrictJsonParser.readTree(text, 20_000);
        if (!root.isObject()
                || root.size() != 9
                || !"observed-expansion-wire-v1".equals(root.path("formatVersion").asText())
                || !pin.release().equals(root.path("schemaRelease").asText())
                || !root.path("templateId").isInt()
                || root.path("templateId").intValue() != pin.templateId()
                || !"DATA_EXPORT".equals(root.path("sourceKind").asText())
                || !"$".equals(root.path("recordRoot").asText())
                || !"ARRAY".equals(root.path("rootCardinality").asText())
                || !root.path("metadata").isArray()
                || !root.path("fields").isArray()
                || !root.path("evidence").isObject()) {
            throw new IllegalStateException("OBS_EXP_SCHEMA_RESOURCE_SELECTION");
        }
        final var elements = new ArrayList<ContractMetadata.Element>();
        for (final JsonNode node : root.path("metadata")) {
            final var kind = ContractMetadata.ElementKind.valueOf(node.path("kind").asText());
            final var type =
                    ContractMetadata.DeclaredType.valueOf(node.path("declaredType").asText());
            if (kind != ContractMetadata.ElementKind.DATA_FIELD
                            && kind != ContractMetadata.ElementKind.DATA_FILTER
                    || kind == ContractMetadata.ElementKind.DATA_FIELD
                            && type != ContractMetadata.DeclaredType.UNDECLARED) {
                throw new IllegalStateException("OBS_EXP_SCHEMA_METADATA_KIND");
            }
            final JsonNode hash = node.path("declaredTypeFingerprint");
            elements.add(
                    new ContractMetadata.Element(
                            kind,
                            node.path("path").asText(),
                            type,
                            hash.isObject()
                                    ? Optional.of(
                                            new ImmutableFingerprint(
                                                    hash.path("version").asText(),
                                                    hash.path("sha256").asText()))
                                    : Optional.empty()));
        }
        final var metadata = new ContractMetadata(elements, Optional.empty());
        final Map<String, Field> fields = new LinkedHashMap<>();
        for (final JsonNode node : root.path("fields")) {
            if (!node.path("required").isBoolean()
                    || !node.path("unknownNonNull").isBoolean()
                    || !node.path("historicalGenericNumber").isBoolean()) {
                throw new IllegalStateException("OBS_EXP_SCHEMA_FIELD_FLAGS");
            }
            final var field =
                    new Field(
                            node.path("path").asText(),
                            node.path("required").booleanValue(),
                            wires(node.path("wireTypes")),
                            node.path("unknownNonNull").booleanValue(),
                            wires(node.path("arrayItemTypes")),
                            node.path("historicalGenericNumber").booleanValue());
            if (fields.put(field.path(), field) != null) {
                throw new IllegalStateException("OBS_EXP_SCHEMA_DUPLICATE_FIELD");
            }
        }
        if (fields.size() != pin.fields()
                || metadata.elements().size() != pin.fields() + pin.filters()
                || metadata.elements().stream()
                                .filter(e -> e.kind() == ContractMetadata.ElementKind.DATA_FIELD)
                                .count()
                        != pin.fields()
                || metadata.elements().stream()
                        .filter(e -> e.kind() == ContractMetadata.ElementKind.DATA_FIELD)
                        .anyMatch(e -> !fields.containsKey(e.path()))) {
            throw new IllegalStateException("OBS_EXP_SCHEMA_RESOURCE_CLOSURE");
        }
        return new Schema(selection, metadata, fields);
    }

    private static Set<Wire> wires(final JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalStateException("OBS_EXP_SCHEMA_WIRES_ARRAY_REQUIRED");
        }
        final Set<Wire> values = EnumSet.noneOf(Wire.class);
        for (final JsonNode item : node) {
            if (!item.isTextual() || !values.add(Wire.valueOf(item.textValue()))) {
                throw new IllegalStateException("OBS_EXP_SCHEMA_WIRE_DUPLICATE_OR_TYPE");
            }
        }
        return values;
    }
}
