# 0377 — P08: campanha de seis esperas pausada antes de efeito físico

- Data: 2026-09-29 UTC. Anterior: [0376](0376-p08-primeira-espera-it-dirigida-pass.md), SHA-256 `A2A252E7B2B34A8B1115444579F689F84E0450D732A3EBAF8A97665566C71B81`.
- Autoridade inicial: Supervisor autorizou campanha finita de seis métodos restantes dos sete sintomas 0354, serial, um método por chamada Maven, teto total 3600 s e até 600 s por método, com reserva/preflight/guard/readback próprios. **Autoridade substituída durante a preparação** pela ordem urgente do usuário: documentar e pausar, não iniciar Maven, SQL, preflight, reserva ou método novos; concluir somente observação/fechamento seguro de processo já em curso.
- Estado: **PAUSADO_POR_USUARIO_SEM_EFEITO_FISICO**. Nenhum dos seis métodos foi executado; nenhum resultado físico incerto.

## Preparação observada antes da pausa

1. Documentos obrigatórios, checkpoints 0354/0365/0366/0374/0376 e lista Maestri foram consultados. `../CONTEXTO_GLOBAL.md` segue ausente no caminho esperado. Cópia privada da campanha em `target/p08-six-waits-20260929-01-campaign/` e do primeiro round em `target/p08-six-waits-20260929-01-m01/`; espelho novo `C:\Users\lucas\p08m0377_01`, inicialmente sem `target`/`.env` herdados. Inventário de **2170/2170** hashes e pins 0374 revision/manifest/ZIP/lock conferidos apenas como candidatos offline. `mirror-proof.json` SHA `B61C98492197CF777A342D1727E55734C171AC2257F7F1AC5DD3D8DF6C7C1C6E`; lista ordenada dos seis seletores `methods.json` SHA `07E5234BFDE02ACB6DE83DAA6A2869E1C1EE154345D6BAAB9A76CB00211FEFA8`.
2. Primeira invocação do helper privado `offline-proof.ps1` recusou `OFFLINE_INPUT_INVALID`: PowerShell 5.1 tratou o JSON como array aninhado. **Não iniciou Maven, SQL ou JDBC.** O helper privado foi corrigido para leitura direta do array. Essa falha de preparação é mantida como observação histórica desta unidade; não altera FAILs/outputs anteriores.
3. Uma compilação `test-compile` JDK17/Maven 3.9.14 **offline**, iniciada antes da ordem de pausa, concluiu com exit 0 após a ordem, sem perfil shadow, URL ou JDBC. Spotless check ativo, Checkstyle ativo com **0 violações**, `BUILD SUCCESS`; os seis métodos exatos foram encontrados uma vez cada no bytecode, e os 2170 inputs permaneceram sem drift. `offline-proof.json` SHA `589C91A3EDE20C11D2A0DC8B148EA5D01884D469000286F0CF2F89D9A83DA5CD`; log bruto privado da compilação SHA `2C88E68495DF01D1EC77C32BE8F2F5C08740346CD37A01CF17ADAA2085EBF78C`. Nenhuma IT foi selecionada ou executada na compilação. O `target` do espelho agora contém **somente artefatos de build offline** e não serve como espelho limpo para futuro efeito físico.

## Estado dos métodos, reserva e processos

| Ordem | Método | Estado nesta campanha |
| --- | --- | --- |
| 1 | `AnalyticExpansionCaptureIT#packagedInputsHydrateMissingFreightAndReuseBothFactsAcrossFourModes` | NÃO INICIADO |
| 2 | `AnalyticFixtureBindingsIT#currentCaptureProvenanceFeedsSixDimensionsWithoutMergingHomonymousUsers` | NÃO INICIADO |
| 3 | `AnalyticLaboratoryCollectionAliasIT#stagingValueWrapperPreservesNumericAliasAndTheCaptureContractRejectsTextualDrift` | NÃO INICIADO |
| 4 | `AnalyticLaboratoryCollectionQueriesIT#regionUsesCepThenCityThenTextAndMissingReleaseBlocksReadiness` | NÃO INICIADO |
| 5 | `AnalyticLaboratoryCollectionQueriesIT#lateralAbsentPreservesWhileExplicitNullClearsWithoutRewritingObservedFields` | NÃO INICIADO |
| 6 | `AnalyticLaboratoryRasterProofsIT#splitCapturesProveEveryLeafWhileMinimumWindowCapRollsBack` | NÃO INICIADO |

- Verificação de parada: **zero** reserva de campanha/round, **zero** ledger físico novo, preflight/guard/SQL/JDBC **não iniciados**, `physical-result.json` ausente, zero XML Failsafe, **zero** processo Java/cmd próprio do espelho remanescente. Não havia transação própria para interromper ou reconciliar e nenhum readback SQL a executar. Não existe reserva para fechar; outcome físico **NÃO INICIADO**, não desconhecido.
- O método dirigido 0376 continua PASS apenas naquela rodada, sem repetição nesta campanha. FAILs, outputs, checkpoints e ledgers 0354/0368–0371/0375/0376 preservados. **8 erros históricos 0354**, **74 classes faltantes**, sete esperas sem causa física atribuída, JaCoCo não alcançado, A/B físico e Gate 1/P08 abertos; stats **2536 apenas observação, sem baseline aceita**; pins 0374 não aceitos.
- Mudanças desta unidade: somente artefatos privados offline, `STATES.md`, `RETOMADA.md` e este checkpoint. Nenhum código versionado, contrato, Runtime, migration, schema, banco, login, serviço ou fonte real alterado.

## Retomada e handoff

1. **Aguardar novo pedido explícito do usuário.** A ordem de pausa substitui a autorização restante da campanha; não usar o tempo ou a reserva propostos como autorização persistente.
2. Se o usuário autorizar nova unidade, Supervisor define escopo/teto. Banco reconfere bytes/pins/estado atual e prepara **espelho físico novo sem `target` herdado**, reserva/preflight/guard categórico próprios antes do método **1**, preservando esta prova offline como histórico; não repetir o método 0376 ou qualquer efeito já registrado.
3. Supervisor decide quando e se retomar os seis métodos; Banco permanece único executor SQL/JDBC/ledger e o terminal fica ocioso após handoff. Nenhum aceite P08/A-B decorre desta preparação.
