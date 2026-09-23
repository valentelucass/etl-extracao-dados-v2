# Retomada — atenções registradas e B61 proposto

ATENCOES_REGISTRADAS_B61_PROPOSTO_NAO_EXECUTADO. O usuário pediu registrar
as atenções da comparação V2/V1 e preparar o próximo chat. Implementação do
B61 ainda não começou. STATES conserva a autoridade sobre critérios e aceites.

[Checkpoint0050](checkpoints/0050-avaliacao-e-preparacao-bloco61.md),
SHA-256 0388f01a0264ec4574aa198fd9abef8eb3df4772bd6244ee5a3c9284cd6c29c4.
[Prompt B61](../runbooks/prompt-bloco-61-consolidacao-local-e-paridade.md):
A, redutor de Manifestos e arquitetura; B, validadores e regressões offline;
C, preparação da paridade; D, testes, diff e continuidade.

O próximo chat executa o escopo local quando o usuário adotar o prompt.
Q-USR-01/Q-COL-01 ainda precisam de oráculos autorizados; Q-MAN-01 mantém hold.
O prompt permite concluir frentes locais sem aguardar esses inputs e não
transforma preparação em paridade real, bootstrap ou cutover.

Sucessão: docs/catalogos/bloco61-preparacao/manifesto.json e
scripts/validation/Test-Bloco61Preparation.ps1. Evidências documentais:
target/bloco61-preparacao-20260910/verification.json e logs.
Atenções completas no início do STATES; relatório comparativo preservado em
target/avaliacao-v2-v1-20260910/RELATORIO.md. Não há nova correção Java/SQL.

B60 continua ACEITO_NO_ESCOPO: 74/74 casos, sete adversariais e recuperação,
com fonte sintética e Windows/JAR/SQL reais em localhost/ETL_SISTEMA_V2_SHADOW.
O controlador b776e40f… permanece NOT_QUALIFIED; a concorrência foi aceita
pela revisão independente da prova física. SQL059 corrigiu o predicado, sem
repetição integral do observador com duas JVMs; SQL060 é o adversarial corrente.
[Checkpoint0049](checkpoints/0049-bloco60-qualificacao-fisica-local-concluida.md)
e target/b60-conclusao-20260910/final/ preservam os detalhes e a recuperação.

O recibo B60 é histórico: os quatro documentos/adaptador evoluídos têm snapshots
exatos na sucessão B61-preparação. Não regravar seu recibo para fazê-lo coincidir
com a fotografia atual. Orçamento encerrado: 204 SQL/83 JVMs físicas/75 HTTP;
nenhuma renovação ou ampliação é criada por esta proposta.

Até três próximas ações:
1. Conferir checkpoint e sucessão documental antes da primeira edição.
2. Após adoção do prompt, concluir A/B com testes locais em build isolado.
3. Entregar matriz C, validação D e checkpoint atualizado, preservando os holds.

Roadmap 67/115 (58,3%), 48 pendentes, 191 rotas, zero AGORA. Java1397/0/0/4
é histórico. Users SHADOW_UPSERT_ONLY permanece transitório. Nenhum aceite
produtivo ou execução física nova nesta preparação.
