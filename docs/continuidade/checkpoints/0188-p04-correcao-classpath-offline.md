# 0188 — P04: diagnóstico delimitado e correção offline

## Identificação e objetivo

- Data UTC: 2026-09-20T02:49:47Z; continuação do pedido de resolver o bloqueio.
- Anterior: `0187-p04-requalificacao-fisica-bloqueada.md`, SHA-256
  `c5bc98a551048e5d3fba2c6515902e7ecfaa22cdb2f10f2b79180a46e06fb9a4`.
- Estado: TESTADO_NA_CAMADA offline; I/J IMPLEMENTADO_NAO_QUALIFICADO.
- Critérios: pedido P04-REQUALIFICACAO-20260919-01, SEQ-01/03/CP-01.

## Autorização e limites

- Usuário: “resolva esse bloqueio lendo as documentacoes”; continua a ordem
  original de até duas tentativas físicas, segunda após correção causal offline.
- Uma tentativa consumida; resta no máximo uma de3600 s, total7200 s sem renovação.
- Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW, integrado, sintético, rollback-only,
  commit de domínio bloqueado e duas travas Maven; heap512, sequência1800,
  etapa240 e SQL60 s. Sem P05/K, P06–P08, DDL, migration, fonte, segredo ou produção.
- Ledger histórico preservado: `target/macrobloco-p04-requalificacao-20260919-01/ledger.json`,
  SHA-256 `1ea5f271825d9872c1d8d1afef065f11269f6551e56251cb340e10de0ee910dd`.
- Não é necessária nova autorização para a segunda tentativa explicitamente
  condicional; o adendo conserva a primeira consumida e não reabre o ledger antigo.

## Alterações e decisões

- Inventário anterior: `target/p04-desbloqueio-0188/baseline.json`, 3566 arquivos,
  2990 entradas de status; cópias `before/`; índice preservado.
- `QualifiedPackage.java`: aceita apenas classpath único ou expandido exato selado.
- `QualifiedPackageClasspathTest.java`: quatro testes positivos/negativos.
- STATES, trilha e CONTRATO: retificação e SEQ-CP-01, sem aceite agregado.
- Leitura rasa de quatro stderr bootstrap e um da sequência preservados revelou
  QUAL_PACKAGE_RUNTIME_CLASSPATH. O verificador antigo recusava o classpath do
  próprio supervisor. Não foi necessário diagnóstico recursivo de fixture.
- Rejeitado: interpretar240 s de classe/ausência de recibo como timeout de etapa.
  Não comprovado na evidência anterior. Primeira falha e reserva permanecem.

## Execução e evidência

| Passo | Camada | Comando/limites | Esperado | Observado | Evidência |
| --- | --- | --- | --- | --- | --- |
| Preflight | Offline JDK17 | Invoke-Build Directed, quatro classes,900 s,512 MiB | exit0 |16/16 PASS; Enforcer/Spotless/Checkstyle verdes | `target/macrobloco-campanhas-integrais-20260915-01/p04-fix0188-offline/result.json` |
| Processos | SO read-only | nomes/caminhos das duas campanhas anteriores | ausentes | nenhum próprio restante | consulta da rodada |

- Efeito físico novo: nenhum nesta unidade. Não há execução com retorno perdido.
- Aceites fechados: nenhum. Nenhum schema/dependência/manifesto histórico alterado.
- Recuperação: delta cirúrgico contra before, sem descartar alterações anteriores.

## Retomada imediata — até três ações

1. Conferir mapa e alvo master, reservar segunda tentativa no adendo; resultado
   esperado é reserva única que inclui o consumo anterior, não orçamento novo.
2. Executar Physical pelo controlador existente com cinco ITs e quatro unidades;
   observar worker, recibos, rollback e limites reais, sem terceira tentativa.
3. Sincronizar resultados STATES/trilha, verificadores, checkpoint e RETOMADA.

Bloqueio externo: nenhum para a segunda tentativa condicional. Parar em falha
real/limite/rollback incerto; encerrar somente árvore comprovadamente própria.
Conclusão requer todos os critérios P04 e evidência física, não apenas preflight.
