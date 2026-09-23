# Ponto de retomada do projeto

Atualizado em 08/09/2026. **B56_INVESTIGACAO_REMOTA_REGISTRADA_IDENTIDADES_BLOQUEADAS**.
Autoridade: mensagens efetivas do usuário e critérios completos do STATES.

## Objetivo e leitura mínima

Resolver P08–P11 e implementar V2-029–032 somente com dependências comprovadas.
Ler AGENTS, STATES, contexto global e protocolo; depois o
[checkpoint 0008](checkpoints/0008-bloco56-validacao-da-continuacao.md) e o
[pacote revisto](../catalogos/bloco56-continuacao/README.md).
0004 é o fechamento histórico da preparação; 0005/0006 registram info/amostras.

## Autorização e resultado observado

O pedido posterior de continuar por APIs/documentação e usar os `.env` foi
aplicado à investigação limitada de leitura. Não equivale a resolver V2-041,
rotacionar chaves, alterar SQL, executar ETL ou adotar campanha/orçamento runtime.
Onze consultas observadas, todas HTTP 200/curl 0: quatro info, quatro dados,
três introspecções. Dados: 04/09/2026, page 1/per 2, oito linhas ao todo.
Planos e ledgers próprios em `target/bloco56-continuacao`; cópias sanitizadas
versionadas no pacote. Sem resposta desconhecida ou processo próprio ativo.
Não repetir consultas: as três sondas recusam etapas cujo ledger já existe.

## Novidade e lacunas restantes

- P09/8636: ID raiz ausente nos paths testados. Schema declara DebitBase.id Int
  e DebitInstallment.accountingDebitId Int; o export não oferece o vínculo.
  DebitBase.installments declara CreditInstallment: inconsistência a esclarecer.
- P10/4924: id INTEGER e mappings ARRAY de STRING na amostra. Schema declara
  FreightBase.accountingCreditId e CreditBase.id, sem provar o crosswalk export.
  Faltam chaves filhas, estabilidade/escopo/alias/rekey e decisão CT-e/NFS-e.
- P08/10633: mapping ARRAY de STRING observado (três itens), sequence INTEGER.
  CheckInOrder tem id ID e sequenceCode Int distintos; sem crosswalk/chave filha.
- P11/6392: sequence INTEGER, número de nota STRING, IDs técnicos não retornados.
  Nenhum InsuranceClaim no schema recebido. Grão/raiz/componentes ainda abertos.

Amostra não prova identidade/estabilidade universal. Nenhuma vertical liberada.
65/115 (56,5%), 50 pendentes, 193 rotas abertas, zero AGORA. Java/runtime intactos;
1.138 testes Java são históricos B55.

## Próximas ações — até três

1. Conferir `target/bloco56-continuacao/final-verification.json` e
   `final/receipt.json`; se ausentes, concluir só validação/diff offline em andamento.
2. Receber do fornecedor/owner os campos e garantias exatos do pacote revisto,
   inclusive FAT-02; sem novidade pertinente, não repetir as onze consultas.
3. Com identidade integralmente comprovada, implementar/testar sua vertical pelos
   critérios originais; preparar aplicação/recuperação para efeitos adicionais.
   Continuar outra frente se houver dependência nova elegível.

## Preservação e recuperação

1352 arquivos anteriores conferidos/copiados em `target/bloco56-continuacao/initial`.
Revisões anteriores em `historico/bloco56-preparacao`; manifest B56 original e
B53/B54/B55 imutáveis. Sem SQL/DDL/DML, grants/scopes, instalação protegida,
ETL_SISTEMA, produção, agenda, rotação, commit/push, transferência ou renovação.
Recuperar só deltas próprios após comparar hashes e edições posteriores. GET e
introspecção não exigem rollback de aplicação; nunca apagar provas.
