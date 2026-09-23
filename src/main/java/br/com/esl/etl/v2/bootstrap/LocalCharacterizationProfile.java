package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioGraphQlNodeMapper;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Original profile metadata plus the current typed mapper; no synthetic oracle is imported. */
public enum LocalCharacterizationProfile {
    COL("coletas-6908", "/id"),
    MAN("manifestos-6399", "/sequence_code"),
    COT("cotacoes-6906", "/sequence_code"),
    USER("usuarios-individual", "/id"),
    FRE("fretes-6389", "/id"),
    LOC("localizacao-8656", "/corporation_sequence_number");

    private final String resource;
    private final String identityPath;

    LocalCharacterizationProfile(final String resource, final String identityPath) {
        this.resource = "/local-characterization/profiles/" + resource + ".profile.json";
        this.identityPath = identityPath;
    }

    private byte[] bytes() throws IOException {
        try (var input = LocalCharacterizationProfile.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("LOCAL_PROFILE_RESOURCE");
            }
            final var bytes = input.readNBytes(65537);
            QualificationJson.parse(bytes, 65536);
            return bytes;
        }
    }

    public String sha256() throws IOException {
        return QualificationJson.sha256(bytes());
    }

    public String revision() throws IOException {
        final var profile = QualificationJson.parse(bytes(), 65536);
        return (profile.has("contract")
                        ? profile.path("contract")
                        : profile.path("channels").get(0).path("contract"))
                .path("contractVersion")
                .asText();
    }

    public String identityPath() {
        return identityPath;
    }

    public Observation inspect(final String raw) throws IOException {
        return inspect(raw, new int[2]);
    }

    Observation inspect(final String raw, final int[] accumulatedShape) throws IOException {
        final var row = QualificationJson.parse(raw.getBytes(StandardCharsets.UTF_8), 32768);
        bounds(row, 0, accumulatedShape);
        if (!row.isObject()) {
            return new Observation(false, false);
        }
        final var profile = QualificationJson.parse(bytes(), 65536);
        if (!fieldContracts(profile, row)) {
            return new Observation(false, false);
        }
        return switch (this) {
            case COL -> {
                final var value = new ColetaDataExportRecordMapper().map(1, row);
                yield observation(value.quarantineReasonCode(), value.freshnessAtUtc());
            }
            case MAN -> {
                final var value = new ManifestoDataExportRecordMapper().map(1, row);
                yield observation(value.quarantineReasonCode(), value.freshnessAtUtc());
            }
            case COT -> {
                final var value = new CotacaoDataExportRecordMapper().map(1, row);
                yield observation(value.quarantineReasonCode(), value.freshnessAtUtc());
            }
            case FRE -> {
                final var value = new FreteDataExportRecordMapper().map(1, row);
                yield observation(value.quarantineReasonCode(), value.freshnessAtUtc());
            }
            case LOC -> {
                // Preserve numeric wire lexemes for the strict LOC-04 mapper.
                final var value = new LocalizacaoCargaDataExportRecordMapper().map(1, raw);
                yield new Observation(
                        !value.quarantined(), !value.quarantined() && value.serviceAtUtc() != null);
            }
            case USER ->
                    new Observation(
                            new UsuarioGraphQlNodeMapper().map(1, row).quarantineReasonCode()
                                    == null,
                            true);
        };
    }

    private static Observation observation(final String quarantine, final Instant freshness) {
        return new Observation(quarantine == null, quarantine == null && freshness != null);
    }

    private boolean fieldContracts(final JsonNode profile, final JsonNode row) {
        // Q-FND-02 unknown types stay with the ratified typed mappers, never guessed here.
        for (final var field : profile.path("fieldContracts")) {
            String path = field.path("path").asText();
            if (this == USER) {
                if (!path.startsWith("/node/")) {
                    continue; // This adapter takes nodes, not a claimed GraphQL transport receipt.
                }
                path = path.substring(5);
            }
            final var value = row.at(path);
            final String presence =
                    value.isMissingNode() ? "ABSENT" : value.isNull() ? "NULL" : "VALUE";
            if (!contains(field.path("presenceStates"), presence)
                    || presence.equals("VALUE")
                            && !contains(field.path("wireTypes"), type(value))) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(final JsonNode array, final String value) {
        for (final var member : array) {
            if (member.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String type(final JsonNode value) {
        if (value.isTextual()) {
            return "STRING";
        }
        if (value.isIntegralNumber()) {
            return "INTEGER";
        }
        if (value.isFloatingPointNumber()) {
            return "DECIMAL";
        }
        if (value.isBoolean()) {
            return "BOOLEAN";
        }
        if (value.isObject()) {
            return "OBJECT";
        }
        return "ARRAY";
    }

    private static void bounds(final JsonNode value, final int depth, final int[] counts) {
        final int currentDepth = depth + (value.isContainerNode() ? 1 : 0);
        counts[0]++;
        if (currentDepth > 16
                || counts[0] > 4096
                || value.isObject() && (counts[1] += value.size()) > 256) {
            throw new IllegalArgumentException("LOCAL_PROFILE_SHAPE_BOUND");
        }
        for (final var child : value) {
            bounds(child, currentDepth, counts);
        }
    }

    public record Observation(boolean valid, boolean freshnessAvailable) {}
}
