package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** P1B frozen technical pins; no runtime selector or source permission. */
final class DataExportObservedEntityPins {
    private DataExportObservedEntityPins() {}

    record Pin(
            DataExportTemplate template,
            int templateId,
            String version,
            String resource,
            String resourceSha256,
            String metadataSha256,
            String responseSha256,
            String releaseSha256,
            int fieldCount,
            int filterCount,
            int responseFieldCount) {}

    static final java.util.List<Pin> ALL =
            java.util.List.of(
                    new Pin(
                            DataExportTemplate.MANIFESTOS,
                            6399,
                            "2026-10-25.p2-manifestos-info-qualified.v1",
                            "/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json",
                            "fbb1d4b349b02dbf9450cb2ec113eacf55643a219e837bf4ce624d7b7b43cccc",
                            "eb7aade2963e4f03e9d80a40d9b296944981a3a133a728c539b224f8e05455df",
                            "b9d3af595e9db1dae53bd9249a26dae2cf89872c5a46a4882ed54da50fe78ccd",
                            "6fd4d10fc52718ce6d1ab684d5188f413cbd1241bb4eedf140b4e349298eab04",
                            91,
                            18,
                            95),
                    new Pin(
                            DataExportTemplate.COTACOES,
                            6906,
                            "2026-10-25.p2-cotacoes-info-qualified.v1",
                            "/contracts/dataexport/cotacoes-6906-observed-p2-info-qualified-20261025.json",
                            "8e6fc68811ef08e707dfbedd21818efa9bb8c2943d9d253183640cb5ae17799c",
                            "81baeba1c15bef7a311a8f7b366ac794b419ac5cf534a182ede2d09e129a767f",
                            "6ba985aa6f4f4e9b0dd775d2bab1a578d9b6bad69a96b3c671113cdd74eb9857",
                            "73fa26b4c8986188a6c2e8c386d29eff9a8f7f3fb01db86cc5d78f9aceec173d",
                            37,
                            6,
                            37),
                    new Pin(
                            DataExportTemplate.LOCALIZACAO_CARGAS,
                            8656,
                            "2026-10-24.p1b-localizacao-cargas-observed.v1",
                            "/contracts/dataexport/localizacao-cargas-8656-observed-p1b-20261024.json",
                            "4abf6c4735a4e3e53e6599dfc5f485132a3f749f4ac6c2d8ab3956ea37aa2997",
                            "215511b7642f7277fcb256f197e2f48adb021a1d8b12e5a1df497771559021f2",
                            "d99335f468f9667c9b4f0a982a462499ceb9bf866a6a6ec98f1d69c4d6e908ac",
                            "24373b7c25cb4c7df64e260fbd3c0624c05255aeb2dc28be3b774a1216c8482f",
                            26,
                            8,
                            27));
}
