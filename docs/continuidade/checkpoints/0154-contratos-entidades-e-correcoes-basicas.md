# 0154 — Contratos de entidades e correções básicas

- Data: 2026-09-14. Pedido efetivo: documentação alinhada à construção e às requisições API possíveis;
  corrigir defeitos básicos sem tratar a V1 como infalível. Sem perguntas ou subagentes.
- Predecessor: docs/continuidade/checkpoints/0153-integracao-funcional-local-concluida.md;
  SHA256 4944f268548890a9c5a3334d9391c9b7e473ce6616dbfd110b9b9a917238140b. A–N anterior continua concluído no seu selo.
- Estado: CONTRATOS_ENTIDADES_CORRECOES_LOCAIS_CONCLUIDAS na implementação/provas; validade da
  entrega depende do final-seal.json e seal-readback.json desta rodada. Não reutilizar o selo do JAR anterior para esta revisão.
- Alvo autorizado: localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado; V099 versionada e testes
  sintéticos com rollback. API/credenciais bloqueadas por V2-041; nenhuma leitura de segredo,
  rede de fornecedor, identidade nova, escrita produtiva, deploy, assinatura operacional ou cutover.
- Inventário inicial: target/correcao-basica-e-contratos-entidades-20260914-01/baseline.json; 3.308 arquivos, before/ preservado.
  Manifesto predecessor SHA256 ffc6701328e046abdaa35533a603a78103b8914917a0c50b85c1c6e0e1965e4d.
- Decisões: manter USERS_SNAPSHOT explícito; corrigir FRE-02/FRE-03 em core/recibo sem adulterar
  staging; remover só nove abstrações substituídas; índice de 2.437 IDs sem reclassificação.
- Provas: 2037 unitários/4 skips históricos; 458 IT/zero skips;
  16 casos SQL; readback de 3 módulos; JAR e catálogo conferidos. Detalhes e tentativas preservados
  em target/correcao-basica-e-contratos-entidades-20260914-01/verification.json, CHECKPOINT-02.md e logs das tentativas.
- Medida: construção 39/45; aceites históricos 67/115. Zero aceite real novo. V1 não executada;
  snapshot local e fonte real não são equivalentes. Classes mantidas restantes exigem análise própria.
- Sem efeito desconhecido no SQL: instalação V099 e rollback observados. Consultar process.json/
  result.json antes de repetir qualquer tentativa. Esta revisão não reabre nem renova campanhas antigas.

## Retomada imediata

1. Conferir manifesto/sucessão, checks e hashes de final-seal.json + seal-readback.json em target/correcao-basica-e-contratos-entidades-20260914-01/.
2. Se o selo faltar, concluir apenas diff/pacote/checks locais desta revisão, sem repetir as provas já confirmadas.
3. Com selo íntegro, manter V2-041 e dependências nominais G01–G08; seguir o roteiro API somente
   quando a evidência de desbloqueio e o escopo aplicável existirem. Não recriar chave/regra por analogia com V1.
