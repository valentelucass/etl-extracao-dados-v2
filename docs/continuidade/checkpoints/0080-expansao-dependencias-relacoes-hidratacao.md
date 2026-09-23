# Checkpoint0080 — dependências, relações e hidratação verificadas

EM_EXECUCAO. A–N permanece uma tarefa única; continuidade/revisão pedidas pelo usuário.
Predecessor0079 SHA256 59d7c4c84e73c830a8d63afd74f8093f0df08b16bf8d2b50258dee0bcc750ca0.
Pedido, inventário2549 e snapshots imutáveis: target/macrobloco-expansao-20260912-01/.

Quatro pipelines novos seguem integrados. Dependências Fretes/Localização usam
parsers/guards/casos de uso/staging anteriores. Captura completa, aplicação SQL,
precisão epoch/nano suplementar, current/history e replay. Nenhuma promoção real.
Relações explícitas FAT/documento/Frete,INV/Frete,SIN/Frete,LOC/Frete por TVP;
raízes/partes/componentes tipados, revisões e cardinalidade; CAP sem elo artificial.
Fila SQL com claim limitado/lease/expiração/retentativas/disposições e hidratação
mínima pelo pipeline. ADR0049 EXP11–13 registra os contratos.

V040/V041 instaladas fora das IT, após master/Windows e qualificação rollback:
database/migrations/V040__capture_expansion_dependencies.sql SHA256 4dbc70fade3468ef28a3e6d96e6bc59701452081674945c7cc67068e9fc6635f
database/migrations/V041__resolve_expansion_relations.sql SHA256 e1a731746c7c13990dc91e4dd152abd6427154f7e3a8f10fde8857bb4b6ed74f.
Imutáveis; próxima livre V042. Baseline atualizado.

compile-dependencies-01 passou. physical-dependencies-02:6/0/0/0.
physical-relations-01:3/0/0/0, cenário com quatro verticais+duas dependências,
14 vínculos,7 inicialmente ausentes,1 alvo na fila,1 observação hidratada,
14 resolvidos sem multiplicação das8 raízes. Revisão/conflito/orfandade e
EMPTY/TEMPORARY/lease expirado/teto3 executados. Agregados before/after iguais.
Falhas preservadas: schema-dependencies-qualify-01(payload na tabela errada),
physical-dependencies-01(nomes no control plane),schema-relations-qualify-01
(collation CONCAT). Correções dirigidas passaram, sem alterar migration instalada.

Ainda faltam referências consumidas, MAT04/MAT03, seis consultas, recomposição/
runner; ampliar adversariais e concorrência efetiva/escala/planos/JAR/verify,
scanners/validadores/diff/manifesto/quadro45 antes-depois/entrega N.
Construção local verificada parcial, aceite real pendente. Nenhum B64/aceite novo.
Somente localhost/ETL_SISTEMA_V2_SHADOW/Windows;DML rollback-only;DDL fora de IT.
Sem API/.env/segredos/grants/reset/V1/dashboard/produção/serviço/commit/push.
Processos próprios ativos:nenhum ao fechar este checkpoint.

Próximas ações sem confirmação:
1. Releases V2-035a consumidas, labels/calendário/filial-pagador e provas H.
2. SQL MAT04/MAT03, seis consultas e recomposição/runner integrado.
3. Completar M/N, revisar diffs e entregar todos os requisitos A–N.
