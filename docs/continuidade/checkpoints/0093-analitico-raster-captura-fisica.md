# 0093 — Raster tipado e primeira captura física verificada

EM_EXECUCAO. Prosseguir no pedido A–N até a entrega final, sem perguntas.
Request, autorização e inventário2704: target/macrobloco-analitico-20260912-01/.
Predecessor: checkpoint0092. Não alterar migrations instaladas V001–V054.

Implementado: modelos Raster Trip/Stop/Route com 51 declarações classificadas;
presença, wire/raw, nanos/offset/sentinela; parser de envelopes e aliases;
binding lateral; lote JDBC16; staging tipado; corrente/histórico; aplicação
SQL pai antes do filho, FK, dedupe/replay, atualização parcial e disposições.
HTTP loopback com credencial fictícia, disabled/fail-fast, bytes/deadline e
ausência de redirect/retry foi escrito e está em verificação dirigida.

V052/V053 instaladas: schema-raster-install-01/ledger.jsonl, CONFIRMED,
179→188 tabelas, contagens preexistentes preservadas. V054 SQL-13/Transit Time
instalada em schema-raster-projection-install-01; ainda sem prova de consumo.
Baseline inclui V001–V054. SQLCMD qualificação com rollback precedeu instalações.

Provas executadas:
- directed-raster-01: 18 unitários, zero falhas/erros/skips, Java17.
- physical-raster-02: 4 IT reais JDBC, zero falhas/erros/skips; antes/depois iguais.
  Duplicação, pai/filho, replay, nanos/sentinela, parcial/NULL, binding ausente,
  inválido e falha de filho foram exercitados com rollback.
- Tentativas de compilação e physical-raster-01 falharam em estilo; preservadas.

Inventário dos 19 SQL legados: 673 colunas, ordem/expressão/source hash em
docs/catalogos/macrobloco-analitico/contratos-colunas-inicial.json. A soma778
informada no progresso foi corrigida para673. ADR0050 e PESQUISA.md são locais;
não ratificam identidades nem consumidor. Construção permanece32/45, semaceite.

Próximas ações:
1. Reconciliar processo directed-raster-http-01 e fechar provas Raster/SQL-13.
2. Implementar referências/seis dimensões, MAT01/02/05 e demais18SQL, com pipelines existentes.
3. Integrar cenário11entradas/5fatos/19consultas, ausênciaK, recomposição e provasL–N.

Pendências locais não são externas: testes de cap, todas as51declarações,
concorrência, escala, SQL13 consumidor, demais frentes e fechamento ainda faltam.
Não declarar construção concluída. A sucessão final deve estender exatamente
CheckGuidance e a expansão com snapshots; validadores históricos não foram
afrouxados e os arquivos anteriores permanecem preservados em before/.
