# Checkpoint 0280 — V1: teste de acesso e pré-condição — 22/09/2026

## Identificação e objetivo

- Anterior: `0279-sequenciamento-dados-reais-v1-candidato.md`, SHA-256 `f0bf6b0d0870aa6b800ad7499d6deaad2186f8f0602ce402a9cf70351ea69ea0`.
- Objetivo: verificar se dados reais do `ETL_SISTEMA` da V1 permitem fechar pendências da etapa 2.
- Estado: conectividade comprovada; leitura de dados não iniciada por falta de identidade read-only V1. Nenhum P/B fechado.
- Critérios: `STATES.md`, `BLOCOS_ETAPA_2.md` B17–B29; P30–P33 fora de escopo.

## Autorização e limites

- Pedido do usuário: analisar/testar `ETL_SISTEMA` da V1. Interpretação operacional: somente leitura local, máximo cinco chamadas seriais, timeout 5/10 s, sem dados de negócio na saída, sem escrita, DDL/DML, alteração de conta ou credencial, runtime V1, ESL ou Raster.
- Alvo: `localhost`, `master` para confirmar existência/acesso ao `ETL_SISTEMA`. Uma chamada de cinco usadas.
- Recuperação: não houve mutação ou efeito produtivo; nenhuma repetição necessária.

## Alterações e decisões

- Working tree preexistente preservado; somente documentação de estado, trilha, checkpoint e índice atualizados. Nenhum código, schema, segredo, ledger histórico ou checkbox alterado.
- A conta Windows atual é `sysadmin` no SQL Server. A checagem de papel interrompeu a rodada antes de qualquer objeto do V1, conforme limite registrado antes do efeito.
- `etl_v2_view` já tem negativa de acesso ao `ETL_SISTEMA` provada na trilha; não presumir que seja leitor V1.
- Hipótese pendente: dados do V1 têm o mesmo grão, janela, transformação e frescor que a ESL. Nenhuma comparação foi executada.

## Execução e evidência

| Passo | Camada | Limite/comando sanitizado | Esperado | Observado | Evidência |
| --- | --- | --- | --- | --- | --- |
| Checar alvo e papel | SQL local, metadados `master` | `sqlcmd -S localhost -d master -E -K ReadOnly -l 5 -t 10 -b`, consulta de três indicadores | banco existe, acesso e identidade segura | exit 0; indicadores 1/1/1: existe, há acesso, sessão `sysadmin` | cabeçalho de `STATES.md` |
| Ler objetos V1 | SQL de domínio | exigia identidade read-only | dados agregados seguros | não executado; pré-condição falhou | cabeçalho de `STATES.md` |

- Testes de código: não executados; nenhum código mudou.
- Efeitos desconhecidos: nenhum. 4924 não sondado. Nenhuma leitura ESL/Raster ou uso de `.env`.
- Aceites fechados: nenhum; camada financeira e paridade permanecem pendentes.

## Retomada imediata — até três ações

1. Owner SQL/V1: indicar identidade de leitura já autorizada para `ETL_SISTEMA`, com objetos, janela e limites, ou mecanismo equivalente que imponha leitura sem alterar grants/credenciais produtivas.
2. Com essa pré-condição, executar consultas agregadas delimitadas para identificar quais entidades têm dados comparáveis, sem expor linhas ou IDs.
3. Validar mapeamento de grão, tempo e divergências com owner de dados; só então avaliar se alguma evidência fecha P17/P20 ou outro gate aplicável.

Condição de parada: identidade sem restrição de leitura, objeto/janela não autorizados, erro SQL ou limite atingido. Conclusão: evidência real qualificada por entidade registrada em `STATES.md` antes de marcar bloco.
