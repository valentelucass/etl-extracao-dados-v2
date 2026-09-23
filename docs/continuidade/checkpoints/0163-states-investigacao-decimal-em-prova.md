# 0163 — Investigação STATES: decimais em prova

Data: 2026-09-15T17:34:41Z. Estado: EM_EXECUCAO.
Predecessor: [0162](0162-cadeia-integral-concluida.md), SHA256
`2c38ffc74ab401981b521963cbb734c6ba9bcbe03d2c73f71f3b237ae3027b37`.

## Objetivo, autorização e limites

O usuário adotou integralmente `target/preparacao-execucao-states-20260915-01/PROMPT-EXECUCAO-STATES.md`:
investigar, testar, implementar e corrigir A–N a partir dos critérios do STATES.
A condição de admissão nominal anterior foi expressamente revogada nesta execução.
Uma entrada e uma entrega final; sem perguntas, subagentes ou pedido de continuação.

Somente arquivos sintéticos e SQL localhost/ETL_SISTEMA_V2_SHADOW com autenticação
Windows, duas travas Maven, transação externa e rollback. Sem segredo/.env, API
autenticada, V1 executada, produção, dashboards, serviços, agenda, grants, deploy,
cutover, commit/push ou alteração do índice Git real. Schema102; nenhuma migration
reaplicada. Construção39/45 e aceites históricos67/115 preservados.

## Investigação e alterações

Base conferida em `target/execucao-states-20260915-01/baseline-verification.json`:
3465 snapshots byte-idênticos,27 pins,2829 membros ZIP,7 contraprovas de sucessão;
índice Git preservado. Base/selo históricos permanecem evidência da revisão0162.

Três divergências concretas contra presença/precisão dos contratos locais:

1. Raster convertia número JSON em Double antes da validação: recusava decimais
   válidos e aceitava excesso de precisão após arredondamento. RED8/5falhas.
2. ExpansionFieldParser rejeitava números exatos pequenos e zero de escala8 ao
   confrontar a notação interna com regex textual. RED8/3falhas.
3. QualificationJson removia zeros finais; preflight e consumo divergiam em escala
   e disposição. RED11/3falhas, inclusive1.000000000 que não cabe na escala declarada.

Correções usam decimal exato, conservam escala e verificam limites antes de converter.
Texto continua com gramática restrita. Nenhuma regra fiscal ou identidade nova.
Arquivos: RasterResponseParser, RasterFieldParser, ExpansionFieldParser e QualificationJson.
Testes novos: StatesRasterDecimalTest, StatesExpansionDecimalTest e
IntegralDecimalPropagationIT; autores sintéticos integrais receberam variante explícita.

## Evidência e processos

Rodada privada: `target/execucao-states-20260915-01/WORKLOG.md`.
As tentativas `raster-red-01`, `expansion-red-01`, `preflight-red-01` encerraram exit1
com as falhas esperadas; não são falhas de ambiente. `decimal-green-01` passou60
testes/0falhas/0erros/0skips em05:21, antes da correção adicional do preflight.
Uma invocação PowerShell sem aspas na lista de testes falhou antes de criar processo;
comando corrigido, sem efeito SQL nem resultado desconhecido.

`decimal-sql-01` está em execução: PID33560 iniciado2026-09-15T17:32:16Z,
budget1500s/heap512MiB/log16MiB; master e agregados iniciais conferidos. Consultar
process.json, logs e result.json antes de repetir. Prova A2/B24,11famílias,5fatos,
19SQL,33previews, campos decimais alternativos e oráculo deliberadamente errado
escrito antes de SQL. Resultado físico e rollback final ainda pendentes.

Revisão lexical atual:646classes de produção,489de teste,46candidatos históricos;
9remoções e3movimentos anteriores preservados,0classes de produção novas.
Não equivale ainda à segunda revisão de bytecode/pacote.

## Retomada — até três ações

1. Observar `decimal-sql-01`, reconciliar rollback e corrigir eventual falha local.
2. Concluir investigação dos consumidores A–K e gates completos da revisão atual;
   conferir multiplicidades, quatro skips históricos e16regressõesV099.
3. Qualificar JAR extraído com entradas/oráculos alternativos, aplicar diff em cópia,
   revisar e fechar selo/readback, sucessão explícita e matrizA–N/45.

Parcelas externas G01–G08 não foram recebidas nem aceitas. Não interrompem trabalho
local independente. Esta unidade não é entrega final nem nova qualificação nominal.
