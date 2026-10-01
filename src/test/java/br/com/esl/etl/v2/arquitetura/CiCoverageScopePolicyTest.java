package br.com.esl.etl.v2.arquitetura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class CiCoverageScopePolicyTest {
    @TempDir Path temporary;
    private static final Path SCOPE = Path.of("docs/catalogos/ci-coverage-scope");
    private static final Set<String> SQL_PACKAGES =
            Set.of(
                    "br.com.esl.etl.v2.plataforma.persistencia.analitico",
                    "br.com.esl.etl.v2.plataforma.persistencia.expansao",
                    "br.com.esl.etl.v2.plataforma.persistencia.relacional");
    private static final Set<String> PURE_JDBC_PACKAGE_CLASSES =
            Set.of("AnalyticFieldComparison.java", "RasterJdbcValue.java");
    private static final Set<String> SHADOW_BOOTSTRAP_SOURCES =
            Set.of(
                    "QualificationTemporalMatrix.java",
                    "QualificationMonitoring.java",
                    "QualificationWorker.java",
                    "ExpansionLaboratoryExecutor.java",
                    "ExpansionLaboratoryHydrator.java",
                    "RelationalLaboratoryExecutor.java",
                    "AnalyticScenarioRuntime.java",
                    "AnalyticExpansionCapture.java",
                    "AnalyticScenarioEnrichment.java",
                    "QualificationWindowExecutor.java",
                    "QualificationConcurrency.java",
                    "SequenceRecomposition.java",
                    "LocalAnalyticCollectionSweep.java",
                    "LocalAnalyticManifestRuntime.java",
                    "LocalAnalyticCollectionRuntime.java",
                    "LocalExpansionRuntime.java",
                    "LocalExpansionDependencyRuntime.java",
                    "LocalAnalyticQuotesRuntime.java",
                    "LocalRasterRuntime.java",
                    "LocalColetasTemporalRuntime.java",
                    "QualificationSqlEvidence.java");
    private static final Map<String, String> SHADOW_NESTED_CLASSES =
            Map.ofEntries(
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalArtifactSequence$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalArtifactSequence.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.DeclaredAnalyticSupport$SqlApply",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/DeclaredAnalyticSupport.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.DeclaredAnalyticSupport$SqlApply$SourceIdentity",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/DeclaredAnalyticSupport.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.QualificationScenarioVerifier$SqlVerification",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationScenarioVerifier.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.QualificationScenarioVerifier$SqlVerification$Evidence",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationScenarioVerifier.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.QualificationCaseExecutor$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationCaseExecutor.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.QualificationCaseExecutor$SqlExecution$AbsenceEvidence",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationCaseExecutor.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.QualificationPhysicalMetadata$SqlVerification",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationPhysicalMetadata.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.QualificationSupervisor$SqlChildExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationSupervisor.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalAnalyticUsersRuntime$SqlCapture",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalAnalyticUsersRuntime.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalRelationalRuntime$SqlCapture",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalRelationalRuntime.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalArtifactScenario$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalArtifactScenario.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryMain$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/AnalyticLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.SequenceAgenda$SqlPersistence",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/SequenceAgenda.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryMain$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/ExpansionLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.RelationalLaboratoryMain$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/RelationalLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalCollectionSweepProgram$SqlExecution",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalCollectionSweepProgram.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.AnalyticScenarioFaults$SqlFault",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/AnalyticScenarioFaults.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.DeclaredSqlOracles$SqlPartitionReceipts",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/DeclaredSqlOracles.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.DeclaredAnalyticReferences$SqlImports",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/DeclaredAnalyticReferences.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalFactOracle$SqlComparison",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalFactOracle.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.RuntimeTemporalOperation$SqlPersistence",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeTemporalOperation.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalDataLaboratoryMain$SqlCapture",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalDataLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalRasterArtifactMain$SqlCapture",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalRasterArtifactMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryMain$SqlSession",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/AnalyticLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryMain$SqlSession",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/ExpansionLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.RelationalLaboratoryMain$SqlSession",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/RelationalLaboratoryMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalCollectionSweepMain$SqlSession",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalCollectionSweepMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalArtifactScenarioMain$SqlSession",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalArtifactScenarioMain.java"),
                    Map.entry(
                            "br.com.esl.etl.v2.bootstrap.LocalArtifactSequenceMain$SqlSession",
                            "src/main/java/br/com/esl/etl/v2/bootstrap/LocalArtifactSequenceMain.java"));

    @Test
    void everySourcePackageAndPhysicalClassHasAnExactReviewedScope() throws Exception {
        verifyScope(SCOPE, sources());
    }

    @Test
    void newlyAddedOrAlteredPhysicalClassAndManifestTamperingFailClosed() throws Exception {
        final var actual = sources();
        final var added = new HashMap<>(actual);
        added.put(
                "src/main/java/br/com/esl/etl/v2/plataforma/persistencia/analitico/JdbcUnclassified.java",
                "package br.com.esl.etl.v2.plataforma.persistencia.analitico;\nimport java.sql.Connection;\nclass JdbcUnclassified {}\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, added));

        final var changed = new HashMap<>(actual);
        final String tracked =
                "src/main/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/JdbcColetaTemporalLaboratory.java";
        changed.put(tracked, changed.get(tracked) + "\n// changed physical route\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, changed));

        final var newMixed = new HashMap<>(actual);
        newMixed.put(
                "src/main/java/br/com/esl/etl/v2/bootstrap/NewGatePath.java",
                "package br.com.esl.etl.v2.bootstrap;\nclass NewGatePath {}\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, newMixed));

        final var changedMixed = new HashMap<>(actual);
        final String pure =
                "src/main/java/br/com/esl/etl/v2/bootstrap/QualificationSupervisor.java";
        changedMixed.put(pure, changedMixed.get(pure) + "\n// changed unit route\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, changedMixed));

        final var changedUsers = new HashMap<>(actual);
        final String users =
                "src/main/java/br/com/esl/etl/v2/bootstrap/LocalAnalyticUsersRuntime.java";
        changedUsers.put(users, changedUsers.get(users) + "\n// changed JDBC route\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, changedUsers));

        final var changedRelational = new HashMap<>(actual);
        final String relational =
                "src/main/java/br/com/esl/etl/v2/bootstrap/LocalRelationalRuntime.java";
        changedRelational.put(
                relational, changedRelational.get(relational) + "\n// changed relational route\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, changedRelational));

        final var changedScenario = new HashMap<>(actual);
        final String scenario =
                "src/main/java/br/com/esl/etl/v2/bootstrap/LocalArtifactScenario.java";
        changedScenario.put(scenario, changedScenario.get(scenario) + "\n// changed SQL route\n");
        assertThrows(AssertionError.class, () -> verifyScope(SCOPE, changedScenario));

        Files.copy(SCOPE.resolve("packages.txt"), temporary.resolve("packages.txt"));
        Files.copy(
                SCOPE.resolve("mixed-class-scope.txt"), temporary.resolve("mixed-class-scope.txt"));
        Files.copy(
                SCOPE.resolve("physical-nested-classes.txt"),
                temporary.resolve("physical-nested-classes.txt"));
        final var rows = Files.readAllLines(SCOPE.resolve("physical-sources.txt"));
        rows.set(0, rows.get(0).replaceFirst("[0-9a-f]{64}", "0".repeat(64)));
        Files.write(temporary.resolve("physical-sources.txt"), rows, StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> verifyScope(temporary, actual));

        final Path routeMutant = Files.createDirectory(temporary.resolve("route-mutant"));
        Files.copy(SCOPE.resolve("packages.txt"), routeMutant.resolve("packages.txt"));
        Files.copy(
                SCOPE.resolve("physical-sources.txt"), routeMutant.resolve("physical-sources.txt"));
        Files.copy(
                SCOPE.resolve("physical-nested-classes.txt"),
                routeMutant.resolve("physical-nested-classes.txt"));
        final var routes = Files.readAllLines(SCOPE.resolve("mixed-class-scope.txt"));
        final int lineage =
                java.util.stream.IntStream.range(0, routes.size())
                        .filter(i -> routes.get(i).contains("QualificationLineageEvidence.java"))
                        .findFirst()
                        .orElseThrow();
        routes.set(lineage, routes.get(lineage).replace("SHADOW_EXCLUDED", "UNIT_INCLUDED"));
        Files.write(routeMutant.resolve("mixed-class-scope.txt"), routes, StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> verifyScope(routeMutant, actual));

        final Path nestedMutant = Files.createDirectory(temporary.resolve("nested-mutant"));
        Files.copy(SCOPE.resolve("packages.txt"), nestedMutant.resolve("packages.txt"));
        Files.copy(
                SCOPE.resolve("physical-sources.txt"),
                nestedMutant.resolve("physical-sources.txt"));
        Files.copy(
                SCOPE.resolve("mixed-class-scope.txt"),
                nestedMutant.resolve("mixed-class-scope.txt"));
        final var nestedRows = Files.readAllLines(SCOPE.resolve("physical-nested-classes.txt"));
        nestedRows.set(0, nestedRows.get(0).replace("SQL_SESSION_EXECUTION", "UNIT_INCLUDED"));
        Files.write(
                nestedMutant.resolve("physical-nested-classes.txt"),
                nestedRows,
                StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> verifyScope(nestedMutant, actual));
    }

    private static Map<String, String> sources() throws Exception {
        final Map<String, String> result = new TreeMap<>();
        try (var walk = Files.walk(Path.of("src/main/java"))) {
            for (final Path path :
                    walk.filter(file -> file.toString().endsWith(".java")).toList()) {
                result.put(
                        path.toString().replace('\\', '/'),
                        Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static void verifyScope(final Path scope, final Map<String, String> sources)
            throws Exception {
        final var packages =
                new TreeSet<>(
                        Files.readAllLines(scope.resolve("packages.txt"), StandardCharsets.UTF_8));
        final Map<String, String> physical = new TreeMap<>();
        for (final String line :
                Files.readAllLines(scope.resolve("physical-sources.txt"), StandardCharsets.UTF_8)) {
            final var fields = line.split("\t", -1);
            assertEquals(3, fields.length);
            assertEquals("DIRECT_SQL_JDBC", fields[2]);
            assertEquals(null, physical.put(fields[0], fields[1]), "duplicate physical source");
        }
        final Map<String, String[]> mixed = new TreeMap<>();
        for (final String line :
                Files.readAllLines(
                        scope.resolve("mixed-class-scope.txt"), StandardCharsets.UTF_8)) {
            final var fields = line.split("\t", -1);
            assertEquals(3, fields.length);
            assertTrue(Set.of("UNIT_INCLUDED", "SHADOW_EXCLUDED").contains(fields[2]));
            assertEquals(null, mixed.put(fields[0], fields), "duplicate mixed source");
        }
        final var observedPackages = new TreeSet<String>();
        final var observedPhysical = new TreeSet<String>();
        final var observedMixed = new TreeSet<String>();
        for (final var entry : sources.entrySet()) {
            final String source = entry.getValue();
            final Path path = Path.of(entry.getKey());
            final String declared = source.lines().findFirst().orElse("");
            assertTrue(declared.startsWith("package ") && declared.endsWith(";"), path.toString());
            final String packageName = declared.substring(8, declared.length() - 1);
            observedPackages.add(packageName);
            if (packageName.equals("br.com.esl.etl.v2.bootstrap")
                    || packageName.equals("br.com.esl.etl.v2.plataforma.qualificacao")) {
                final String relative = entry.getKey();
                observedMixed.add(relative);
                assertTrue(mixed.containsKey(relative), "unclassified mixed source: " + relative);
                assertEquals(mixed.get(relative)[1], sha256(source), relative);
                assertEquals(
                        relative.endsWith("/QualificationLineageEvidence.java")
                                        || (packageName.equals("br.com.esl.etl.v2.bootstrap")
                                                && SHADOW_BOOTSTRAP_SOURCES.contains(
                                                        path.getFileName().toString()))
                                ? "SHADOW_EXCLUDED"
                                : "UNIT_INCLUDED",
                        mixed.get(relative)[2],
                        relative);
            }
            final boolean physicalPackageClass =
                    SQL_PACKAGES.contains(packageName)
                            && !PURE_JDBC_PACKAGE_CLASSES.contains(path.getFileName().toString());
            final boolean physicalColetasClass =
                    packageName.equals("br.com.esl.etl.v2.plataforma.persistencia.coletas")
                            && path.getFileName()
                                    .toString()
                                    .equals("JdbcColetaTemporalLaboratory.java");
            final boolean physicalQualificationClass =
                    packageName.equals("br.com.esl.etl.v2.plataforma.qualificacao")
                            && path.getFileName()
                                    .toString()
                                    .equals("QualificationLineageEvidence.java");
            final boolean physicalBootstrapClass =
                    packageName.equals("br.com.esl.etl.v2.bootstrap")
                            && SHADOW_BOOTSTRAP_SOURCES.contains(path.getFileName().toString());
            if (physicalPackageClass
                    || physicalColetasClass
                    || physicalQualificationClass
                    || physicalBootstrapClass) {
                final String relative = entry.getKey();
                observedPhysical.add(relative);
                assertTrue(
                        source.contains("java.sql")
                                || source.contains("new Jdbc")
                                || source.contains("ColetaTemporalLaboratorySession.open(")
                                || source.contains(".getConnection(")
                                || source.contains(".prepareStatement(")
                                || source.contains(".executeQuery(")
                                || source.contains(".executeUpdate("),
                        "physical class lost its SQL route: " + relative);
                assertEquals(physical.get(relative), sha256(source), relative);
            }
        }
        assertEquals(packages, observedPackages, "new package needs scope classification");
        assertEquals(mixed.keySet(), observedMixed, "new or removed mixed source needs review");
        assertEquals(
                physical.keySet(),
                observedPhysical,
                "new physical class needs exact classification");
        final var nested =
                Files.readAllLines(
                        scope.resolve("physical-nested-classes.txt"), StandardCharsets.UTF_8);
        assertEquals(SHADOW_NESTED_CLASSES.size(), nested.size(), "review nested physical classes");
        final var observedNested = new TreeSet<String>();
        for (final String row : nested) {
            final var fields = row.split("\t", -1);
            assertEquals(4, fields.length);
            assertTrue(observedNested.add(fields[0]), "duplicate nested physical class");
            assertEquals(SHADOW_NESTED_CLASSES.get(fields[0]), fields[1]);
            assertEquals(sha256(sources.get(fields[1])), fields[2]);
            assertEquals(
                    fields[0].endsWith("$SourceIdentity")
                            ? "SQL_SESSION_IDENTITY"
                            : fields[0].endsWith("$Evidence")
                                            || fields[0].endsWith("$AbsenceEvidence")
                                    ? "SQL_SESSION_EVIDENCE"
                                    : "SQL_SESSION_EXECUTION",
                    fields[3]);
            assertTrue(
                    sources.get(fields[1])
                                    .contains(
                                            "class "
                                                    + fields[0].substring(
                                                            fields[0].lastIndexOf('$') + 1))
                            || sources.get(fields[1])
                                    .contains(
                                            "record "
                                                    + fields[0].substring(
                                                            fields[0].lastIndexOf('$') + 1)));
            assertTrue(
                    sources.get(fields[1]).contains("new Jdbc")
                            || sources.get(fields[1]).contains(".JdbcAnalyticScenario(session)")
                            || (fields[0].equals(
                                            "br.com.esl.etl.v2.bootstrap.QualificationPhysicalMetadata$SqlVerification")
                                    && sources.get(fields[1]).contains("session.getConnection()"))
                            || (fields[0].equals(
                                            "br.com.esl.etl.v2.bootstrap.QualificationSupervisor$SqlChildExecution")
                                    && sources.get(fields[1])
                                            .contains(
                                                    "QualificationSqlEvidence.master(configuration)"))
                            || (fields[0].equals(
                                            "br.com.esl.etl.v2.bootstrap.LocalArtifactScenario$SqlExecution")
                                    && sources.get(fields[1]).contains("session.getConnection()"))
                            || (fields[0].equals(
                                            "br.com.esl.etl.v2.bootstrap.DeclaredSqlOracles$SqlPartitionReceipts")
                                    && sources.get(fields[1]).contains("session.getConnection()"))
                            || (fields[0].equals(
                                            "br.com.esl.etl.v2.bootstrap.LocalFactOracle$SqlComparison")
                                    && sources.get(fields[1]).contains("session.getConnection()"))
                            || (Set.of(
                                                    "br.com.esl.etl.v2.bootstrap.LocalCollectionSweepMain$SqlSession",
                                                    "br.com.esl.etl.v2.bootstrap.LocalArtifactScenarioMain$SqlSession",
                                                    "br.com.esl.etl.v2.bootstrap.LocalArtifactSequenceMain$SqlSession")
                                            .contains(fields[0])
                                    && sources.get(fields[1])
                                            .contains(
                                                    "ColetaTemporalLaboratorySession.openFromEnvironment()")));
            assertTrue(
                    Files.isRegularFile(
                            Path.of("target/classes/" + fields[0].replace('.', '/') + ".class")));
        }
        assertEquals(SHADOW_NESTED_CLASSES.keySet(), observedNested);
    }

    @Test
    void pomKeepsBothCoverageGatesAndShadowOptIn() throws Exception {
        verifyPom(Path.of("pom.xml"));
        final String original = Files.readString(Path.of("pom.xml"));
        final int shadow = original.indexOf("<id>check-shadow-package-coverage</id>");
        assertTrue(shadow > 0);
        final int threshold = original.indexOf("<minimum>0.80</minimum>", shadow);
        assertTrue(threshold > shadow);
        final String mutated =
                original.substring(0, threshold)
                        + "<minimum>0.00</minimum>"
                        + original.substring(threshold + "<minimum>0.80</minimum>".length());
        final Path pom = temporary.resolve("pom-mutated.xml");
        Files.writeString(pom, mutated);
        assertThrows(AssertionError.class, () -> verifyPom(pom));

        final String exclusion =
                "<exclude>br/com/esl/etl/v2/plataforma/qualificacao/QualificationLineageEvidence.class</exclude>";
        final Path widened = temporary.resolve("pom-widened.xml");
        Files.writeString(
                widened,
                original.replaceFirst(
                        java.util.regex.Pattern.quote(exclusion),
                        exclusion
                                + "<exclude>br/com/esl/etl/v2/bootstrap/QualificationSupervisor.class</exclude>"));
        assertThrows(AssertionError.class, () -> verifyPom(widened));

        for (final String physical :
                Set.of(
                        "QualificationSupervisor$SqlChildExecution",
                        "QualificationSqlEvidence",
                        "LocalAnalyticUsersRuntime$SqlCapture",
                        "LocalRelationalRuntime$SqlCapture",
                        "LocalArtifactScenario$SqlExecution",
                        "AnalyticLaboratoryMain$SqlExecution",
                        "SequenceAgenda$SqlPersistence",
                        "ExpansionLaboratoryMain$SqlExecution",
                        "RelationalLaboratoryMain$SqlExecution",
                        "LocalCollectionSweepProgram$SqlExecution",
                        "AnalyticScenarioFaults$SqlFault",
                        "DeclaredSqlOracles$SqlPartitionReceipts",
                        "DeclaredAnalyticReferences$SqlImports",
                        "LocalFactOracle$SqlComparison",
                        "RuntimeTemporalOperation$SqlPersistence",
                        "LocalDataLaboratoryMain$SqlCapture",
                        "LocalRasterArtifactMain$SqlCapture",
                        "AnalyticLaboratoryMain$SqlSession",
                        "ExpansionLaboratoryMain$SqlSession",
                        "RelationalLaboratoryMain$SqlSession",
                        "LocalCollectionSweepMain$SqlSession",
                        "LocalArtifactScenarioMain$SqlSession",
                        "LocalArtifactSequenceMain$SqlSession")) {
            final String include =
                    "<include>br.com.esl.etl.v2.bootstrap." + physical + "</include>";
            assertTrue(original.contains(include));
            final Path missing = temporary.resolve("pom-shadow-missing-" + physical + ".xml");
            Files.writeString(missing, original.replace(include, ""));
            assertThrows(AssertionError.class, () -> verifyPom(missing));
        }
    }

    private static void verifyPom(final Path pom) throws Exception {
        final var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final var document = factory.newDocumentBuilder().parse(pom.toFile());
        final var unit = execution(document.getDocumentElement(), "check-package-coverage");
        final var shadow =
                execution(document.getDocumentElement(), "check-shadow-package-coverage");
        final var unitPackage = rule(unit, "PACKAGE", SQL_PACKAGES, "excludes");
        final var shadowPackage = rule(shadow, "PACKAGE", SQL_PACKAGES, "includes");
        assertLimits(unitPackage, "0.80", "0.60");
        assertLimits(shadowPackage, "0.80", "0.60");
        final var expectedClasses = shadowClassNames();
        final var expectedPaths = new TreeSet<String>();
        expectedClasses.forEach(name -> expectedPaths.add(name.replace('.', '/') + ".class"));
        assertEquals(
                expectedPaths, selected((Element) unit.getElementsByTagName("excludes").item(0)));
        final var shadowColetas = rule(shadow, "CLASS", expectedClasses, "includes");
        assertLimits(shadowColetas, "0.80", "0.60");
        final var pure =
                rule(
                        unit,
                        "CLASS",
                        Set.of(
                                "br.com.esl.etl.v2.plataforma.persistencia.analitico.AnalyticFieldComparison",
                                "br.com.esl.etl.v2.plataforma.persistencia.analitico.RasterJdbcValue"),
                        "includes");
        assertLimits(pure, "0.80", "0.60");
        assertEquals(
                "false", text(document.getDocumentElement(), "shadow.local.integration.enabled"));
        execution(document.getDocumentElement(), "shadow-local-integration-tests");
        final var offline =
                execution(document.getDocumentElement(), "offline-package-integrity-tests");
        final var offlinePlugin = (Element) offline.getParentNode().getParentNode();
        assertEquals(
                Set.of("**/QualificationPackageIntegrityIT.java"),
                selected((Element) offlinePlugin.getElementsByTagName("includes").item(0)));
        assertEquals(
                "true",
                text(document.getDocumentElement(), "shadow.local.integration.profile.active"));
    }

    private static Element execution(final Element root, final String id) {
        final var all = root.getElementsByTagName("execution");
        for (int i = 0; i < all.getLength(); i++) {
            final var item = (Element) all.item(i);
            if (id.equals(text(item, "id"))) {
                return item;
            }
        }
        throw new AssertionError("missing JaCoCo/Failsafe execution: " + id);
    }

    private static Element rule(
            final Element execution,
            final String type,
            final Set<String> names,
            final String selector) {
        final var rules = execution.getElementsByTagName("rule");
        for (int i = 0; i < rules.getLength(); i++) {
            final var candidate = (Element) rules.item(i);
            if (type.equals(text(candidate, "element"))) {
                final var selected = candidate.getElementsByTagName(selector);
                if (selected.getLength() == 0) {
                    continue;
                }
                final var observed = selected((Element) selected.item(0));
                if (observed.equals(names)) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("missing exact " + type + " " + selector + ": " + names);
    }

    private static void assertLimits(
            final Element rule, final String minimumLines, final String minimumBranches) {
        final var limits = rule.getElementsByTagName("limit");
        final Map<String, String> actual = new TreeMap<>();
        for (int i = 0; i < limits.getLength(); i++) {
            final var limit = (Element) limits.item(i);
            assertEquals("COVEREDRATIO", text(limit, "value"));
            actual.put(text(limit, "counter"), text(limit, "minimum"));
        }
        assertEquals(Map.of("LINE", minimumLines, "BRANCH", minimumBranches), actual);
    }

    private static String text(final Element root, final String name) {
        final var nodes = root.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static Set<String> selected(final Element parent) {
        final var observed = new TreeSet<String>();
        final var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.item(i);
            if (node instanceof Element element) {
                observed.add(element.getTextContent().trim());
            }
        }
        return observed;
    }

    private static Set<String> shadowClassNames() {
        final var result = new TreeSet<String>();
        result.add(
                "br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcColetaTemporalLaboratory");
        result.add("br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence");
        SHADOW_BOOTSTRAP_SOURCES.forEach(
                source ->
                        result.add(
                                "br.com.esl.etl.v2.bootstrap."
                                        + source.substring(0, source.length() - ".java".length())));
        result.addAll(SHADOW_NESTED_CLASSES.keySet());
        return result;
    }

    private static String sha256(final String source) throws Exception {
        final byte[] bytes =
                MessageDigest.getInstance("SHA-256")
                        .digest(source.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
