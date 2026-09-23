# Checkpoint0204 — P06 revisado; P07/P08 delimitados — 20/09/2026

Anterior0203:c796b4322007ea5ff60f8a67b1ee2d95c96bdf49da7f50127f7358f94182468e.
Pedido: revisão P06 integral, sem subagentes, efeitos SQL/JDBC ou fontes.
Rodada: target/P06-REVISAO-POS0202-20260920T225525774Z/; request.txt/baseline.json
identificam autorização e revisão. Consumo físico zero, nenhuma reserva nova.
POS0198 permanece fechado2/3 e7200/10800s; saldo não usado nem renovado.

Revisão técnica concluída e correções TESTADAS_NA_CAMADA_OFFLINE. P06-01 média:
causa rollback preservada com falha close suprimida. P06-02 média: scanner
reconhece somente remoções do manifesto fixo e snapshots íntegros. P06-03 média:
sucessor novo resolve deriva documental sem modificar manifests antigos.
P06-04 baixa: helpers versionados permitem a rodada do pacote via RoundName
validado e recusam traversal antes do corpo; execução física não realizada.

Evidência executada: red-close4testes/1falha; green-close68PASS; preflight
ArtifactDirected18PASS dentro240s; JAR/explodido e14oráculos/adulterações reais;
scanner17contraprovas, remoções5, helpers2recusas+parser. Sucessor11contraprovas
PASS; preparação PASS; primeira trilha completa PASS67marcos/191fatias/67de115.
Os validadores finais após este checkpoint/ponteiro são registrados em
final-validation e closure.json; não confundir resultado preliminar com readback
dos bytes finais. Falhas de harness preservadas em WORKLOG e logs originais.

Predecessores: manifests/recibos/XMLs/JAR/12planos/agregados conferidos;2077
inputs P05;13pins P03 (11testes+2migrations) iguais. Modificação na sessão muda
JAR e exige nova prova física dos consumidores; os aceites B–K permanecem
históricos, com reviewP06 explícito na matriz. A/L/M/N abertos;39/45 e67/115.
L inclui regressão ainda não executada; não há seloN, revisão humana, paridade
real ou prontidão produtiva. Sem DDL, dependência, credencial ou índice alterado.

Relatório e gates: docs/catalogos/campanhas-integrais/P06-REVISAO-POS0202.md.
Sucessão: docs/catalogos/p06-revisao/; snapshot original3505, delta explícito e
inventário fechado. Recuperação por own-diff.patch/before, com revisão dos hunks
próprios e requalificação; sem reset/restauração automática de exclusões.

Próximas ações, após conferir fechamento final:
1. Conferir closure.json, final-validation, diff e manifests P06/POS0198;
   qualquer drift mantém gate bloqueado até reconciliação local.
2. Obter ordem P07/P08 com alvo, vigência, reservas/tetos por cenário e cumulativo;
   nenhum efeito físico está autorizado por P06. Pacote concreto no relatório.
3. Sob essa ordem, verify físico/requalificação primeiro; depois JAR extraído,
   supervisor A/B/recusas e integridade final. Não antecipar aceite L/M/N.

Condição de saída P06: leitura final/hash/UTF8/JSON/diff, scanner/trilha/preparação
da revisão final conferidos e zero processos próprios; evidências privadas
conservam resultados observados, sem substituir lacunas por planejamento.
