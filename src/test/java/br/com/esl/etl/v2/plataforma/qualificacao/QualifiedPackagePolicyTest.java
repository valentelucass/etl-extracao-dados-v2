package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualifiedPackagePolicyTest {
    @TempDir Path root;

    @Test
    void rejectsUnsafeWindowsMembersAndUndeclaredRoles() {
        for (final String path :
                List.of(
                        "../escape.json",
                        "a/../b.json",
                        "a//b.json",
                        "a/./b.json",
                        "a/CON.txt",
                        "a/COM1.log",
                        "a/b.",
                        "a/",
                        "package.json",
                        "package.sha256")) {
            assertEquals(
                    "QUAL_PACKAGE_PATH",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> QualifiedPackage.safeMember(path))
                            .getMessage());
        }
        assertEquals("fixtures/a.json", QualifiedPackage.safeMember("fixtures/a.json"));
        final var payload = new QualifiedPackage(root, "a".repeat(64), "b".repeat(64), Map.of());
        assertEquals(
                "QUAL_PACKAGE_REQUIRED_MEMBER",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> payload.member("fixtures/a.json", "FIXTURE"))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class, () -> new QualifiedPackage(root, "a", "b", null));
    }

    @Test
    void campaignPinsMustMatchEverySealedMember() {
        final var members = new HashMap<String, QualifiedPackage.Member>();
        for (final String name :
                List.of(
                        "etl-dataexport-v2.jar",
                        "schema/schema-index.json",
                        "contracts/query-contracts.synthetic.json",
                        "fixtures/fixture-index.json",
                        "oracles/outputs.synthetic.json")) {
            members.put(
                    name, new QualifiedPackage.Member(name, 1, "a".repeat(64), "JSON", "POLICY"));
        }
        final var payload = new QualifiedPackage(root, "a".repeat(64), "b".repeat(64), members);
        final var accepted =
                new QualificationCampaign.Pins(
                        "b".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64));
        payload.verifyPins(accepted);
        final var drifted =
                new QualificationCampaign.Pins(
                        "c".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64),
                        "a".repeat(64));
        assertEquals(
                "QUAL_PACKAGE_CAMPAIGN_REVISION",
                assertThrows(IllegalArgumentException.class, () -> payload.verifyPins(drifted))
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> QualifiedPackage.verify(root, "bad"));
    }

    @Test
    void artifactIndexAcceptsOnlyDeclaredScopedSyntheticMembers() throws Exception {
        final String index = "artifact-cases/index.json";
        final String input = "artifact-cases/case-a/input.json";
        final String oracle = "artifact-cases/case-a/oracle.json";
        final Path file = root.resolve(index);
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                """
                {"version":"qualification-artifact-cases-v1",\
                 "origin":"LOCAL_SYNTHETIC_ARTIFACT_V1",\
                 "cases":[{"id":"case-a","members":[\
                   {"path":"artifact-cases/case-a/input.json","role":"FIXTURE"},\
                   {"path":"artifact-cases/case-a/oracle.json","role":"ORACLE"}]}]}
                """);
        final var members = new HashMap<String, QualifiedPackage.Member>();
        members.put(index, member(index, "FIXTURE"));
        members.put(input, member(input, "FIXTURE"));
        members.put(oracle, member(oracle, "ORACLE"));
        final var payload = new QualifiedPackage(root, "a".repeat(64), "b".repeat(64), members);
        assertEquals(java.util.Set.of(index, input), QualificationArtifactIndex.fixtures(payload));

        final var orphan = new HashMap<>(members);
        final String extra = "artifact-cases/case-a/extra.json";
        orphan.put(extra, member(extra, "FIXTURE"));
        assertEquals(
                "QUAL_ARTIFACT_INDEX_SET",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationArtifactIndex.fixtures(
                                                new QualifiedPackage(
                                                        root,
                                                        "a".repeat(64),
                                                        "b".repeat(64),
                                                        orphan)))
                        .getMessage());
        members.remove(index);
        assertEquals(
                "QUAL_ARTIFACT_INDEX_MISSING",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationArtifactIndex.fixtures(
                                                new QualifiedPackage(
                                                        root,
                                                        "a".repeat(64),
                                                        "b".repeat(64),
                                                        members)))
                        .getMessage());
        assertTrue(
                QualificationArtifactIndex.fixtures(
                                new QualifiedPackage(
                                        root, "a".repeat(64), "b".repeat(64), Map.of()))
                        .isEmpty());
    }

    private static QualifiedPackage.Member member(final String path, final String role) {
        return new QualifiedPackage.Member(path, 1, "a".repeat(64), "JSON", role);
    }
}
