package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalRegexEquivalenceTest {
    private static final String SHA = "a".repeat(64);
    private static final String CODE = "b".repeat(64);
    @TempDir Path directory;
    private int ordinal;

    @Test
    void campaignPinsKeepLegacyDigestAcceptanceIncludingUnicodeAndFinalNewline() throws Exception {
        for (final String value : digests()) {
            final boolean accepted = value.matches("[a-f0-9]{64}");
            final Path root = next();
            if (accepted) {
                assertEquals(0, CampaignJournal.create(root, value, CODE).read().size());
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CampaignJournal.create(root, value, CODE));
            }
        }
        assertThrows(
                IllegalArgumentException.class, () -> CampaignJournal.create(next(), null, CODE));
    }

    @Test
    void caseIdsKeepTheSameAsciiAlphabetAndFortyCharacterBoundary() throws Exception {
        for (final String value :
                new String[] {
                    "a",
                    "a0-",
                    "a".repeat(40),
                    "a".repeat(41),
                    "",
                    "A",
                    "a_",
                    "0a",
                    "a\n",
                    "a\u00e9"
                }) {
            final var journal = campaign();
            if (value.matches("[a-z][a-z0-9-]{0,39}")) {
                assertEquals(
                        value,
                        journal.append(value, nonce(), CampaignJournal.Kind.RESERVED, "FACT_APPEND")
                                .caseId());
                assertEquals(1, journal.read().size());
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                journal.append(
                                        value,
                                        nonce(),
                                        CampaignJournal.Kind.RESERVED,
                                        "FACT_APPEND"));
                assertEquals(0, journal.read().size());
            }
        }
    }

    @Test
    void genericDetailsKeepTheSameAlphabetAnd256CharacterBoundary() throws Exception {
        for (final String value :
                new String[] {
                    "Aa0_:.,-", "x".repeat(256), "x".repeat(257), "", "x ", "x\n", "x\r\n", "\u00c1"
                }) {
            final var journal = campaign();
            if (value.matches("[A-Za-z0-9_:.,-]{1,256}")) {
                assertEquals(
                        value,
                        journal.append("case", nonce(), CampaignJournal.Kind.RESERVED, value)
                                .detail());
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                journal.append(
                                        "case", nonce(), CampaignJournal.Kind.RESERVED, value));
                assertEquals(0, journal.read().size());
            }
        }
    }

    @Test
    void evidenceTransitionsStillRequireTheExactLowercaseDigest() throws Exception {
        for (final String value : digests()) {
            final var journal = campaign();
            final UUID nonce = nonce();
            journal.append("evidence-case", nonce, CampaignJournal.Kind.RESERVED, "FACT_APPEND");
            journal.append("evidence-case", nonce, CampaignJournal.Kind.STARTED, "FACT_APPEND");
            if (value.matches("[a-f0-9]{64}")) {
                assertEquals(
                        value,
                        journal.append("evidence-case", nonce, CampaignJournal.Kind.EVIDENCE, value)
                                .detail());
                assertEquals(3, journal.read().size());
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                journal.append(
                                        "evidence-case",
                                        nonce,
                                        CampaignJournal.Kind.EVIDENCE,
                                        value));
                assertEquals(2, journal.read().size());
            }
        }
    }

    @Test
    void eventNamesKeepAsciiDigitsCaseAndExactSuffixBeforeJsonValidation() throws Exception {
        for (final String value :
                new String[] {
                    "0001.json",
                    "0512.json",
                    "001.json",
                    "00001.json",
                    "0001.JSON",
                    "0001a.json",
                    "\uff10\uff10\uff10\uff11.json"
                }) {
            final var journal = campaign();
            Files.writeString(journal.root().resolve(value), "{}");
            final var refused = assertThrows(IllegalArgumentException.class, journal::read);
            if (value.matches("[0-9]{4}\\.json")) {
                assertNotEquals("QUAL_JOURNAL_UNDECLARED_FILE", refused.getMessage());
            } else {
                assertEquals("QUAL_JOURNAL_UNDECLARED_FILE", refused.getMessage());
            }
        }
    }

    @Test
    void persistentKindsKeepAsciiUppercaseAndUnderscoresWith32CharacterBound() throws Exception {
        try (var journal = PersistentExportJournal.create(next(), SHA, CODE, nonce())) {
            for (final String value :
                    new String[] {
                        "A",
                        "_",
                        "ROOT_FACT",
                        "Z".repeat(32),
                        "Z".repeat(33),
                        "",
                        "a",
                        "A1",
                        "A\n",
                        "\u00c1"
                    }) {
                if (value.matches("[A-Z_]{1,32}")) {
                    journal.record(
                            value, JsonNodeFactory.instance.objectNode().put("value", value));
                    assertEquals(value, journal.latest(value).required("value").textValue());
                } else {
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> journal.record(value, JsonNodeFactory.instance.objectNode()));
                    assertEquals(0L, journal.count(value));
                }
            }
        }
    }

    @Test
    void persistentConstructorPinsKeepTheSameDigestPredicate() throws Exception {
        for (final String value : digests()) {
            final Path root = next();
            if (value.matches("[a-f0-9]{64}")) {
                try (var journal = PersistentExportJournal.create(root, value, CODE, nonce())) {
                    assertEquals(0L, journal.count("NOT_WRITTEN"));
                }
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PersistentExportJournal.create(root, value, CODE, nonce()));
            }
        }
    }

    @Test
    void persistentFactLookupRejectsInvalidDigestsAndKeepsActualPayloadPins() throws Exception {
        try (var journal = PersistentExportJournal.create(next(), SHA, CODE, nonce())) {
            final String retained =
                    journal.record(
                            "AUTHORED_FACT", JsonNodeFactory.instance.objectNode().put("v", 1));
            assertEquals(retained, journal.pin(retained).sha256());
            for (final String value : digests()) {
                if (!value.matches("[a-f0-9]{64}")) {
                    assertThrows(IllegalArgumentException.class, () -> journal.pin(value));
                }
            }
            assertEquals(1, journal.latest("AUTHORED_FACT").required("v").intValue());
        }
    }

    private static String[] digests() {
        return new String[] {
            "0123456789abcdef".repeat(4),
            "a".repeat(63),
            "a".repeat(65),
            "A".repeat(64),
            "a".repeat(63) + "g",
            SHA + "\n",
            SHA + "\r\n",
            "a".repeat(63) + "\u00e9",
            ""
        };
    }

    private Path next() {
        return directory.resolve("authored-" + ++ordinal);
    }

    private UUID nonce() {
        return UUID.nameUUIDFromBytes(
                ("authored-regex-" + ++ordinal).getBytes(StandardCharsets.UTF_8));
    }

    private CampaignJournal campaign() throws IOException {
        return CampaignJournal.create(next(), SHA, CODE);
    }
}
