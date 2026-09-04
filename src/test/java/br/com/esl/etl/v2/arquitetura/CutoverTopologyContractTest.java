package br.com.esl.etl.v2.arquitetura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CutoverTopologyContractTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path CATALOG_ROOT = REPOSITORY_ROOT.resolve("docs/catalogos/cutover");

    @Test
    void manifestoFixaBancoNovoUnidadeDatabaseWideEPontoDeNaoRetornoProdutivo()
            throws IOException, NoSuchAlgorithmException {
        final Path manifestPath = CATALOG_ROOT.resolve("manifesto.json");
        final Path fingerprintPath = CATALOG_ROOT.resolve("manifesto.sha256");
        final JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
        final JsonNode topology = manifest.path("topology");

        assertEquals("V2-048a", manifest.path("roadmap_task").textValue());
        assertEquals(
                "LOCAL_DESIGN_COMPLETE_EXTERNAL_REHEARSAL_PENDING",
                manifest.path("decision_state").textValue());
        assertEquals("NEW_DEDICATED_V2_DATABASE", topology.path("strategy").textValue());
        assertEquals("DATABASE_WIDE", topology.path("safe_cutover_granularity").textValue());
        assertFalse(topology.path("granular_cutover_proven").booleanValue());
        assertFalse(topology.path("shadow_database_may_be_renamed").booleanValue());
        assertFalse(topology.path("in_place_legacy_database").booleanValue());
        assertFalse(topology.path("cross_database_wrappers").booleanValue());
        assertFalse(
                topology.path("technical_shadow_publication_is_point_of_no_return").booleanValue());
        assertEquals(
                "FIRST_ACCEPTED_AUTHORITATIVE_V2_PRODUCTION_PUBLICATION",
                topology.path("point_of_no_return").textValue());
        assertEquals(
                "ROLL_FORWARD_ONLY",
                topology.path("recovery_after_point_of_no_return").textValue());

        final String expectedFingerprint =
                Files.readString(fingerprintPath, StandardCharsets.UTF_8)
                        .trim()
                        .split("\\s+", 2)[0];
        final byte[] manifestBytes = Files.readAllBytes(manifestPath);
        final String actualFingerprint =
                HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(manifestBytes));
        assertEquals(expectedFingerprint, actualFingerprint);
    }

    @Test
    void matrizesCobremDagFencesEContratosSemAlegarRotaGranular() throws IOException {
        final List<String> responsibilities =
                Files.readAllLines(
                        CATALOG_ROOT.resolve("responsabilidades.csv"), StandardCharsets.UTF_8);
        final List<String> dag =
                Files.readAllLines(CATALOG_ROOT.resolve("dag.csv"), StandardCharsets.UTF_8);
        final List<String> fences =
                Files.readAllLines(CATALOG_ROOT.resolve("fences.csv"), StandardCharsets.UTF_8);

        assertEquals(132, responsibilities.size());
        assertEquals(50, dag.size());
        assertEquals(12, fences.size());
        assertEquals(11, countCsvValue(dag, "SOURCE"));
        assertEquals(5, countCsvValue(dag, "MART_FACT"));
        assertEquals(19, countCsvValue(dag, "PUB_CONTRACT"));
        assertTrue(
                responsibilities.stream()
                        .anyMatch(
                                line ->
                                        line.contains("\"view_wrapper\"")
                                                && line.contains("\"FORBIDDEN_CROSS_DATABASE\"")));
        assertTrue(
                dag.stream()
                        .filter(line -> line.contains("\"PUB_CONTRACT\""))
                        .allMatch(
                                line ->
                                        line.contains("CONSUMER_MANIFEST_PENDING")
                                                || line.contains("INTERNAL_SCOPE_PENDING_V2_037")));
        assertTrue(
                dag.stream()
                        .anyMatch(
                                line ->
                                        line.contains("\"DIM_USUARIOS\"")
                                                && line.contains(
                                                        "core.v_usuario_dimension_current_v1")
                                                && line.contains("INTERNAL_SAME_DATABASE")
                                                && line.contains(
                                                        "LOCAL_SHADOW_IMPLEMENTED_CONSUMER_CONTRACT_BLOCKED")));
        assertTrue(
                dag.stream()
                        .anyMatch(
                                line ->
                                        line.contains("\"PUB_DIM_USUARIOS\"")
                                                && line.contains(
                                                        "EXTERNAL_CONSUMER_MANIFEST_PENDING")
                                                && line.contains(
                                                        "READ_ONLY_PUB_GRANT_PENDING_V2_037")
                                                && line.contains(
                                                        "PENDING_IMPLEMENTATION_OR_EXTERNAL_GATE")));
        assertTrue(
                fences.stream()
                        .anyMatch(
                                line ->
                                        line.contains("FENCE-06-LEGACY-WRITE")
                                                && line.contains("revogado")
                                                && line.contains(
                                                        "Lock de aplicação isolado não atende")));
        assertTrue(
                fences.stream()
                        .anyMatch(
                                line ->
                                        line.contains("FENCE-10-PNR")
                                                && line.contains("shadow não conta")
                                                && line.contains("POINT_OF_NO_RETURN")));
        assertFalse(String.join("\n", responsibilities).contains("APPLICATION_LOCK_ONLY"));
    }

    @Test
    void documentacaoSeparaShadowDeProducaoEMantemEnsaioExterno() throws IOException {
        final String adr = read("docs/adr/0015-topologia-material-e-fences-de-cutover.md");
        final String runbook = read("docs/runbooks/freeze-cutover-e-recuperacao.md");
        final String catalog = read("docs/catalogos/cutover/README.md");
        final String normalizedRunbook = runbook.replaceAll("\\s+", " ");

        assertTrue(adr.contains("DATABASE_WIDE"));
        assertTrue(adr.contains("Um estado `PUBLISHED` no ambiente de sombra"));
        assertTrue(adr.contains("V2-048b continua dona da ratificação nominal"));
        assertTrue(adr.contains("`SIMULATED_PNR`"));
        assertTrue(
                adr.contains(
                        "A execução produtiva e seu receipt autoritativo pertencem ao gate V2-014"));
        assertTrue(normalizedRunbook.contains("não formam um fence comum"));
        assertTrue(runbook.contains("somente roll-forward"));
        assertTrue(normalizedRunbook.contains("V2-014 permanece aberto até o ensaio V2-048b"));
        assertTrue(runbook.contains("produz somente `SIMULATED_PNR`"));
        assertTrue(catalog.contains("EXTERNAL_CONSUMER_MANIFEST_PENDING"));
        assertFalse((adr + runbook + catalog).contains("jdbc:sqlserver://"));
    }

    @Test
    void preflightPermaneceShadowOnlyEMigrationsNaoCriamFronteiraProdutiva() throws IOException {
        final String preflight =
                read(
                        "src/main/java/br/com/esl/etl/v2/plataforma/configuracao/RuntimeTargetPreflight.java");
        assertTrue(preflight.contains("RuntimeEnvironment.LOCAL_SHADOW"));
        assertFalse(preflight.contains("RuntimeEnvironment.PRODUCTION"));

        final Path migrations = REPOSITORY_ROOT.resolve("database/migrations");
        final Pattern productiveBoundaryCreation =
                Pattern.compile("(?m)^\\s*CREATE\\s+(DATABASE|LOGIN|SYNONYM|JOB)\\b");
        try (Stream<Path> paths = Files.list(migrations)) {
            for (final Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                final String sql =
                        Files.readString(path, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
                assertFalse(productiveBoundaryCreation.matcher(sql).find(), path.toString());
            }
        }
    }

    private static long countCsvValue(final List<String> lines, final String value) {
        final String quotedValue = ",\"" + value + "\",";
        return lines.stream().skip(1).filter(line -> line.contains(quotedValue)).count();
    }

    private static String read(final String relativePath) throws IOException {
        return Files.readString(REPOSITORY_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
