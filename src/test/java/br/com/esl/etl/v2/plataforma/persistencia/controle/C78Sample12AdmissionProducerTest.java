package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure structure/wire components only. No fixture authenticates Root, Source or physical SQL. */
class C78Sample12AdmissionProducerTest {
    @Test
    void factoryIsConcreteButMissingRootRefusesBeforeSourceOrConnection() throws Exception {
        var selected = C78Sample12OwnerTest.selection();
        var calls = new AtomicInteger();
        var source =
                new RuntimeDevEngineBankBinding.Admission() {
                    public void verify() {
                        calls.incrementAndGet();
                    }

                    public RuntimeDevEngineBankBinding.Session open() {
                        throw new AssertionError();
                    }

                    public void close() {}
                };
        try (var producer = C78Sample12AdmissionProducer.sample12(selected, null, null, source);
                var prepared =
                        new C78DevEngineBankAdapter(null, null, producer)
                                .prepare(selected.scope())) {
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, prepared::verify)
                            .gate());
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, prepared::open).gate());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void sourceZeroStructureCanNeverOpenEvenWithAllOtherLocalBytes() throws Exception {
        var f = new LocalFixture(true);
        try (var controls = f.controls()) {
            C78Sample12AdmissionProducer.verifyLocal(controls, f.selected);
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, controls::start)
                            .gate());
            assertThrows(IllegalStateException.class, controls::sampleSecret);
            assertFalse(Files.exists(f.occurrence.resolve("runtime-engine-claim.json")));
        }
    }

    @Test
    void positiveStructureAndRepeatedPureRecheckGrantNoPhysicalAuthority() throws Exception {
        var f = new LocalFixture(false);
        try (var controls = f.controls()) {
            controls.verify();
            controls.verify();
            assertThrows(IllegalStateException.class, controls::sampleSecret);
            assertFalse(Files.exists(f.occurrence.resolve("runtime-engine-claim.json")));
        }
    }

    @Test
    void callRefusesWhenOnlyItsExecutionFitsButItsOwnerProofDoesNot() throws Exception {
        var f = new LocalFixture(false);
        try (var controls = f.controls()) {
            controls.reserveSampleCleanup();
            for (int index = 0; index < 124; index++) {
                controls.consumeStatement();
            }
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.BUDGET,
                    assertThrows(
                                    RuntimeDevEngineBankBinding.Refusal.class,
                                    controls::requireSampleCallProofBudget)
                            .gate());
            assertEquals(124, controls.statementsUsed());
            for (int index = 0; index < 3; index++) {
                controls.consumeSampleCleanup();
            }
            assertEquals(127, controls.statementsUsed());
        }
    }

    @Test
    void rawLimitStringsFractionsOverflowAndWideningRefuse() throws Exception {
        for (String invalid : List.of("\"128\"", "128.0", "128.5", "2147483648", "129", "-1")) {
            var f = new LocalFixture(false);
            f.d.withObject("limits")
                    .set("statements", C78DevEngineBankAdapterTest.JSON.readTree(invalid));
            f.save();
            try (var controls = f.controls()) {
                assertEquals(
                        RuntimeDevEngineBankBinding.MissingGate.BUDGET,
                        assertThrows(RuntimeDevEngineBankBinding.Refusal.class, controls::verify)
                                .gate());
            }
        }
    }

    @Test
    void tableDropExtraLimitOrUnreviewedQualificationRefuses() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            var f = new LocalFixture(false);
            if (kind == 0) {
                f.d.withObject("writerProtectionPlan").withArray("tables").remove(0);
            }
            if (kind == 1) {
                f.d.withObject("limits").put("extra", 1);
            }
            if (kind == 2) {
                f.d.withObject("sqlQualification").put("sha256", "f".repeat(64));
            }
            f.save();
            try (var controls = f.controls()) {
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, controls::verify);
            }
        }
    }

    @Test
    void existingUtf16PrivateFrameDoesNotConsumeFollowingPermit() throws Exception {
        byte[] secret = "offline-component-value".getBytes(StandardCharsets.UTF_16LE);
        var output = new java.io.ByteArrayOutputStream();
        output.write(
                ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(secret.length)
                        .array());
        output.write(secret);
        output.write("NEXT\n".getBytes(StandardCharsets.US_ASCII));
        var input = new ByteArrayInputStream(output.toByteArray());
        assertEquals("offline-component-value", C78Sample12SecretFrame.read(input));
        assertEquals("NEXT\n", new String(input.readAllBytes(), StandardCharsets.US_ASCII));
    }

    @Test
    void privateFrameLimitsTruncationAndInvalidEncodingRefuseBeforeAllocationOrUse() {
        for (int size : new int[] {0, 1, 8193, Integer.MAX_VALUE, -1}) {
            var header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(size).array();
            assertThrows(
                    IOException.class,
                    () -> C78Sample12SecretFrame.read(new ByteArrayInputStream(header)));
        }
        for (byte[] body :
                new byte[][] {
                    new byte[0], new byte[] {0}, new byte[] {0, 0}, new byte[] {0, (byte) 0xD8}
                }) {
            var out = new java.io.ByteArrayOutputStream();
            out.writeBytes(new byte[] {2, 0, 0, 0});
            out.writeBytes(body);
            assertThrows(
                    IOException.class,
                    () -> C78Sample12SecretFrame.read(new ByteArrayInputStream(out.toByteArray())));
        }
    }

    static final class LocalFixture {
        final C78Sample12Selection selected = C78Sample12OwnerTest.selection();
        final C78DevEngineBankAdapterTest.Fixture old =
                new C78DevEngineBankAdapterTest.Fixture(selected.scope());
        final ObjectNode d = old.dispatch.deepCopy();
        final Path path = old.path.resolveSibling("sample12-dispatch113.json");
        final Path occurrence =
                path.getParent()
                        .getParent()
                        .resolve("banco01/occurrences/" + d.path("dispatchId").textValue());
        String hash;

        LocalFixture(boolean zero) throws Exception {
            d.put("schema", "V2_DEV_C78_SAMPLE12_DISPATCH_V1")
                    .put("operation", "SAMPLE12_ROLLBACK_ONLY")
                    .put("profile", "C78_LOCAL_SHADOW_SQL_PIN_V1")
                    .put(
                            "mode",
                            zero
                                    ? "SOURCE0_PUBLIC_MAIN_NEGATIVE"
                                    : "PHYSICAL_SAMPLE12_ROLLBACK_ONLY");
            var limits = C78DevEngineBankAdapterTest.JSON.createObjectNode();
            limits.put("connections", zero ? 0 : 1)
                    .put("statements", zero ? 0 : 128)
                    .put("HTTP", zero ? 0 : 2)
                    .put("provider", zero ? 0 : 1)
                    .put("rows", zero ? 0 : 12)
                    .put("querySeconds", 10)
                    .put("unitSeconds", 60)
                    .put("retry", 0)
                    .put("commit", 0)
                    .put("promotion", 0);
            d.set("limits", limits);
            Path original = Path.of(System.getProperty("bank.sample.qualification"));
            Path copy = path.getParent().resolve("sample-contract.json");
            Files.copy(original, copy);
            var ref =
                    C78DevEngineBankAdapterTest.JSON
                            .createObjectNode()
                            .put("path", copy.toString())
                            .put("sha256", digest(Files.readAllBytes(copy)));
            d.set("sqlQualification", ref);
            save();
        }

        void save() throws Exception {
            byte[] bytes = C78DevEngineBankAdapterTest.JSON.writeValueAsBytes(d);
            Files.write(path, bytes);
            hash = digest(bytes);
        }

        C78DevEngineDispatch controls() throws Exception {
            return C78DevEngineDispatch.readSample12(path, hash, selected);
        }

        private static String digest(byte[] bytes) throws Exception {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        }
    }
}
