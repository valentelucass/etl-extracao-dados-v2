# Checkpoint 0281 — smoke offline do JAR V2 — 22/09/2026 22:14 UTC

## Identificação e objetivo

- Anterior: `0280-v1-acesso-readonly-precondicao.md`, SHA-256 `4ed1f2e91f1b0933444d3f7e23048e141d951e82a5b627e0f5284c51b05e6a40`.
- Objetivo: avançar na qualificação e começar a testar o script V2 solicitado.
- Estado: `TESTADO_NA_CAMADA` de entrada/configuração offline; leitura real `BLOQUEADO_POR_INPUT`.
- Critérios: `STATES.md` V2-041/G01 e P17–P29; `BLOCOS_ETAPA_2.md` B17–B29.

## Autorização e limites

- Pedido efetivo: testar o script no que foi construído e rever pendências resolvidas; o comando e os itens exatos não foram especificados.
- Smoke escolhido: JAR já presente, JDK 17, configuração exemplo com fontes e auditoria desligadas; dois comandos sem `.env`, banco, rede ou escrita.
- Uso externo de credenciais permanece vedado pelo V2-041/G01; o runtime Java ESL requer autoridade separada da sonda `curl`.
- Orçamento: duas invocações locais, duas consumidas; nenhum ledger físico aplicável por ausência de efeito externo ou SQL.

## Alterações e decisões

- Working tree preexistente preservado. Apenas `STATES.md`, trilha, checkpoint e índice de retomada recebem esta evidência.
- O JAR não foi recompilado nesta unidade; a prova de equivalência de fontes permanece a já registrada no `STATES.md`.
- O atestado sanitizado não estava presente no caminho de intake. Alegações genéricas de pendências resolvidas não fecham checkboxes sem seus artefatos e critérios.

## Execução e evidência

| Passo | Camada | Comando/limite | Esperado | Observado | Evidência |
| --- | --- | --- | --- | --- | --- |
| Configuração | JAR offline | `config validate --config config/application.example.properties` | preflight sem efeitos | exit 0; `LOCAL_SHADOW`, fontes desligadas, `deny-all` | cabeçalho de `STATES.md` |
| Dry-run | JAR offline | `dry-run --config config/application.example.properties` | preflight sem efeitos | exit 0; mesmo estado seguro | cabeçalho de `STATES.md` |
| Intake G01 | filesystem, presença apenas | `target/v2-041/sanitized-attestation.json` | artefato para avaliar | ausente | cabeçalho de `STATES.md` |

- Efeitos desconhecidos: nenhum. Nenhuma extração, chamada ESL/GraphQL/Raster, SQL, deploy, cutover ou sonda 4924.
- Aceites fechados: nenhum novo; B16 interno e provas locais anteriores preservados.

## Retomada imediata

1. Receber de Segurança/Operações o atestado V2-041/G01 autenticado e sanitizado, sem segredo; validar estrutura e autenticidade antes de uso externo.
2. Receber os IDs das pendências alegadamente resolvidas e a evidência de cada critério; reavaliar a linha correspondente em `STATES.md`.
3. Para o teste real, confirmar script/canal, entidade/template, janela, teto e alvo de sombra; obter autoridade própria para JAR, ou usar somente a sonda `curl` allowlisted quando seus gates forem satisfeitos.

Condição de parada: gate G01 sem prova, escopo de execução indefinido ou contrato/terminalidade não verificáveis. O V1 requer identidade read-only específica antes de consulta.
