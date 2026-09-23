# Checkpoint0081 — referências, consultas e revisão de selagem

EM_EXECUCAO no pedido único A–N. Predecessor0080 SHA256 35b6d62266628883fe6bdf482236410d239f11fc6b50bd3d34a2a863cd3393ee.
Request e inventário2549/snapshots:target/macrobloco-expansao-20260912-01/. Universo funcional45 congelado.

V042 estende V2-035a somente com labels sintéticos; calendário/filial/pagador
reutilizam registro, conteúdo e selagem existentes. Seleção por run/revisão/
vigência, sem default operacional. Origem do pagador é token literal sintético.
V043 fornece quatro projeções SQL; JdbcExpansionQueries devolve quatro modelos
tipados ExpansionProjection, paginação por componente corrente1–100 e linhagem.
CAP labels reais consumidos e FAT placeholders/CTE-NFSE/CNPJ/frescor preservados.
INV/SIN preservam candidatos, valores/pesos/volumes e tempos tipados.

Revisão encontrou enum de quarentena incompatível na view:V044 corrige prefixo
QUARANTINE_* e conserva conflito pendente na projeção. Também fixa seis contratos
do run e verifica fonte/tenant/protocolo/contrato/modo/replay/janela/páginas
durante a selagem. Finish da fila aceita replay exato já concluído.
ADRs EXP14–16. Migrations novas imutáveis; próxima V045:
database/migrations/V042__consume_expansion_references.sql SHA256 b774cc4b50a8806200f5e98eba453132821061c6047fdd5485959a3a5d4fa018
database/migrations/V043__project_expansion_financial_inputs.sql SHA256 b6233fbd7895a67e61cbb3f1d3dd0feccebb60e37385e3940cbaefe2f55e5d38
database/migrations/V044__fence_expansion_captures_and_conflicts.sql SHA256 89595bece46908a29cb87844bd7597097df4466ec7b80c17e1a55a6225193bc3.

Provas:physical-references-02:4/0/0/0. physical-captures-projections-01:33/0/0/0
(12 capturas,6 dependências,3 relações,4 referências,8 consultas),Java/JDBC/SQL.
physical-capture-fences-02:16/0/0/0,dados negativos de fonte/tenant/contrato/página
nas4 verticais; mutação-alvo confirmada antes de cada recusa, nenhum selo/core.
Agregados before/after iguais. Formatter/checkstyle passaram nesses builds.
Falhas preservadas:physical-references-01 collation do join de teste;
physical-projections-01 quarentena não sinalizada;schema-capture-fences-qualify-01
collation entre contratos;physical-capture-fences-01 linha de teste142 caracteres.
Correções aditivas/dirigidas verificadas; nenhuma migration aplicada editada.

MAT04/MAT03 ainda SEM implementação. Restam suas duas consultas, recomposição/
executor/JAR, adversariais adicionais, concorrência efetiva, escalas/planos,
verify/scanners/validadores/diff/manifesto/quadro45 antes-depois/entrega N.
Não alegar A–N completas nem aceite real/B64. Próximas ações:
1. MAT04 sobre pub.ufn_expansion_fat e vínculos/ref selados; oráculos manuais.
2. MAT03 sobre Frete/Localização/FAT, contrato sintético explícito dos inputs
financeiros que o Frete operacional ainda não fornece;6 consultas/recomposição/JAR.
3. Completar M/N e entrega final no mesmo pedido, sem pedir continue.

Limites:localhost/ETL_SISTEMA_V2_SHADOW/Windows;DDL aditivo fora das IT;
DML rollback-only. Sem API/.env/segredos/grants/reset/V1/dashboard/produção/
serviço/commit/push. Nenhum processo próprio ativo neste checkpoint.
