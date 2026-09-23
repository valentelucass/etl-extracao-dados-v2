# Checkpoint 0144 — fechamento dos bytes e verificação final

AUDITORIA_E_CORRECOES_LOCAIS_CONCLUIDAS no escopo local A–N, válida somente com final-seal.json íntegro. UTC 2026-09-14T02:58:18.300Z.
Anterior: docs/continuidade/checkpoints/0143-entrega-auditoria-e-correcao-de-construcao.md; SHA256 7b0e618640e066aaefd257cd748ee90bc69739e20be1dd64dc73ea99d383f257. O checkpoint 0143 registrou a candidata anterior; seus bytes de resumo/manifesto/matriz anteriores estão preservados em target/macrobloco-fechamento-construcao-20260913-01/final-revision-01/.

A matriz de 6.756.614 bytes foi recusada pelo scanner e dividida em três partes abaixo de 5 MiB, sem retirar campo, modificar o scanner ou reclassificar progresso. O Git da prova de diff recebeu core.longpaths=true por comando para ler as cópias longas do Windows. Tentativas e erros anteriores preservados.

Duas correções funcionais no supervisor mantidas; 1.976 unitários/4 skips históricos, 423 IT incluindo 417 identidades anteriores + 6 novas, cobertura e rollback aprovados. Dois pacotes byte-idênticos, 5 smokes/30 comandos, 4 casos de retomada/12 comandos, 21/8 recusas extraídas e 25 guards de envelope. As correções documentais não alteram esses inputs Java, JAR ou ZIP.

Construção 39/45 antes/depois; aceites históricos 67/115. G01–G08 e partes operacionais condicionadas permanecem abertos. PLANO_PRONTO_ENSAIO_NAO_EXECUTADO; nenhuma autorização operacional/nominal acrescentada.
Resumo: docs/catalogos/macrobloco-fechamento-construcao/verification-summary.json; SHA256 b4a8a33ccac4b1f3a1520dafc01737989d92951e4d4289dbd421cf73cbb478a3. Relatório/matrizes/plano no mesmo catálogo. Rodada: target/macrobloco-fechamento-construcao-20260913-01/.

Sem selo: (1) gerar manifesto e diff aplicável exatos; (2) concluir foundation-02, runtime-02, succession-02 e closure-02, corrigindo toda falha local; (3) revisar todos os pins/bytes e selar. Com selo íntegro, entrega local encerrada. Não repetir autores one-shot, efeitos desconhecidos ou etapas SQL já concluídas.
