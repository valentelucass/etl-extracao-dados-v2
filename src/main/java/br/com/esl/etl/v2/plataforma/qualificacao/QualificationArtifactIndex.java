package br.com.esl.etl.v2.plataforma.qualificacao;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Exact optional file-input index for the existing closed package, within its unchanged caps. */
public final class QualificationArtifactIndex {
    private static final String INDEX = "artifact-cases/index.json";

    private QualificationArtifactIndex() {}

    public static void verify(final QualifiedPackage payload) throws IOException {
        fixtures(payload);
    }

    static Set<String> fixtures(final QualifiedPackage payload) throws IOException {
        if (!payload.members().containsKey(INDEX)) {
            if (payload.members().keySet().stream()
                    .anyMatch(path -> path.startsWith("artifact-cases/"))) {
                throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_MISSING");
            }
            return Set.of();
        }
        final var index = QualificationJson.read(payload.member(INDEX, "FIXTURE"), 262144);
        QualificationJson.fields(index, "version", "origin", "cases");
        final boolean sequences =
                "qualification-artifact-cases-v2"
                        .equals(QualificationJson.text(index, "version", 40));
        if (!(sequences
                        || "qualification-artifact-cases-v1"
                                .equals(QualificationJson.text(index, "version", 40)))
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1"
                        .equals(QualificationJson.text(index, "origin", 40))) {
            throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_ORIGIN");
        }
        QualificationJson.array(index.path("cases"), 1, 4);
        final var seen = new HashSet<String>();
        final var fixtures = new HashSet<String>();
        final var ids = new HashSet<String>();
        seen.add(INDEX);
        fixtures.add(INDEX);
        for (final var item : index.path("cases")) {
            if (sequences) {
                QualificationJson.fields(item, "id", "kind", "members");
            } else {
                QualificationJson.fields(item, "id", "members");
            }
            final String kind = sequences ? QualificationJson.text(item, "kind", 16) : "SCENARIO";
            if (!Set.of("SCENARIO", "SEQUENCE").contains(kind)) {
                throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_KIND");
            }
            final String id = QualificationJson.text(item, "id", 40);
            if (!id.matches("[a-z][a-z0-9-]{0,39}") || !ids.add(id)) {
                throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_ID");
            }
            QualificationJson.array(item.path("members"), 2, 340);
            final String prefix = "artifact-cases/" + id + "/";
            for (final var member : item.path("members")) {
                QualificationJson.fields(member, "path", "role");
                final String path =
                        QualifiedPackage.safeMember(QualificationJson.text(member, "path", 200));
                final String role = QualificationJson.text(member, "role", 24);
                if (!path.startsWith(prefix)
                        || !path.endsWith(".json")
                        || !Set.of("FIXTURE", "ORACLE").contains(role)
                        || !seen.add(path)) {
                    throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_MEMBER");
                }
                payload.member(path, role);
                if (role.equals("FIXTURE")) {
                    fixtures.add(path);
                }
            }
            if (kind.equals("SEQUENCE")) {
                if (!seen.contains(prefix + "sequence.json")) {
                    throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_ROOTS");
                }
                payload.member(prefix + "sequence.json", "FIXTURE");
            } else {
                if (!seen.contains(prefix + "input.json")
                        || !seen.contains(prefix + "oracle.json")) {
                    throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_ROOTS");
                }
                payload.member(prefix + "input.json", "FIXTURE");
                payload.member(prefix + "oracle.json", "ORACLE");
            }
        }
        final var actual = new HashSet<String>();
        payload.members().keySet().stream()
                .filter(path -> path.startsWith("artifact-cases/"))
                .forEach(actual::add);
        if (!seen.equals(actual)) {
            throw new IllegalArgumentException("QUAL_ARTIFACT_INDEX_SET");
        }
        return Set.copyOf(fixtures);
    }
}
