# 0398 — P08: método 2 reclassificado somente na evidência local

- Data: 30/09/2026. Anterior: [0397](0397-p08-metodo2-delta-stats-classificado-offline.md), SHA `ACFCB56423E4452AA4E401FEC70C914F41AD448530A191BD30D0B1BF46D1F829`.
- Decisão explícita do Supervisor nesta unidade: adotar apenas para evidência local o predicado restrito 0397 e reclassificar o resultado físico preservado de 0396 como `PASS_METHOD2_LOCAL_OBSERVED_WITH_AUTO_STATS_DELTA` por revisão aditiva. Não alterar o STOP, recibo, ledger, XML ou outputs originais; não promover 2538 a baseline nem P08 agregado.

## Aplicação aos arquivos imutáveis

`target/p08-method2-reclass-20260930-01/reclassify-offline.ps1`, SHA `857EF64B210C6602657DB6768BBB61AB9B1A1D3C45DF58F1F1C411C222BB80C2`, validou SHA dos recibos PRE/físico/final, ledger 0396, diagnóstico original e correção aditiva, além do classificador completo 0397. Verificou guard 55104 PASS no instante anterior, uma chamada Maven exit 0, XML Failsafe **1 teste/0 erros/0 falhas/0 skips** do seletor exato, OS pré/pós PASS, Flyway 106/105/0, zero consumidor e igualdade PRE/POST de master, alvo, segurança, contagens e 064. Inventários da mesma SQL/escopo: PRE 2537, POST 2538, uma adição de uma coluna `auto_created`, sem filtro/índice/user, fora das quatro colunas V105 e de audit/mart/recon/ctl, zero remoções/modificações de grupos, duas leituras POST iguais. Recibo novo SHA `821123B1BA8480C80382EFDBADB9B695F7E57E850BDD1052EC092CE1A4C88AF0`; nenhuma chamada física nova.

**Classificação local do método 2: `PASS_METHOD2_LOCAL_OBSERVED_WITH_AUTO_STATS_DELTA`.** O ledger original continua `STOP_POST` e o recibo original registra ausência de `post-receipt.json`: a classificação é uma revisão posterior, com critério diferente, não uma alteração histórica da execução. O método 1 mantém PASS local 0395. O P08 agregado, Gate 1/JaCoCo, 107 ITs/A-B, oito erros e 74 classes faltantes permanecem abertos.

A [documentação Microsoft Learn sobre Statistics](https://learn.microsoft.com/en-us/sql/relational-databases/statistics/statistics?view=sql-server-ver17) diz que, com `AUTO_CREATE_STATISTICS` habilitado, o otimizador pode criar estatísticas de uma coluna para predicados durante a compilação. Isso explica plausibilidade; os recibos 0396 não registram criador nem momento. O inventário agrupa metadados e não identifica `stats_id`. **2538 é observação histórica, não baseline durável.**

Próximo passo já autorizado na mesma mensagem: preparar prova offline completa e, somente se passar, uma rodada física nova e exclusiva para o método 3 de 0377, teto 900 s, com PRE contemporâneo e predicado POST prospectivo. Nenhum outro método autorizado nesta unidade.
