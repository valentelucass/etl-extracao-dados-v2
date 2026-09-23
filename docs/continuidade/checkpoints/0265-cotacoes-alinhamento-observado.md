# Checkpoint 0265 — Cotações: alinhamento técnico observado

Data: 22/09/2026. Sucede o checkpoint 0264.

## Unidade concluída

Foi concluída a segunda linha de B16 para Cotações: comparar o contrato
observado com o código existente e corrigir somente divergência demonstrada. A
ordem read-only registrada previamente consultou 6906 por `/info` e uma página
`GET_WITH_QUERY`, com duas chamadas HTTP 200, `per=100`, limite de 10 MiB,
timeout de 30 segundos, intervalo de três segundos e trava interprocessos V2.

O perfil sanitizado dos nove campos usados por
`CotacaoDataExportRecordMapper` mostrou `sequence_code` numérico; datas como
texto ou nulas onde aceitas; total, nomes e códigos como texto. Não houve
incompatibilidade de presença/tipo demonstrada, portanto não houve correção de
código a aplicar. As respostas não foram persistidas nem expostas.

`CotacaoDataExportRecordMapperTest` passou sob JDK 17: 10 testes, zero falhas,
zero erros e zero skips. A primeira tentativa com JDK 25 parou corretamente no
enforcer antes de executar testes; a correção foi restringir `JAVA_HOME` ao
processo de teste, sem mudar o ambiente global.

## Limites preservados

O resultado é alinhamento técnico de uma página observada. Não é contrato ou
release oficial, estabilidade, paginação terminal, completude, referência
tarifária, oráculo, paridade, aprovação nominal ou produção. A primeira linha
de B16 permanece aberta; nenhuma atividade de P17–P29 foi antecipada.

`STATES.md`, trilha, recibo sanitizado e `BLOCOS_ETAPA_2.md` foram atualizados;
a segunda linha de B16 foi marcada. `git diff --check` passou, com avisos
CRLF/LF preexistentes. Não houve banco, DDL/DML, escrita, deploy, corte,
GraphQL ou consulta a 4924.

## Próximo ataque maior

O próximo bloco potencial é a caracterização ampliada de Cotações: paginação
finita até terminal dentro de ordem própria e comparação da forma observada
contra o catálogo. Ela pode ampliar a evidência técnica, mas não fecha a primeira
linha B16 nem P17 sem referência tarifária e oráculo independentes.
