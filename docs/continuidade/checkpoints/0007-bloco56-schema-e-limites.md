# Checkpoint 0007 — schema observado e limite de resolução B56

Sucessor imutável de 0006, em 08/09/2026. Onze resultados HTTP 200/curl 0:
quatro info, quatro páginas de dois registros e três introspecções estáticas.
Documentos GraphQL, ledgers e resumos sanitizados estão no catálogo da continuação.
Nenhuma query GraphQL buscou registros de negócio; nenhuma mutation ou ETL.

O schema inclui DebitBase.id Int, DebitInstallment.accountingDebitId Int,
CheckInOrder.id ID e sequenceCode Int, CreditBase.id ID!, FreightBase.id Int
e accountingCreditId Int. InvoiceBase declara id ID, number/series/key String.
Isso não cria a correspondência com as linhas de Data Export.
DebitBase.installments foi declarado [CreditInstallment!], divergência nominal
concreta que impede assumir o relacionamento contábil correto por semelhança.
Não há InsuranceClaim nem raiz de consulta para sinistros/inventário no schema
observado. CheckInOrder existir como tipo não autoriza mutation para lê-lo.

O pacote revisto aponta campos, crosswalks, garantias e decisão fiscal faltantes.
Somente a observação do shape dos mappings foi acrescentada; ainda sem prova de
chave filha, escopo ou estabilidade universal. P08–P11 abertos, E–H não liberados,
zero novo aceite/vertical. 65/115. O acesso às chaves funcionou; não é a informação
que falta para os vínculos restantes.

1.352 arquivos anteriores conferidos, snapshots de cinco documentos/validators
preservados antes da sucessão. Estados/trilha mantêm suas fotografias históricas
e checkboxes. V2-041 pendente; adoção limitada de leitura não prova rotação.
Zero SQL, runtime, grants, instalação, saldo transferido ou resultado desconhecido.

Próximas ações:

1. Concluir validator da sucessão e contraprovas offline; conferir sanitização,
   preservação e recusa de repetição das etapas executadas.
2. Gerar `target/bloco56-continuacao/final-verification.json`, diff próprio e
   `final/receipt.json`. Este checkpoint não substitui os resultados finais.
3. Com resposta pertinente ao pacote revisto, retomar a identidade correspondente;
   sem novidade, registrar o limite e não gerar domínio sobre suposição.

Recuperação: restaurar só deltas próprios após comparar hashes/edições posteriores.
Preservar artefatos e ledgers; não há rollback de banco. Não repetir as onze consultas.
