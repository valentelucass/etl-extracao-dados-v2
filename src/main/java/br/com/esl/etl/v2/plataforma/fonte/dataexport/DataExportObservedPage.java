package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** In-memory observation. Raw values must only go to the caller's scoped staging transaction. */
public final class DataExportObservedPage {
    private final DataExportTemplate template;
    private final DataExportPageResponse page;
    private final int distinctRoots;
    private final int infoStatus;
    private final int dataStatus;

    DataExportObservedPage(
            final DataExportObservationRequest request,
            final List<JsonNode> records,
            final int maximumRows,
            final int infoStatus,
            final int dataStatus) {
        final String root = request.paginationRoot();
        if (records.size() > maximumRows) {
            throw new IllegalArgumentException("OBS_ROW_BOUND");
        }
        final var roots = new HashSet<String>();
        for (final JsonNode record : records) {
            if (record == null
                    || !record.isObject()
                    || record.has("error")
                    || record.has("errors")) {
                throw new IllegalArgumentException("OBS_RECORD_INVALID");
            }
            final JsonNode value = record.get(root);
            if (value == null
                    || (!value.isTextual() && !value.isIntegralNumber())
                    || value.asText().isBlank()
                    || value.asText().length() > 256) {
                throw new IllegalArgumentException("ROOT_PER_UNVERIFIED");
            }
            roots.add(value.getNodeType().name() + ":" + value.asText());
            if (roots.size() > request.page().pageSize()) {
                throw new IllegalArgumentException("OBS_PER_EXCEEDED");
            }
        }
        template = request.page().template();
        page = new DataExportPageResponse(records);
        distinctRoots = roots.size();
        this.infoStatus = infoStatus;
        this.dataStatus = dataStatus;
    }

    public DataExportTemplate template() {
        return template;
    }

    public List<JsonNode> records() {
        return page.records();
    }

    public int physicalRows() {
        return page.records().size();
    }

    public int distinctRoots() {
        return distinctRoots;
    }

    public int infoStatus() {
        return infoStatus;
    }

    public int dataStatus() {
        return dataStatus;
    }

    public int calls() {
        return 2;
    }

    public boolean canonicalIdentityProven() {
        return false;
    }

    public boolean sourceCompletenessProven() {
        return false;
    }

    @Override
    public String toString() {
        return "DataExportObservedPage[template=" + template + ", rows=" + physicalRows() + "]";
    }

    /** TEST-OBS-PHYSICAL-001 bounds an entire response, without a root or per claim. */
    public static final class PhysicalObservation {
        private final DataExportTemplate template;
        private final DataExportPageResponse page;
        private final int associatedItems;
        private final int infoStatus;
        private final int dataStatus;

