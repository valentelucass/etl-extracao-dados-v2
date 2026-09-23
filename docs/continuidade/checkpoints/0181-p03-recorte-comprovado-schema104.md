# 0181 — Recorte P03 comprovado; schema104 local

## Objetivo e alcance

19/09/2026. ACEITO_NO_ESCOPO do pedido: corrigir sucessão MC e referência com fonte
preservada, qualificar/instalar migrations e provar campanhas/sequências locais.
Continuação de0180, sem fechar P03 inteiro, A–N, paisV2, paridade real ou produção.
39/45 e67/115 preservados. Relatório:
docs/catalogos/campanhas-integrais/P03-CORRECOES-20260919.md.

## Autorização e efeito

Pedido explícito autoriza migrations V2 novas/aditivas e provas sintéticas somente
em localhost/ETL_SISTEMA_V2_SHADOW. Master confirmou alvo ONLINE/read-write;
Windows existente, conexão explícita. V103/V104 qualificadas com rollback por
upgrade02/baseline02 e instaladas uma vez em p03-schema-install-01. Catálogo
efetivo1816objetos/definições igual ao qualificado,246tabelas e244contagens anteriores
preservadas. Prova do sufixo sobre102; nenhuma recriação de banco.

V001–V102 intactas. Sem novos grants/logins/credenciais/jobs, fonte real, V1,
produção, deploy, cutover, commit/push ou índice Git. Instalação confirmou somente
DDL; dados sintéticos de todas as provas foram revertidos. Pós-instalação exige
migration compensatória revisada se necessária; não editar/reaplicar V103/V104.

## Decisões e correções

ADR0052/SEQ-MC-01: declaração completa por origem incluída, após todos os lotes;
histórico preservado, nenhuma aposentadoria por ausência em captura parcial ou
por omissão de outra origem. Cardinalidade e conflitos contemporâneos mantidos.
ADR0052/SEQ-REF-01: revisão de tarifa independente, auditoria própria e snapshot
encadeado; fonte/frescor preservados, replay no-op e fonte stale sem revisar tarifa.
Consumers Java/baseline/validadores e metadados do pacote acompanham104; nenhum
pacote/JAR final foi executado. Fixtures e oráculos da campanha não mudaram.

## Evidência executada

Rodada: target/macrobloco-campanhas-integrais-20260915-01/.

| Prova | Resultado observado |
| --- | --- |
| p03-schema-upgrade-02 / baseline-02 | PASS, contraprovas MC, catálogos idênticos e rollback |
| p03-schema-install-01 | CONFIRMED; readback do schema104 |
| p03-directed-01 | 27unit PASS, preparação anterior |
| p03-campaign-sql-07 | 30unit/6IT PASS; A/B7etapas, referência3, recomposição;19m53s |
| p03-regression-sql-01 | 66IT:65PASS/1erro técnico da nova contraprova;7m40s;FAIL preservado |
| p03-relational-counterproof-02 | 41IT PASS;2m11s; correção apenas de evidenceId do teste |

Conjunto funcional distinto qualificado:30unit/72IT. Outros25IT de regression01
reutilizados por bytes iguais. Cinco fatos,19saídas e33previews nos pontos
contratuais. Contraprovas de substituição/omissão/conflito, histórico/replay/stale,
isolamento e falha Coletas→Fretes aprovadas. Todas as três tentativas físicas
OBSERVED, rollbackConfirmed=true e agregados antes/depois iguais; zero skips/timeouts.
Limites preservados:heap512MiB,etapa240s,sequência1800s, tentativas3600s ou600s na
repetição dirigida, SQL≤60s com limites menores preservados. Sem saldo reutilizado.

Falha intermediária: evidenceId sem prefixo synthetic- recusado no construtor,
antes de bind/resolve. Quatro nomes técnicos corrigidos; identidade de domínio,
relações, cardinalidade e expectativas intactas. Nenhuma nova alteração de produto.
Provas01 do schema preservam a comparação de FKs automáticas, corrigidas antes da
instalação. Diferenças de layout SQL nos readbacks foram reconciliadas por hashes,
sem repetir DDL ou esconder drift.

## Integridade e limitações

p03-corrections-20260919/: proof-summary.json, code-readback.json,
installed-readback.json, closing-checks.json, reviewed.diff,
sql-procedure-delta.diff e process-readback.json.695arquivos de runtime iguais às
três provas;11classes selecionadas ligadas ao build correspondente;102migrations,
157manifests, índice e oito exclusões preexistentes preservados. Nenhum processo
próprio ativo ou efeito desconhecido. Build/Enforcer/Spotless/Checkstyle e gates
estáticos pertinentes PASS; diff/UTF-8/sintaxe PowerShell/33passos/115checkboxes
com67marcados conferidos. Revisão do agente, não revisão humana.

Self-test scanner PASS. Scanner integral permanece FAIL somente pelos oito
MISSING_CANDIDATE anteriores; validator histórico FAIL por
STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md. Base histórica isolada PASS
reconferida. Nenhum manifest histórico regravado ou gate artificialmente aprovado.
Selagem/sucessão integral e regressão integral permanecem P06–P08.

## Próximas ações — outro macrobloco

1. Conferir o saldo dos critérios P03 sem reabrir este recorte já comprovado.
2. Admitir P04(supervisor/preview) somente após seus predecessores, Terra/High
   conforme trilha; Astra/High se aparecer nova decisão semântica.
3. Depois seguir P05–P08 na ordem, com autorizações/limites próprios. Nenhuma
   dessas etapas foi iniciada aqui; não gerar novo prompt sem solicitação.

Predecessor: docs/continuidade/checkpoints/0180-p03-regressao-e-contraprova-tecnica.md; SHA256 57443ff6b28a9e4d326574034d4e663f99dc25a518a4ef638ebd1e4d0d3b871f.
