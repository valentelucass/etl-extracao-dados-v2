package br.com.esl.etl.v2.plataforma.analitico;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed metadata only: nineteen contracts, 673 columns and no captured business identities. */
public final class AnalyticSqlCatalog {
    private AnalyticSqlCatalog() {}

    private static final Set<String> TYPES =
            Set.of(
                    "nvarchar",
                    "varchar",
                    "char",
                    "decimal",
                    "bigint",
                    "int",
                    "smallint",
                    "tinyint",
                    "bit",
                    "date",
                    "time",
                    "datetime2",
                    "datetimeoffset",
                    "uniqueidentifier");
    private static final Map<AnalyticSqlContract, List<Column>> CATALOG = loadCatalog();

    public static List<Column> columns(final AnalyticSqlContract contract) {
        return CATALOG.get(Objects.requireNonNull(contract));
    }

    private static Map<AnalyticSqlContract, List<Column>> loadCatalog() {
        try (var input =
                AnalyticSqlCatalog.class.getResourceAsStream(
                        "/analytic-laboratory/query-contracts.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_QUERY_CATALOG_MISSING");
            }
            final byte[] bytes = input.readNBytes(262145);
            if (bytes.length > 262144) {
                throw new IllegalStateException("ANA_QUERY_CATALOG_BOUND");
            }
            final var json =
                    new ObjectMapper()
                            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(bytes);
            if (!"synthetic-analytic-query-contracts-v1".equals(json.path("version").asText())
                    || !json.path("contracts").isArray()
                    || json.path("contracts").size() != 19) {
                throw new IllegalStateException("ANA_QUERY_CATALOG_VERSION");
            }
            final var result =
                    new EnumMap<AnalyticSqlContract, List<Column>>(AnalyticSqlContract.class);
            for (final var item : json.path("contracts")) {
                final var contract =
                        AnalyticSqlContract.valueOf(item.path("id").asText().replace('-', '_'));
                if (!item.path("columns").isArray()
                        || item.path("columns").size() != contract.columns()) {
                    throw new IllegalStateException("ANA_QUERY_CATALOG_COLUMNS");
                }
                final var columns = new ArrayList<Column>(contract.columns());
                for (final var field : item.path("columns")) {
                    final var column =
                            new Column(
                                    field.path("ordinal").asInt(),
                                    field.path("name").asText(),
                                    field.path("type").asText(),
                                    field.path("precision").asInt(),
                                    field.path("scale").asInt(),
                                    field.path("nullable").asBoolean());
                    if (column.ordinal() != columns.size() + 1
                            || !field.path("nullable").isBoolean()) {
                        throw new IllegalStateException("ANA_QUERY_CATALOG_ORDINAL");
                    }
                    columns.add(column);
                }
                if (result.put(contract, List.copyOf(columns)) != null) {
                    throw new IllegalStateException("ANA_QUERY_CATALOG_DUPLICATE");
                }
            }
            if (result.size() != 19) {
                throw new IllegalStateException("ANA_QUERY_CATALOG_INCOMPLETE");
            }
            return Map.copyOf(result);
        } catch (final IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("ANA_QUERY_CATALOG_INVALID", failure);
        }
    }

    public record Column(
            int ordinal, String name, String type, int precision, int scale, boolean nullable) {
        public Column {
            if (ordinal < 1
                    || ordinal > 123
                    || name == null
                    || name.isBlank()
                    || name.length() > 128
                    || !TYPES.contains(type)
                    || precision < 0
                    || scale < 0
                    || scale > 38) {
                throw new IllegalArgumentException("ANA_QUERY_COLUMN_INVALID");
            }
        }
    }
}
