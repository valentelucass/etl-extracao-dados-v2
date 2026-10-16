# Checkpoint0442 — carga de testes retida aprovada, implementação em andamento

## Identificação e objetivo

- Data: 05/10/2026. Estado: implementação e qualificação offline em andamento.
- Anterior: `0441-primeira-extracao-java-api.md`, SHA256 `2EB03545B598EA017CF1DA948C966B7851964C27898BFF1B3317386C6EC3E0D7`, preservado.
- Objetivo do usuário: preparar o banco de testes e extrair todas as entidades para testar; a primeira amostra somente em memória não encerra esse objetivo.
- Critérios: STATES, AGENTS e ADR0060; unidade `target/test-database-all-entities-20261005/`.

## Autorização e limites

O usuário respondeu “pode, so n pode mudar versao sql server” à decisão concreta de confiança manual na folha E3A5…ACE276 para um novo perfil de dados retidos no shadow. Aprovação `USER_20261005_E3_TEST_RETAINED`, registrada em `authority01.json`. A pergunta anterior está respondida. Perfil `TEST_RETAINED_LOCAL_SQL_PIN_V1`, alvo literal `localhost:1433/ETL_SISTEMA_V2_SHADOW`, sa existente via provedor externo, exceção DNS local e procedência independente incompleta explicitadas. Pin BF/SAMPLE permanece imutável e rollback-only.

Preservar SQL Server16.0.1000.6, contas, permissões, serviço, listeners, certificado do servidor, trust global e produção. Sem causa física de schema ausente, nenhuma migration/Flyway é elegível. Cada efeito exige bytes qualificados e reserva nova; nenhum orçamento histórico reaberto. Não há nova pendência de aprovação genérica. Volume conservador comunicado: pequena amostra por entidade.

## Alterações e decisões

- Inventário inicial: `initial-state.json`; documentos/checkpoints anteriores preservados.
- Root: AGENTS/STATES, ADR0060, scanner com caminho/hash exatos do certificado público E3 e três contraprovas; nenhum segredo ou trust global adicionado.
- Banco: novo target/TLS/conector/sessão, responsável exclusivo SQL. Runtime: entrada/orquestração separadas e qualificação agregada. Fontes: cliente Raster HTTPS limitado, sem transformar fixtures em dados reais. Arquivos de produção ainda em edição; não qualificados por este checkpoint.
- Retenção inicial em `ctl`/`stg`, estado CANCELLED por limite de teste e resultado `CAPTURE_RETAINED_PARTIAL`; sem terminalidade falsa, DQ, promoção ou sucesso integral.
- Namespace exclusivo da primeira ocorrência: `TEST_RETAINED_SOURCE_20261005` / `TEST_RETAINED_SCOPE_20261005`; identidade técnica de teste vinculada à seleção única da origem, sem alegar identidade certificada pelo fornecedor. Ocupação inesperada exige reconciliação.

## Execução e evidência

| Critério | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Inventário integrado | Offline | 47 membros dos três inventários com hashes conferidos, zero divergências | `supervisor-inventory-review01.json` |
| Modelos SQL | Offline | Banco: 128 verificações; 105 migrations sem drift, 246 tabelas permanentes declaradas, 46 views | `banco/handoff.json` |
| Scanner E3 | Offline | 36 casos PASS, incluindo bytes alterados e E3 no caminho SAMPLE recusados | `target/macrobloco-qualificacao-pacote-20260913-01/test-retained-e3-20261005-scanner01/result.json` |
| SQL/HTTP desta unidade | Física | Zero conexões/chamadas; nenhum dado retido ainda | Inventários e autoridade, sem despacho físico |

Onze entradas catalogadas não equivalem a onze rotas reais prontas. Coletas possui a prova anterior em memória; expansão tem guards de transporte sintético e Raster precisava cliente HTTP. Schema instalado atual, proteção de writers e leitura pós-commit continuam não provados. Nenhum efeito incerto novo, PM2 alterado ou aceite físico/checkbox. Builders ativos somente nas unidades offline; históricos e recusas preservados.

## Próximas ações

1. Integrar os testes causais e bytes finais de Banco/Runtime/Fontes, revisar diff/CI e qualificar o agregado.
2. Despachar Banco para diagnóstico novo: uma conexão, até12 SELECTs, timeout10s/deadline60s, sem retry; verificar versão/hash PRE/POST e contrato físico necessário.
3. Após gates atuais, reservar captura Coletas com per1/página1/até1000 linhas/HTTP2, retenção e readback pós-commit; seguir para as demais entidades sem declarar prontidão por fixtures.

Parar a ocorrência em identidade/contrato/limite inválido, conflito, 429/não2xx ou efeito incerto; reconciliar antes de repetir. Conclusão exige evidência real de cada entidade e dados de teste inspecionáveis, não apenas build ou documentação.
