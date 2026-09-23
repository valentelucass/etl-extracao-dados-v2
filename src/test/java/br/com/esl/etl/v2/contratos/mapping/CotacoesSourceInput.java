package br.com.esl.etl.v2.contratos.mapping;

import br.com.esl.etl.v2.contratos.caracterizacao.StrictUtf8JsonLoader;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportQueryEncoder;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/** Private input for one future sample; never infers scope or civil date boundaries. */
final class CotacoesSourceInput {
    static final int RESPONSE_BYTES = 65_536;
    static final String DECISION = "docs/catalogos/cotacoes-v2-027/decisao-v01.json";
    private static final Set<String> KEYS =
            Set.of(
                    "version",
                    "template",
                    "method",
                    "baseUri",
                    "logicalHost",
                    "sourceInstance",
                    "tenantScope",
                    "windowStart",
                    "windowEndExclusive",
                    "civilDateFrom",
                    "civilDateThrough",
                    "timezone",
                    "authorizationReference",
                    "temporalGuaranteeReference",
                    "representativenessReference",
                    "credentialAttestationReference",
                    "notBefore",
                    "notAfter",
                    "budgetAdopted",
                    "sourceExecutionEnabled",
                    "oracleFile",
                    "oracleSha256",
                    "executionId");
    private final JsonNode input;
    private final JsonNode oracle;
    private final URI base;

    private CotacoesSourceInput(
            final JsonNode input,
            final JsonNode oracle,
            final JsonNode decision,
            final Instant now) {
        this.input = input;
        this.oracle = oracle;
        final Set<String> keys = new HashSet<>();
        input.fieldNames().forEachRemaining(keys::add);
        require(input.isObject() && keys.equals(KEYS), "INPUT_SCHEMA");
        require(
                input.path("version").isInt() && input.path("version").intValue() == 1,
                "INPUT_VERSION");
        require(
                input.path("template").isInt()
                        && input.path("template").intValue() == 6906
                        && "GET_WITH_QUERY".equals(text("method")),
                "TEMPLATE_OR_METHOD");
        for (final String name :
                Set.of(
                        "logicalHost",
                        "sourceInstance",
                        "tenantScope",
                        "authorizationReference",
                        "temporalGuaranteeReference",
                        "representativenessReference",
                        "credentialAttestationReference",
                        "executionId")) {
            require(text(name).matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,79}"), "MISSING_BINDING");
            require(
                    !Set.of("DEFAULT", "GLOBAL", "SINGLETON", "MISSING", "PENDING", "TODO", "NULL")
                            .contains(text(name).toUpperCase(java.util.Locale.ROOT)),
                    "SENTINEL_BINDING");
        }
        require(
                input.path("budgetAdopted").isBoolean()
                        && input.path("budgetAdopted").booleanValue()
                        && input.path("sourceExecutionEnabled").isBoolean()
                        && input.path("sourceExecutionEnabled").booleanValue(),
                "ADOPTION_MISSING");
        base = URI.create(text("baseUri"));
        require(
                "https".equals(base.getScheme())
                        && base.getHost() != null
                        && base.getRawUserInfo() == null
                        && base.getRawQuery() == null
                        && base.getRawFragment() == null
                        && base.getPort() == -1
                        && (base.getRawPath().isEmpty() || "/".equals(base.getRawPath())),
                "HOST_REJECTED");
        require("America/Sao_Paulo".equals(text("timezone")), "ZONE_REJECTED");
        require(
                Instant.parse(text("windowStart"))
                        .isBefore(Instant.parse(text("windowEndExclusive"))),
                "WINDOW_REJECTED");
        require(
                !LocalDate.parse(text("civilDateThrough"))
                        .isBefore(LocalDate.parse(text("civilDateFrom"))),
                "CIVIL_RANGE_REJECTED");
        require(
                !Instant.parse(text("notAfter"))
                        .isAfter(Instant.parse(text("notBefore")).plusSeconds(7_200)),
                "AUTHORIZATION_DURATION");
        requireValidAt(now);
        require(
                oracle.isObject()
                        && oracle.size() == 3
                        && oracle.has("decisionSha256")
                        && oracle.has("metadata")
                        && oracle.has("pages"),
                "ORACLE_SCHEMA");
        require(
                oracle.path("metadata").isObject()
                        && oracle.path("metadata").size() == 2
                        && oracle.path("metadata").path("fields").isArray()
                        && !oracle.path("metadata").path("fields").isEmpty()
                        && oracle.path("metadata").path("filters").isArray()
                        && !oracle.path("metadata").path("filters").isEmpty(),
                "METADATA_EXPECTATIONS");
        require(oracle.path("pages").isArray() && pages() >= 1 && pages() <= 3, "PAGE_PLAN_LIMIT");
        int rows = 0;
        for (final JsonNode page : oracle.path("pages")) {
            require(page.isArray() && page.size() <= 3, "ROW_PLAN_LIMIT");
            rows += page.size();
            for (final JsonNode row : page) {
                require(
                        row.isObject() && row.size() <= 256 && row.path("/quarantine").isTextual(),
                        "ROW_EXPECTATIONS");
                final var fields = row.fields();
                while (fields.hasNext()) {
                    final var field = fields.next();
                    require(
                            field.getKey().matches("/[A-Za-z0-9_/]+")
                                    && field.getValue().isTextual(),
                            "ROW_EXPECTATIONS");
                }
                require(
                        decision.path("presenceSemantics").path("requiredFields").size() == 9,
                        "DECISION_FIELD_CONTRACT");
                for (final JsonNode name :
                        decision.path("presenceSemantics").path("requiredFields")) {
                    require(
                            row.path("/" + name.textValue() + "/presence").isTextual()
                                    && row.path("/" + name.textValue() + "/wireType").isTextual(),
                            "FIELD_COVERAGE_MISSING");
                }
                if ("NONE".equals(row.path("/quarantine").textValue())) {
                    require(
                            row.path("/sequence_code/typed").isTextual()
                                    && row.path("/freshness/typed").isTextual()
                                    && row.path("/freshness/origin").isTextual(),
                            "TYPED_EXPECTATIONS_MISSING");
                }
            }
        }
        require(rows >= 1 && rows <= 9, "EMPTY_OR_EXCESSIVE_SAMPLE");
    }

