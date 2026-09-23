# Checkpoint 0286 — matriz read-only de nove contratos — 22/09/2026

## Identificação e objetivo

- Anterior: `0285-identidade-coletas-6908-readonly.md`, SHA-256 `3d6de9fe22d7f7d578eb48cd3090c8a9d28e0867aa242c9a14f16500240acaba`.
- Objetivo do usuário: avançar para o bloco completo de contratos Data Export da V2-025d, com dados reais e limites conservadores.
- Estado: `TESTADO_NA_CAMADA` API read-only até a primeira falha de envelope; `IMPLEMENTADO_NAO_QUALIFICADO` para a correção local do parser.
- Critérios: `AGENTS.md` regras de parada e evidência sanitizada; `STATES.md` V2-025d/V2-041; matriz final de nove requisições.

## Autorização e limites

- O usuário ordenou avançar após receber a descrição do alcance adicional e havia dispensado rotação prévia para leituras. A exceção ficou nesta rodada read-only; não modifica a autoridade de produção, JAR, SQL ou credenciais e não fecha G01.
- Script novo: `scripts/probes/Invoke-DataExportNineContractProbe.ps1`, definições fixas para 6908, 6389, 6399, 6906, 8656, 8636, 4924, 10633 e 6392; `/info` e somente página 1 `GET_WITH_QUERY`, `per=3`, até 18 chamadas, 3 s entre chamadas, conexão/total 10/30 s, HTTPS, 10 MiB, sem redirect/retry/fallback. Para 8636 os dois filtros ficam na mesma requisição.
- Ordem exata e janela de um dia no diretório privado `target/nine-contracts-20260922-01/`; nenhuma mutação. Recuperação: reconciliar processo e recibo; não repetir automaticamente a requisição interrompida.

## Alterações e decisões

- O parser inicialmente aceitava apenas array. O cliente de contrato existente também aceita objeto único e objeto vazio; o novo script foi corrigido para esses shapes, com `id` escalar obrigatório em 6908/6389 e limite por entidades distintas em expansão física.
- Como o corpo da resposta de Fretes não foi retido, não se atribui com certeza o erro observado a um desses shapes. Uma execução futura com ordem própria poderá validar a correção; não houve replay nesta rodada.
- `STATES.md`, trilha, checkpoint e índice de retomada foram sincronizados. Nenhum baseline de contrato, código Java, SQL, schema, manifesto histórico ou credencial mudou.

## Execução e evidência

| Passo | Camada | Teto | Observado | Evidência privada |
| --- | --- | --- | --- | --- |
| Perfil 6908/6389 | API Data Export read-only | 18 chamadas máximas | quatro chamadas HTTP 200; 6908 metadata 31 campos/seis filtros e quatro linhas físicas com limite válido; 6389 metadata 110 campos/16 filtros, `/data` recusado com `DATA_ENVELOPE_INVALID`; parada imediata, sete templates não consultados | `target/nine-contracts-20260922-01/summary.json`, SHA-256 `1bbd4693f23c08e1bc88dcb2e26e10c75d8c959b9b144a54a745f82d5a24b67` |
| Parser corrigido | PowerShell local | zero rede | self-test passou: lista, objeto único/vazio, ID textual e ausente, expansão válida/inválida, forma inválida | stdout sanitizado do `-SelfTest` |

- Aceites canônicos fechados: nenhum. V2-025d requer as nove linhas e critérios de contrato completos; V2-041 exige prova operacional. Contadores preservados em 67/115.
- `git diff --check`, UTF-8 estrito e whitespace final passaram. `Test-Gpt56ChatTrail.ps1` saiu 1 em `HANDOFF_PIN` histórico; ledger/selo anteriores preservados. Java/SQL/schema não mudaram e suas suítes não foram repetidas.

## Retomada imediata

1. Reconciliar recibo e processo desta rodada antes de qualquer nova ordem; a rodada encerrou no primeiro erro, sem chamadas remanescentes.
2. Uma futura campanha própria pode testar o parser corrigido contra Fretes, distinguindo shape real de contrato inválido por tipo JSON sanitizado. Não reutilizar o teto encerrado como retry.
3. Para aceitar V2-025d, executar os nove perfis sob alcance próprio e qualificar metadata, identidade, filtros, temporalidade e fingerprints; preservar G01 como gate operacional independente.

Condição de parada: erro/`429`, resposta inválida, limite de entidade não verificável ou teto. Condição de conclusão V2-025d não foi alcançada.
