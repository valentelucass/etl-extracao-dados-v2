# Catálogo V2-013 — Q-SWP-FND-01

Este catálogo versiona somente a fundação local, offline e preview-only do kernel fail-closed de Sweep and Prune. O resultado máximo é `FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED`.

## Conteúdo

- `matriz-aplicabilidade-v01.csv`: 33 responsabilidades, 11 famílias, zero `ENABLED` e zero `PROVEN_COMPLETE`.
- `fixtures/kernel-cases-v01.synthetic.json`: 53 casos fechados, sendo um happy path sintético e 52 mutantes reais.
- `manifesto.json` e `manifesto.sha256`: inventário, hashes, bindings, vocabulários e limites.

Os roots já implementados preservam suas chaves aceitas (`/id`, `/sequence_code`, `/corporation_sequence_number` e `/node/id`); o bloqueio atual é completude/paridade V2-012b, não redescoberta de identidade. Pick e MDF-e mantêm chaves próprias sob ownership do Manifesto. Performance de Fretes e histórico de Usuários não possuem lifecycle independente. O sidecar GraphQL de Fretes é canal observacional, não filho. Tarifa de Cotações é referência separada. Faturas 4924 distingue `/id` da source line do título lógico ainda não resolvido. CAP, Faturas, Inventário e Sinistros conservam candidatos sem promovê-los a filhos.

Fretes, Localização e Usuários permanecem `DISABLED` conforme suas decisões; `BLOCKED_NO_COMPLETENESS_PROOF` é uma dimensão separada. Raster viagens/paradas permanece condicional e desligado. Nenhuma linha habilita execução.

Ordinais e fingerprints de ocorrência/execução são somente envelopes técnicos O(1). Eles não autenticam uma origem e uma diferença de hash isolada jamais satisfaz proveniência, owner ou gate nominal por entidade. O único happy path positivo é sintético/local e habilita zero entidades.

Somente `ROOT` pode alcançar o resultado sintético de preview. `CHILD`, inclusive Pick e MDF-e, permanece bloqueado até um planner nominal por entidade vincular a avaliação do pai e a propagação atômica; a matriz não declara esse gate satisfeito.

O gerador deve produzir bytes UTF-8 sem BOM e LF determinísticos. `Build-V2013SweepApplicabilityCatalog.ps1 -VerifyGenerated` recusa drift. `Test-V2013SweepPreviewFoundation.ps1 -ArtifactsOnly` valida catálogo, hashes, schema fechado e fronteira; o modo completo também exige o fechamento central coerente.

Não há rede, banco, filesystem no código produtivo, SQL, migration, runtime wiring, universo de chaves, anti-join, persistência, sweep, prune, delete, desativação, publicação, deploy ou cutover.