    static CotacoesSourceInput read(final Path file, final Instant now) throws IOException {
        try {
            final JsonNode input = json(readFile(file));
            final byte[] oracle = readFile(Path.of(input.path("oracleFile").asText("")));
            return validate(input, oracle, readFile(Path.of(DECISION)), now);
        } catch (final RuntimeException error) {
            throw new IOException("INPUT_REJECTED");
        }
    }

    static CotacoesSourceInput validate(
            final JsonNode input, final byte[] oracle, final byte[] decision, final Instant now) {
        require(sha256(oracle).equals(input.path("oracleSha256").textValue()), "ORACLE_BINDING");
        final JsonNode parsed = json(oracle);
        require(
                sha256(decision).equals(parsed.path("decisionSha256").textValue()),
                "DECISION_BINDING");
        return new CotacoesSourceInput(input.deepCopy(), parsed, json(decision), now);
    }

    void requireValidAt(final Instant now) {
        require(
                !now.isBefore(Instant.parse(text("notBefore")))
                        && now.isBefore(Instant.parse(text("notAfter"))),
                "AUTHORIZATION_EXPIRED_OR_NOT_STARTED");
    }

    URI metadataUri() {
        return base.resolve("/api/analytics/reports/6906/info");
    }

    URI dataUri(final int page) {
        require(page >= 1 && page <= pages(), "PAGE_GAP_OR_LIMIT");
        final var template = DataExportTemplate.COTACOES;
        final var request =
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(
                                LocalDate.parse(text("civilDateFrom")),
                                LocalDate.parse(text("civilDateThrough"))),
                        Optional.empty(),
                        page,
                        3,
                        template.defaultOrderBy());
        return new DataExportQueryEncoder()
                .appendQuery(
                        base.resolve("/api/analytics/reports/6906/data"),
                        request,
                        ZoneId.of("America/Sao_Paulo"));
    }

    int pages() {
        return oracle.path("pages").size();
    }

    JsonNode expected(final int page) {
        return oracle.path("pages").get(page - 1);
    }

    String executionId() {
        return text("executionId");
    }

    String oracleHash() {
        return text("oracleSha256");
    }

    boolean metadataMatches(final JsonNode observed) {
        return observed.isObject()
                && !observed.has("error")
                && !observed.has("errors")
                && oracle.path("metadata").path("fields").equals(observed.path("fields"))
                && oracle.path("metadata").path("filters").equals(observed.path("filters"));
    }

    private String text(final String name) {
        require(
                input.path(name).isTextual()
                        && !input.path(name).textValue().isBlank()
                        && input.path(name).textValue().length() <= 1_024,
                "INPUT_MISSING");
        return input.path(name).textValue();
    }

    static JsonNode json(final byte[] bytes) {
        return new StrictUtf8JsonLoader()
                .load(
                        bytes,
                        RESPONSE_BYTES,
                        new StrictUtf8JsonLoader.JsonStructureLimits(16, 256, 4_096));
    }

    static byte[] readBounded(final InputStream stream) throws IOException {
        final byte[] bytes = stream.readNBytes(RESPONSE_BYTES + 1);
        if (bytes.length > RESPONSE_BYTES) {
            throw new IOException("BYTE_LIMIT");
        }
        return bytes;
    }

    static byte[] readFile(final Path path) throws IOException {
        for (Path current = path.toAbsolutePath().normalize();
                current != null;
                current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                final var attributes =
                        Files.readAttributes(
                                current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                require(!attributes.isSymbolicLink() && !attributes.isOther(), "REPARSE_REJECTED");
            }
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return readBounded(stream);
        }
    }

    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA256_UNAVAILABLE");
        }
    }

    static void require(final boolean condition, final String reason) {
        if (!condition) {
            throw new IllegalArgumentException(reason);
        }
    }

    @Override
    public String toString() {
        return "COTACOES_PRIVATE_INPUT_REDACTED";
    }
}