        PhysicalObservation(
                final DataExportObservationRequest request,
                final JsonNode response,
                final DataExportTemplateInfoParser.ParsedTemplateInfo metadata,
                final int infoStatus,
                final int dataStatus) {
            template =
                    Objects.requireNonNull(request, "PHYSICAL_REQUEST_REQUIRED").page().template();
            requireTemplate(template);
            Objects.requireNonNull(metadata, "PHYSICAL_METADATA_REQUIRED");
            final JsonNode records;
            if (response != null
                    && response.isArray()
                    && template == DataExportTemplate.MANIFESTOS) {
                records = response;
            } else if (response != null
                    && response.isObject()
                    && response.size() == 1
                    && response.has("data")
                    && response.get("data").isArray()) {
                records = response.get("data");
            } else {
                throw new IllegalArgumentException("PHYSICAL_ENVELOPE_INVALID");
            }
            if (records.size() > 1) {
                throw new IllegalArgumentException("PHYSICAL_ROW_BOUND");
            }
            final Set<String> declared = new HashSet<>();
            metadata.fields().forEach(field -> declared.add(field.technicalName()));
            int associated = 0;
            if (!records.isEmpty()) {
                final JsonNode row = records.get(0);
                if (!row.isObject() || row.isEmpty() || row.has("error") || row.has("errors")) {
                    throw new IllegalArgumentException("PHYSICAL_RECORD_INVALID");
                }
                final var fields = row.fields();
                while (fields.hasNext()) {
                    final var field = fields.next();
                    if (!declared.contains(field.getKey())) {
                        throw new IllegalArgumentException("PHYSICAL_FIELD_UNDECLARED");
                    }
                    final JsonNode value = field.getValue();
                    if (value.isObject()) {
                        throw new IllegalArgumentException("PHYSICAL_NESTED_OBJECT_REFUSED");
                    }
                    if (knownAssociation(template, field.getKey())
                            && !value.isArray()
                            && !value.isNull()) {
                        throw new IllegalArgumentException("PHYSICAL_ASSOCIATED_SHAPE");
                    }
                    if (value.isArray()) {
                        if (!knownAssociation(template, field.getKey())) {
                            throw new IllegalArgumentException("PHYSICAL_COLLECTION_UNKNOWN");
                        }
                        associated += value.size();
                        if (associated > 1) {
                            throw new IllegalArgumentException("PHYSICAL_ASSOCIATED_BOUND");
                        }
                        for (final JsonNode item : value) {
                            if (!item.isTextual() || item.textValue().length() > 256) {
                                throw new IllegalArgumentException("PHYSICAL_ASSOCIATED_SHAPE");
                            }
                        }
                    }
                }
            }
            associatedItems = associated;
            page =
                    new DataExportPageResponse(
                            records.isEmpty() ? List.of() : List.of(records.get(0)));
            this.infoStatus = infoStatus;
            this.dataStatus = dataStatus;
        }

        static void requireTemplate(final DataExportTemplate template) {
            if (template == null
                    || template == DataExportTemplate.COLETAS
                    || template == DataExportTemplate.FRETES) {
                throw new IllegalArgumentException("PHYSICAL_TEMPLATE_REFUSED");
            }
        }

        private static boolean knownAssociation(
                final DataExportTemplate template, final String name) {
            return switch (template) {
                case FATURAS_POR_CLIENTE ->
                        name.equals("invoices_mapping")
                                || name.equals("fit_fte_invoices_order_number");
                case INVENTARIO -> name.equals("cnr_c_s_fit_invoices_mapping");
                case SINISTROS ->
                        name.equals("rcfdc") || name.equals("rctac") || name.equals("rctrc");
                default -> false;
            };
        }

        public DataExportTemplate template() {
            return template;
        }

        public List<JsonNode> records() {
            return page.records();
        }

        public int physicalRows() {
            return page.recordCount();
        }

        public int associatedItems() {
            return associatedItems;
        }

        public int infoStatus() {
            return infoStatus;
        }

        public int dataStatus() {
            return dataStatus;
        }

        public int calls() {
            return 2;
        }

        public String mode() {
            return "TEST-OBS-PHYSICAL-001";
        }

        public Verification sourceEntities() {
            return Verification.UNVERIFIED;
        }

        public Verification remotePer() {
            return Verification.UNVERIFIED;
        }

        public boolean paginationRootProven() {
            return false;
        }

        public boolean canonicalIdentityProven() {
            return false;
        }

        public boolean sourceCompletenessProven() {
            return false;
        }

        public PhysicalStatus status() {
            return physicalRows() == 0
                    ? PhysicalStatus.NO_DATA_OBSERVED
                    : PhysicalStatus.PHYSICAL_ROW_OBSERVED;
        }

        @Override
        public String toString() {
            return "PhysicalObservation[template="
                    + template
                    + ", rows="
                    + physicalRows()
                    + ", associatedItems="
                    + associatedItems
                    + ", sourceEntities=UNVERIFIED, remotePer=UNVERIFIED]";
        }
    }

    public enum Verification {
        UNVERIFIED
    }

    public enum PhysicalStatus {
        NO_DATA_OBSERVED,
        PHYSICAL_ROW_OBSERVED
    }
}
