package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.io.IOException;
import java.nio.file.Path;

/** Closed request identities. Integrity pins do not grant authority or source access. */
enum C78Sample12Namespace {
    F4(
            "hermesf4eda62e83d1",
            "HERMES_f4eda62e83d1",
            "C78SAMPLE12-[a-f0-9]{32}",
            "IssueSample12Admission113.py",
            "4c0b4e7c5e5518df5782579b4d32b1d8723af196a65a6f3eb5e690848296d87d",
            "RunC78Sample12Admission113.py",
            "39b4b8048fd1673fafa576afb1aea71eb05a93b8f26784fb57bc1181a99d7844"),
    C54(
            "hermesc54d6a25880d",
            "HERMES_c54d6a25880d",
            "C54SAMPLE12-[a-f0-9]{32}",
            "IssueSample12AdmissionC54.py",
            "e1f743049599232891b174fa0dee194691818d1a3668cba2ed423e29cfb23bb0",
            "RunC78Sample12AdmissionC54Bridge.py",
            "e9f58477959d971809d9e236a46dd0ae71507fb5962b9e23041b3dc19f46c5ce");

    final String unitName;
    final String request;
    final String dispatchPattern;
    final String issuerName;
    final String issuerSHA256;
    final String controllerName;
    final String controllerSHA256;

    C78Sample12Namespace(
            final String unitName,
            final String request,
            final String dispatchPattern,
            final String issuerName,
            final String issuerSHA256,
            final String controllerName,
            final String controllerSHA256) {
        this.unitName = unitName;
        this.request = request;
        this.dispatchPattern = dispatchPattern;
        this.issuerName = issuerName;
        this.issuerSHA256 = issuerSHA256;
        this.controllerName = controllerName;
        this.controllerSHA256 = controllerSHA256;
    }

    static C78Sample12Namespace from(final Path unit) throws IOException {
        if (unit == null || unit.getFileName() == null) {
            throw new IOException("ROOT_SAMPLE12_REQUEST_NAMESPACE_REFUSED");
        }
        return switch (unit.getFileName().toString()) {
            case "hermesf4eda62e83d1" -> F4;
            case "hermesc54d6a25880d" -> C54;
            default -> throw new IOException("ROOT_SAMPLE12_REQUEST_NAMESPACE_REFUSED");
        };
    }
}
