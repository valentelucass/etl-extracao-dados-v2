package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Code-owned hashes for four observed wire resources. Hashes are integrity, not authority. */
final class ObservedExpansionSchemaPins {
    private ObservedExpansionSchemaPins() {}

    record Pin(
            int templateId,
            String release,
            String resource,
            String sha256,
            int fields,
            int filters) {}

    static Pin pin(final ObservedExpansionCaptureTemplate template) {
        return switch (template) {
            case CAP ->
                    new Pin(
                            8636,
                            "observed-expansion-wire-8636-20261025-v1",
                            "/contracts/dataexport/expansion-8636-observed-wire-20261025-v1.json",
                            "863f46f21d232c8bc8f27e9803061492ed613c020c42c211ffadf93c3aa17277",
                            28,
                            9);
            case FAT ->
                    new Pin(
                            4924,
                            "observed-expansion-wire-4924-20261025-v1",
                            "/contracts/dataexport/expansion-4924-observed-wire-20261025-v1.json",
                            "4bdc78464659c30367ba8d97ed7e9a5f88824503beaa05aaff621c5e97354405",
                            52,
                            18);
            case INV ->
                    new Pin(
                            10633,
                            "observed-expansion-wire-10633-20261025-v1",
                            "/contracts/dataexport/expansion-10633-observed-wire-20261025-v1.json",
                            "03e0e446ea19bb2d70b33056d05a36fef96981f710dfa216e1a8e41e1f3e5794",
                            26,
                            7);
            case SIN ->
                    new Pin(
                            6392,
                            "observed-expansion-wire-6392-20261025-v1",
                            "/contracts/dataexport/expansion-6392-observed-wire-20261025-v1.json",
                            "1082b9d5be12ea9107830f5b287bb324d886efd83e313a7ae8977384e203a90d",
                            44,
                            6);
        };
    }
}
