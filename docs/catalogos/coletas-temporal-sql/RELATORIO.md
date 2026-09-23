# B63 — fechamento técnico e integração temporal SQL

**B63_TEMPORAL_TECHNICAL_SCOPE_COMPLETE**,10/09/2026. Frentes A–D do prompt
adotado concluídas; o restante da ligação temporal foi implementado e testado
como integração observacional injetável. Não há equivalência de fonte ou
aceite representativo presumidos. Matriz precisa: [ACEITES.md](ACEITES.md).

## O que foi entregue

- Mapa e correção temporal originais permanecem no catálogo bloco63-temporal-local;
  ADR0044/0045 preservadas e ADR0046 fundamenta este delta.
- Captura existente ligada a ColetaTemporalReferenceStore/JDBC. Staging de uma
  observação, selo de travessia completa local, binding explícito e qualificação
  agregada; nenhuma massa ou cruzamento transferidos para a JVM.
- Proposta SQL com três tabelas, cinco procedimentos e uma view. Identidade
  tipada/escopada, proveniência, presença, bruto, epochSecond/nano e observação
  técnica separados. Duplicatas conflitantes, fonte/tenant incorretos, captura
  parcial, status/janela divergentes e timestamps inválidos recusados.
- Trava transacional por execução e retry idempotente. O selo impede entrada
  tardia; a contagem inclui quarentena sem sidecar. Expansão6908 mantém linhas
  físicas sem inventar correspondências de identidade.

O SQL está fora de migrations/baseline. Cada caso físico criou somente os
objetos propostos dentro de sua transação e os reverteu. A autorização aplicada
foi o pedido de concluir o restante e a autonomia de sombra local do AGENTS,
limitada a localhost/ETL_SISTEMA_V2_SHADOW. Sem reset, DDL permanente, grants,
UAC, .env, credencial, API, nova campanha B60/B62, operação ou produção.

## Evidência executada

| Camada | Resultado |
| --- | --- |
| Predecessor antes das edições | Test-ColetasTemporalLink com evidência privada e9 guardas passou |
| Dirigido inicial | 60 testes/0 falhas/0 erros/0 skips;22 novos nessa revisão |
| Verify offline final | 1556 testes/0 falhas/0 erros/4 skips históricos;26 novos ao todo |
| Toolchain e gates | Java17,Maven3.9.14,512MiB;Enforcer,Spotless,Checkstyle,compilação,arquitetura,JaCoCo passaram |
| Cobertura final | 91,78%linhas;76,73%branches;775 fontes iguais ao build |
| SQL final physical-03 | 34 casos passaram;rollback e ausência dos objetos/escopo próprios após cada caso |
| Exclusão entre sessões | Segunda sessão recusada enquanto a trava do staging está ativa; admitida após rollback |

Falha preservada physical-01: classe de caracteres T-SQL recusava underscore
e hífen válidos; diagnóstico sintético isolado confirmou o motivo. Corrigido
posicionamento do hífen, sem afrouxar a regra Java. physical-02 passou33 casos.
physical-03 acrescentou a trava entre sessões e expectativas completas de
epoch/nano. Nenhum resultado desconhecido e nenhum retry de chamada de fonte.
O primeiro validador documental teve erro de sintaxe na continuação de
condições PowerShell; tentativa/log preservados e forma corrigida. A revisão
seguinte passou com12 contraprovas,sem alteração Java ou repetição SQL.
As quatro contraprovas Java adicionais recusam JSON/texto incompatíveis antes
de abrir conexão. O verify final inclui essas contraprovas e todos os anteriores.

A prova JDBC usa proxies verificáveis de binding/transação/fechamento; o SQL
físico usa SqlClient com fixtures sintéticas. Não alegar um teste Java/JDBC
físico ponta a ponta. A prova concorrente exercita a trava real adquirida pelo
staging, não uma corrida completa de promoção entre duas execuções de negócio.
Não há nova qualificação de conectores reais nesta fase.

## Limites e decisão de fechamento

O resultado SQL é candidato observacional com promotion_authorized=0.
NATIVE_PRECISION_UNVERIFIED recusa equivalência de representações nativas
distintas quando a coluna antiga só preserva milissegundos. Offset equivalente
não é declarado conflito de evento. A origem nativa só é conservada com texto
bruto exatamente igual; nenhum arredondamento escolhe vencedor.

Payload/presença/fallback6908, core.coleta, V004/V010/erro51428, terminalidade,
contratos/bindings antigos, migrations/baseline e as provas reais anteriores
permanecem iguais. Não ligamos esta projeção aos promotores antigos nem ao Main.
Isso exige os bindings/aceites da ativação futura, fora do B63 local.

As cinco chamadas reais da prova anterior continuam encerradas5/5: timestamp
ausente6908 confirmado, sem transição real observada. Esses resultados e os
sete pares SQL antigos são históricos preservados. COL-TIME-01/Q-COL-01/
V2-012a/b/c/V2-041 não receberam novo aceite.67/115,191 rotas,zero AGORA.
A ausência do oráculo real e de aceite nominal não deixa A–D incompletas.

## Revisão e continuidade

Inventário inicial2433; quatro arquivos existentes alterados apenas para
continuidade e sucessão do validador, com snapshots públicos e before privado.
Nenhum código anterior foi editado. Os arquivos novos e hashes estão no manifesto.
As evidências e os dois checkpoints0066 concorrentes anteriores foram preservados.
RETOMADA foi condensada com cópia integral imutável do índice anterior.

Diff completo e diff de implementação em target/coletas-temporal-sql-20260910/;
checks.json e receipt.json registram as validações finais, incluindo scanner,
continuidade, trilha, guardas negativos e revisão do diff. Não presumir PASS
somente pela existência deste relatório. Cadeia0067→0068→0069.
Rollback do código pelos deltas/snapshots, preservando todas as evidências.
Nenhum commit ou push. A–D e o complemento local encerrados; próximos passos
dependem exclusivamente da evidência/aceites da ativação, descritos em ACEITES.md.
