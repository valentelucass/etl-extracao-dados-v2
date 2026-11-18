package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Candidate in-process join of captured fields and scoped raw references; no preassembled fact
 * table.
 */
final class LegacyReferenceGovernanceAssembler {
    private LegacyReferenceGovernanceAssembler() {}

    static JsonNode derive(
            final JsonNode pair,
            final JsonNode recipe,
            final ObjectNode source,
            final JsonNode context,
            final ExportValidationSupport.Pin contextPin,
            final ExportValidationSupport.Pin capturePin,
            final Path provenancePath,
            final Consumer<ExportValidationSupport.Pin> track,
            final Map<String, JsonNode> referenceCache,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        JsonNode request = referenceCache.get("__request");
        if (request == null) {
            request =
                    ExportValidationSupport.Pin.from(pair.required("requestPin"))
                            .json(262144, budget);
            referenceCache.put("__request", request);
        }
        final String key = source.required("id").bigIntegerValue().toString();
        final var identityPin =
                ExportValidationSupport.Pin.from(context.required("legacyIdentityBindingPin"));
        JsonNode identity = referenceCache.get("__identity");
        if (identity == null) {
            identity = identityPin.json(262144, budget);
            track.accept(identityPin);
            referenceCache.put("__identity", identity);
        }
        require(
                "DATAEXPORT_LEGACY_IDENTITY_BINDING_V1".equals(text(identity, "schema"))
                        && context.required("idNamespace")
                                .equals(identity.required("sourceNamespace"))
                        && pair.required("sourceConfigurationSha256")
                                .equals(identity.required("sourceConfigurationSha256"))
                        && pair.required("mappingPin").equals(identity.required("mappingPin")),
                "DASHBOARD_LEGACY_IDENTITY_EQUIVALENCE_PENDING");
        final var lifecycle = dataset("lifecycle", context, pair, track, referenceCache, budget);
        final var lifecycleRow = one(lifecycle, "sourceKey", key);
        require(
                lifecycleRow.required("excludedAtSource").isBoolean(),
                "DASHBOARD_EXPLICIT_EXCLUSION_REFERENCE_REQUIRED");
        final var publication =
                dataset("publication", context, pair, track, referenceCache, budget);
        final var publicationRow =
                one(
                        publication,
                        "requestSha256",
                        ExportValidationSupport.Pin.from(pair.required("requestPin")).sha256());
        java.time.Instant.parse(text(publicationRow, "snapshotUtc"));
        require(
                "ACTUAL_STAGE_PREPARATION_CLOCK".equals(text(publicationRow, "eventKind")),
                "DASHBOARD_REAL_STAGE_CLOCK_REFERENCE_REQUIRED");
        final var governance =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_DASHBOARD_GOVERNANCE_V1")
                        .put("sourceKey", key)
                        .put(
                                "excludedAtSource",
                                lifecycleRow.get("excludedAtSource").booleanValue())
                        .put("publicationSnapshotUtc", text(publicationRow, "snapshotUtc"));
        governance.set("exclusionEvidencePin", context.path("datasets").required("lifecycle"));
        governance.set("snapshotEvidencePin", context.path("datasets").required("publication"));
        if (ColetasIndependentRequest.PROFILE.equals(text(request, "profile"))) {
            require(
                    lifecycleRow.required("confirmedAbsences").isIntegralNumber(),
                    "DASHBOARD_REAL_ABSENCE_CONFIRMATION_REFERENCE");
            governance.put(
                    "statusExcluded",
                    lifecycleRow.get("excludedAtSource").booleanValue()
                            || lifecycleRow.get("confirmedAbsences").bigIntegerValue().signum()
                                    > 0);
            final var dimension =
                    dataset("logisticsRegions", context, pair, track, referenceCache, budget);
            final String rawCep = nullableText(source, "pck_pds_postal_code");
            final String cep =
                    rawCep == null ? "" : rawCep.replace("-", "").replace(".", "").replace(" ", "");
            final String city = trim(nullableText(source, "pck_pds_cty_name"));
            final String state = trim(nullableText(source, "pck_pds_cty_sae_code"));
            final List<JsonNode> candidates = new ArrayList<>();
            if (cep.matches("[0-9]{8}")) {
                for (final var rule : dimension.required("records")) {
                    if (!rule.path("cep_inicio").isNull()
                            && rule.has("cep_inicio")
                            && rule.has("cep_fim")
                            && cep.compareTo(text(rule, "cep_inicio")) >= 0
                            && cep.compareTo(text(rule, "cep_fim")) <= 0) {
                        candidates.add(rule);
                    }
                }
                candidates.sort(
                        Comparator.comparing((JsonNode row) -> text(row, "cep_inicio"))
                                .reversed()
                                .thenComparing(row -> text(row, "cep_fim"))
                                .thenComparingLong(row -> row.required("id").longValue()));
            }
            if (candidates.isEmpty() && city != null && state != null) {
                throw new IllegalArgumentException(
                        "DASHBOARD_CITY_COLLATION_OR_REGION_REFERENCE_PENDING");
            }
            require(!candidates.isEmpty(), "DASHBOARD_LOGISTICS_REGION_REFERENCE_PENDING");
            final var region =
                    governance
                            .putObject("logisticsRegion")
                            .put("sourceKey", key)
                            .put("name", text(candidates.get(0), "regiao_logistica"))
                            .put("validFrom", text(request, "periodStart"))
                            .put("validThrough", text(request, "periodEnd"))
                            .put("validityBasis", "REFERENCE_SNAPSHOT_ADMITTED_FOR_THIS_REQUEST");
            region.set("postalCode", source.required("pck_pds_postal_code"));
            region.set("city", source.required("pck_pds_cty_name"));
            region.set("state", source.required("pck_pds_cty_sae_code"));
            region.set("evidencePin", context.path("datasets").required("logisticsRegions"));
            final var manifestos =
                    dataset("manifestos", context, pair, track, referenceCache, budget);
            final long sequence = integral(source, "sequence_code");
            final List<JsonNode> manifests = new ArrayList<>();
            for (final var row : manifestos.required("records")) {
                if (integral(row, "pick_sequence_code") == sequence
                        && !bool(row, "excludedAtSource")) {
                    manifests.add(row);
                }
            }
            manifests.sort(
                    Comparator.comparingLong((JsonNode row) -> integral(row, "sequence_code"))
                            .reversed()
                            .thenComparing(
                                    Comparator.comparingLong((JsonNode row) -> integral(row, "id"))
                                            .reversed()));
            if (manifests.isEmpty()) {
                governance.putNull("canonicalManifestSequence");
            } else {
                governance.set(
                        "canonicalManifestSequence", manifests.get(0).required("sequence_code"));
            }
        } else {
            final var raw =
                    one(
                            dataset("rawFretes", context, pair, track, referenceCache, budget),
                            "sourceKey",
                            key);
            final var documents = raw.required("documents");
            final var issuedAtom = source.required("fit_fhe_cte_issued_at");
            final var issued = documents.required("cteIssuedAt");
            if ("NUMBER".equals(issuedAtom.path("type").asText()) && issued.isTextual()) {
                require(
                        LocalDate.parse(
                                        QualifiedColetasModel.nativeCivil(
                                                text(issuedAtom, "value")))
                                .equals(OffsetDateTime.parse(issued.textValue()).toLocalDate()),
                        "FREIGHT_CTE_REFERENCE_CIVIL_BINDING");
            } else {
                require(
                        List.of("ABSENT", "BLANK").contains(issuedAtom.path("type").asText())
                                && issued.isNull(),
                        "FREIGHT_CTE_TIMESTAMP_MODEL_PENDING");
            }
            final var actualReference =
                    OffsetDateTime.parse(
                            issued.isNull() ? text(source, "service_at") : issued.textValue());
            final var calendar =
                    one(
                            dataset("calendar", context, pair, track, referenceCache, budget),
                            "sourceDay",
                            actualReference.toLocalDate().toString());
            final var locations =
                    dataset("localizacao", context, pair, track, referenceCache, budget);
            final var location =
                    one(
                            locations,
                            "sequenceNumber",
                            source.required("corporation_sequence_number").asText());
            require(
                    !bool(location, "excludedAtSource"),
                    "DASHBOARD_ACTIVE_LOCALIZACAO_REFERENCE_REQUIRED");
            final var fiscal =
                    one(
                            dataset("rankedFiscal", context, pair, track, referenceCache, budget),
                            "cteKey",
                            normalizeCte(nullableText(source, "fit_fhe_cte_key")));
            final var refs =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "LEGACY_FREIGHT_RAW_REFERENCES_V1")
                            .put("sourceKey", key);
            refs.set("lifecycle", lifecycleRow);
            refs.set("documents", documents);
            refs.set("fiscal", fiscal);
            refs.set("calendar", calendar);
            refs.set("localizacao", location);
            refs.set("rawFrete", raw.required("rawFrete"));
            final var derived = LegacyFreightBillingRules.derive(source, refs);
            governance.set("courtesy", derived.required("courtesy"));
            governance.set(
                    "responsavel_regiao_destino", derived.required("responsavel_regiao_destino"));
            governance.set(
                    "responsavel_regiao_destino_key",
                    derived.required("responsavel_regiao_destino_key"));
            governance.set("status_frete", source.required("status"));
            governance.put("tipo_frete", text(source, "type").replace("Freight::", ""));
            governance.set("modal", raw.required("modal"));
            final var volumes = location.required("invoicesVolumes");
            final var fallbackVolumes = source.required("invoices_volumes");
            governance.set(
                    "materializedVolumes",
                    volumes.isNull()
                            ? fallbackVolumes.isNull()
                                    ? ExportValidationSupport.JSON.getNodeFactory().numberNode(0)
                                    : fallbackVolumes
                            : volumes);
            final var assignments =
                    dataset("branchAssignment", context, pair, track, referenceCache, budget);
            final String payerKey =
                    text(source, "fit_pyr_document")
                            .replace(".", "")
                            .replace("-", "")
                            .replace("/", "")
                            .replace(" ", "")
                            .replace("\t", "")
                            .toUpperCase(Locale.ROOT);
            JsonNode assignment = null;
            for (final var rule : assignments.required("records")) {
                budget.check();
                if (payerKey.equals(rule.required("pagador_documento_key").asText())
                        && bool(rule, "ativo")) {
                    require(assignment == null, "DASHBOARD_BRANCH_RULE_MULTIPLICITY_PENDING");
                    assignment = rule;
                }
            }
            governance.set(
                    "materializedBranchName",
                    assignment == null || assignment.path("filial_destino_nome").isNull()
                            ? source.required("fit_crn_psn_nickname")
                            : assignment.required("filial_destino_nome"));
            final var billing =
                    governance
                            .putObject("billing")
                            .put("governedRevision", "VERSIONED_V1_RULE_PORT_UNAPPROVED");
            for (final String field :
                    List.of(
                            "referenceAt",
                            "grossRevenue",
                            "freightValue",
                            "eligible",
                            "cteId",
                            "cteNumber",
                            "cteIssuedAt")) {
                billing.set(field, derived.required(field));
            }
            billing.set("nfseNumber", source.required("nfse_number"));
            billing.set("evidencePin", ExportValidationSupport.JSON.valueToTree(contextPin));
        }
        final var provenance =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_REFERENCE_FACT_PROVENANCE_V1")
                        .put("originKind", "DERIVED_FROM_CAPTURE_AND_REFERENCE_SNAPSHOTS")
                        .put("authorityCreated", false)
                        .put("semanticApprovalIssued", false)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put(
                                "governanceSha256",
                                ExportValidationSupport.sha256(
                                        ExportValidationSupport.canonicalBytes(governance)));
        final var id = provenance.putObject("idAtom").put("type", "INTEGER").put("value", key);
        provenance.set("requestPin", pair.required("requestPin"));
        provenance.set("profile", request.required("profile"));
        provenance.set("sourceConfigurationSha256", pair.required("sourceConfigurationSha256"));
        provenance.set(
                "rawReferenceContextPin", ExportValidationSupport.JSON.valueToTree(contextPin));
        provenance.set("capturePin", ExportValidationSupport.JSON.valueToTree(capturePin));
        final var pin =
                CapturedDashboardInputProducer.writeJson(
                        provenancePath, provenance, 262144, budget);
        final var fact = ExportValidationSupport.JSON.createObjectNode();
        fact.set("idAtom", id);
        fact.set("governance", governance);
        fact.set("provenancePin", ExportValidationSupport.JSON.valueToTree(pin));
        return fact;
    }

    private static JsonNode dataset(
            final String kind,
            final JsonNode context,
            final JsonNode pair,
            final Consumer<ExportValidationSupport.Pin> track,
            final Map<String, JsonNode> referenceCache,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var pin = ExportValidationSupport.Pin.from(context.path("datasets").required(kind));
        JsonNode data = referenceCache.get(kind);
        if (data == null) {
            final long bytes =
                    referenceCache.containsKey("__bytes")
                            ? referenceCache.get("__bytes").longValue()
                            : 0;
            require(bytes + pin.bytes() <= 8388608, "DASHBOARD_TOTAL_RAW_REFERENCE_BYTES_BOUND");
            referenceCache.put(
                    "__bytes",
                    ExportValidationSupport.JSON.getNodeFactory().numberNode(bytes + pin.bytes()));
            data = pin.json(8388608, budget);
            track.accept(pin);
            referenceCache.put(kind, data);
        }
        require(
                "DASHBOARD_RAW_REFERENCE_DATASET_V1".equals(text(data, "schema"))
                        && kind.equals(text(data, "kind"))
                        && pair.required("requestPin").equals(data.required("requestPin"))
                        && data.required("completeForDeclaredJoinScope").isBoolean()
                        && data.get("completeForDeclaredJoinScope").booleanValue()
                        && data.path("records").isArray()
                        && data.get("records").size() <= 12000,
                "DASHBOARD_RAW_REFERENCE_DATASET_SCOPE_OR_COMPLETENESS");
        return data;
    }

    private static JsonNode one(final JsonNode data, final String keyField, final String key) {
        JsonNode found = null;
        for (final var row : data.required("records")) {
            if (key.equals(row.required(keyField).asText())) {
                require(found == null, "DASHBOARD_REFERENCE_JOIN_MULTIPLICITY_PENDING");
                found = row;
            }
        }
        require(found != null, "DASHBOARD_REQUIRED_RAW_REFERENCE_MISSING");
        return found;
    }

    private static String normalizeCte(final String value) {
        final String trimmed = trim(value);
        return trimmed == null
                ? ""
                : trimmed.replace(" ", "").replace("\t", "").toUpperCase(Locale.ROOT);
    }

    private static String nullableText(final JsonNode node, final String field) {
        final var value = node.required(field);
        require(value.isTextual() || value.isNull(), "DASHBOARD_RAW_REFERENCE_TEXT");
        return value.isNull() ? null : value.textValue();
    }

    private static long integral(final JsonNode node, final String field) {
        final var value = node.required(field);
        require(
                value.isIntegralNumber() && value.canConvertToLong(),
                "DASHBOARD_RAW_REFERENCE_EXACT_BIGINT");
        return value.longValue();
    }

    private static boolean bool(final JsonNode node, final String field) {
        require(node.required(field).isBoolean(), "DASHBOARD_RAW_REFERENCE_EXPLICIT_BOOLEAN");
        return node.get(field).booleanValue();
    }

    private static String trim(final String value) {
        if (value == null) {
            return null;
        }
        int from = 0;
        int to = value.length();
        while (from < to && value.charAt(from) == ' ') {
            from++;
        }
        while (to > from && value.charAt(to - 1) == ' ') {
            to--;
        }
        return from == to ? null : value.substring(from, to);
    }
}
