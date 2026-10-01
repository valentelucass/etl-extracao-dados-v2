package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/** Escopo fechado de travessia 6908. O plano nao e uma autorizacao de fonte ou SQL. */
public record Coletas6908PilotPlan(
        String sourceInstance,
        String tenantScope,
        LocalDate businessDate,
        int per,
        int maxPages,
        int maxPhysicalRows,
        int maxResponseBytes,
        Duration deadline) {

    private static final Set<String> FIELDS =
            Set.of(
                    "version",
                    "templateId",
                    "target",
                    "mode",
                    "sourceInstance",
                    "tenantScope",
                    "businessDate",
                    "page",
                    "per",
                    "maxPages",
                    "maxPhysicalRows",
                    "maxResponseBytes",
                    "deadlineSeconds");

    public Coletas6908PilotPlan {
        new ScopedSourceIdentity(
                sourceInstance,
                tenantScope,
                FirstWaveIdentityContract.Entity.COLETAS,
                new ScopedSourceIdentity.SourceKey(
                        ScopedSourceIdentity.WireType.INTEGER, "INTEGER:0"));
        if (businessDate == null
                || per < 1
                || per > 100
                || maxPages < 2
                || maxPages > 4
                || maxPhysicalRows < per
                || maxPhysicalRows > 1000
                || maxResponseBytes < 1
                || maxResponseBytes > 10 * 1024 * 1024
                || deadline == null
                || deadline.isNegative()
                || deadline.isZero()
                || deadline.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("COL_PILOT_INVALID_BOUNDS");
        }
    }

    public DataExportPageRequest pageRequest() {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(businessDate, businessDate),
                Optional.empty(),
                1,
                per,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    public static Coletas6908PilotPlan read(final Path path) {
        try {
            if (Files.size(path) > 8192 || Files.size(path) == 0) {
                throw new IllegalArgumentException("COL_PILOT_INVALID_PLAN");
            }
            final ObjectMapper mapper = new ObjectMapper();
            mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            final JsonNode root = mapper.readTree(Files.readAllBytes(path));
            if (root == null
                    || !root.isObject()
                    || !root.properties().stream()
                            .map(java.util.Map.Entry::getKey)
                            .collect(java.util.stream.Collectors.toSet())
                            .equals(FIELDS)
                    || !"coletas-6908-traversal-v1".equals(text(root, "version"))
                    || number(root, "templateId") != 6908
                    || !"LOCAL_SHADOW".equals(text(root, "target"))
                    || !"BOUNDED_TRAVERSAL_PARITY".equals(text(root, "mode"))
                    || number(root, "page") != 1) {
                throw new IllegalArgumentException("COL_PILOT_INVALID_PLAN");
            }
            return new Coletas6908PilotPlan(
                    text(root, "sourceInstance"),
                    text(root, "tenantScope"),
                    LocalDate.parse(text(root, "businessDate")),
                    number(root, "per"),
                    number(root, "maxPages"),
                    number(root, "maxPhysicalRows"),
                    number(root, "maxResponseBytes"),
                    Duration.ofSeconds(number(root, "deadlineSeconds")));
        } catch (final IOException | RuntimeException failure) {
            throw new IllegalArgumentException("COL_PILOT_INVALID_PLAN");
        }
    }

    private static String text(final JsonNode root, final String field) {
        final JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("COL_PILOT_INVALID_PLAN");
        }
        return value.textValue();
    }

    private static int number(final JsonNode root, final String field) {
        final JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("COL_PILOT_INVALID_PLAN");
        }
        return value.intValue();
    }
}
