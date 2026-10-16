# Checkpoint0443 — componentes testados, qualificação conjunta em correção

## Identificação e objetivo

- Data:05/10/2026. Estado: TESTADO_NA_CAMADA; agregado ainda não aprovado.
- Anterior: `0442-banco-teste-retido-aprovado-em-implementacao.md`, SHA256 `3BED8C73BEB5C53B17A54EEE7C91117F86DBB0BB56C176E2FBB8BC62ADCB0E7A`, preservado.
- Objetivo: preparar o shadow e extrair as onze entidades para testes com dados retidos. Primeiro marco atual: Coletas em ctl/stg, commit conhecido e readback independente após fechamento.
- Autoridade: pedido direto e continuação de05/10, AGENTS/STATES/ADR0060, `USER_20261005_E3_TEST_RETAINED`. Nenhuma nova pergunta genérica ou renovação de orçamento histórico.

## Limites e preservação

Alvo literal `localhost:1433/ETL_SISTEMA_V2_SHADOW`, sa existente externo, pin E3 manual imutável. Preservar SQL16.0.1000.6, contas, permissões, serviço/listeners/certificados/trust global e produção; BF/SAMPLE e Main originais intactos. Diagnóstico futuro:1conexão/até12SELECT/10s/60s. Captura em reserva distinta:per1/página1/até1000linhas/HTTP2, commit exclusivo do owner e segunda conexão de readback. Não houve despacho físico nesta unidade até o checkpoint.

## Alterações e evidência

Unidade `target/test-database-all-entities-20261005/`, inventário inicial e recusas anteriores preservados. Root mantém documentos/CI; Banco SQL/TLS/sessão; Runtime entrada/controlador/qualificação; Fontes transporte/contratos. A interrupção encerrou agentes antigos e foi reconciliada em `resume01.json`, sem processo da unidade observado ou efeito físico pendente reportado.

| Critério | Camada | Resultado observado | Evidência |
| --- | --- | --- | --- |
| Raster novo | Offline |34 testes PASS; Root37 hashes iguais | `fontes/raster-http/manifest01.json`, `supervisor-raster-review01.json` |
| Banco freeze01 | Offline |44 testes PASS; Root79 hashes iguais | `banco/implementation-manifest01.json`, `supervisor-bank-review01.json` |
| Diagnóstico JDBC preparado | Offline |74 causais+1JSON+37 checks;24tabelas/11módulos/12write tables;10SELECT planejados | `banco/implementation-handoff01.json` |
| Runtime integrado com JDBC fake | Offline |16 testes PASS;1000linhas/1066 operações simuladas | `runtime/runtime-directed02-receipt.json` |
| Controladores | Offline |18 checks composição/recibos,8 processo sintético,36 catalogal; nenhum Java/SQL/HTTP real | `runtime/controller-offline01.json`, `runtime/diagnostic-controller01/offline01.json` e unidade Runtime |
| CI | Revisão |4classes UNIT_INCLUDED, nenhum limiar/exclusão JaCoCo alterado | `coverage-scope-review01.json` |
| Primeiro agregado | Offline |FAIL no Spotless,9arquivos Banco; sem drift, SF0/FS0, antes da compilação | `runtime/aggregate01/receipt.json` |

O checkstyle do dirigido02 foi chamado sem a execução configurada do POM; a chamada correta passou em quality02. Esse erro local não autoriza reformatar legado. O agregado01 revelou diferença real entre a formatação externa usada por Banco e a sequência Spotless do POM. Corrigir somente os nove arquivos novos atingidos e emitir novo freeze, preservando freeze01 e FAIL. Nenhum teste físico, staging retido, aceite integrado ou checkbox novo.

## Demais entidades

FRE/MAN/COT/LOC/USU possuem staging normal, com footprints próprios; FRE/MAN incluem candidatos em recon. CAP/FAT/INV/SIN/RAS têm pais SQL synthetic-only, embora os modelos aceitem binding nulo. Não rotular dados reais como sintéticos. Busca no modelo105 não encontrou staging genérico de payload/presença; proposta de duas tabelas observacionais existe somente offline, sem DDL ou prova de ausência instalada. Referências: `banco/remaining-capture-footprints01.json` e `observation-storage-proposal01.json`.

Fontes prepara oito novas classes/testes de observação Data Export como unidade separada. O snapshot Coletas declara esses oito paths WIP fora de seu escopo; classes legadas do pacote e Raster final continuam incluídas. Isso não exclui cobertura nem qualifica a árvore corrente inteira. CAP dispõe de caminho INFO separado em preparo; página sem raiz verificável continua recusada.

## Próximas ações

1. Concluir formato Banco pelo POM, conferir freeze02 e qualificar agregado02 dos bytes finais, sem alterar recibos históricos.
2. Revisar pacote/controlador e despachar Banco para diagnóstico read-only com reserva e PRE/POST atuais; corrigir somente causas comprovadas.
3. Após contratos físicos/proteção, executar captura Coletas e readback independente; continuar as rotas reais das demais entidades conforme seus footprints.

Parada: qualquer identidade/contrato/limite inválido, não2xx/429, conflito ou resultado incerto; reconciliar antes de repetir. Sem bloqueio externo novo demonstrado para Coletas. Conclusão exige evidência física e dados retidos, não apenas os testes acima.
