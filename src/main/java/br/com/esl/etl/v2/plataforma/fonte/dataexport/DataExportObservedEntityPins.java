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
                            "2026-10-24.p1b-manifestos-observed.v1",
                            "/contracts/dataexport/manifestos-6399-observed-p1b-20261024.json",
                            "3b398b00a81349edff085c9b677bca7906d2b42b9aa516823ae2be3152e80cd0",
                            "304bb002e4c9beef86200fde9f6b1be1297c1814495810c2838d154653a86f61",
                            "9e3e3180eee134ea27d63ddd1c2abf78cc0ce521ad8f9d52a7dc732472e702c3",
                            "6fbc71f29c4062324ec5ccd248b7adf9638cc2c60c81d69379b429fbeba4f77f",
                            91,
                            18,
                            95),
                    new Pin(
                            DataExportTemplate.COTACOES,
                            6906,
                            "2026-10-24.p1b-cotacoes-observed.v1",
                            "/contracts/dataexport/cotacoes-6906-observed-p1b-20261024.json",
                            "56226741859dcea3f0258d241375f0eb538088e1c93a116a1ca3f7e643452ae4",
                            "a76cfc75333b32ffd0aa026607d7d690967af6960cb410bf9a3c786ed7824af1",
                            "f2254c46bacf3404fb8b74f360131d0dbf1ebb6c00642645dad0840f98429ce8",
                            "6dd5197a7fa7ef035c4d0c5420b2403da7260c9ae38e0c3d5e8da72d6f5523f2",
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
