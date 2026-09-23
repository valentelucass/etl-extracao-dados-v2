# B56 — investigação remota das quatro identidades

Em 08/09/2026, as credenciais provisionadas permitiram **11 consultas, todas HTTP
200/curl 0**: quatro metadados, quatro páginas de duas linhas e três introspecções
de schema. Essa investigação é sucessora da preparação B56; não reescreve seus
manifests, contadores, resultados ou aceites. Direção técnica: Windows/SQL.

O usuário pediu continuidade pela leitura de APIs/documentação e reafirmou o uso
dos `.env` após a pergunta sobre rotação. Foi adotada somente essa leitura limitada.
V2-041 segue pendente; acesso funcional não prova substituição/invalidação de chaves.
Não houve SQL, ETL, rotação, escrita remota, nova campanha runtime, grant, scope,
instalação protegida, transferência de saldo ou renovação de autorização anterior.

## O que foi observado

| Frente | Evidência nova | Limite da conclusão |
| --- | --- | --- |
| A/P09/8636 → E/V2-029 | Metadata: 28 campos; parcela `ant_ils_sequence_code` INTEGER nas duas linhas; `/id` e `/accounting_debit_id` ausentes | Raiz e vínculo parcela/rateio não identificados; duas parcelas distintas não invalidam colisão histórica |
| B/P10/4924 → F/V2-030 | 52 campos; `/id` INTEGER; `invoices_mapping` ARRAY de STRING, quatro itens; pedidos ARRAY de STRING, dois itens e um array vazio | Textos não fornecem identidade de documento; título, vínculo com Frete, alias/rekey e decisão fiscal não resolvidos |
| C/P08/10633 → G/V2-031 | 26 campos; sequence INTEGER; mapping ARRAY de STRING, três itens nas duas linhas | Shape observado em fonte real; chave filha, vínculo de raiz/Frete e cardinalidade ainda desconhecidos |
| D/P11/6392 → H/V2-032 | 44 campos; sequence INTEGER; `icm_fis_ioe_number` STRING; identificadores técnicos testados ausentes | Número de nota não identifica universalmente nota/ocorrência; raiz versus composição ainda sem prova |

Janela solicitada nas quatro amostras: **04/09/2026**, page 1/per 2. Em 8636 foram enviados
issue_date e created_at juntos. Não houve próxima página nem comparação temporal.
Cada resposta teve duas linhas físicas. Contagens de valores distintos são apenas
observações da amostra; todos os resumos mantêm `identityProven=false`.

O schema remoto declarou:

| Tipo/campo GraphQL | Tipo declarado | Uso possível da evidência |
| --- | --- | --- |
| DebitBase.id / sequenceCode | Int / Int | Identificadores distintos no schema; sem correspondência declarada com 8636 |
| DebitInstallment.id / accountingDebitId / sequenceCode | Int / Int / Int | Campos concretos para exigir o vínculo raiz/parcela no export |
| DebitBase.installments | [CreditInstallment!] | Inconsistência nominal observada; esclarecer antes de usar |
| CreditBase.id / sequenceCode | ID! / Int | Entidade contábil existe, sem prova de que seja a linha 4924 |
| FreightBase.id / accountingCreditId | Int / Int | Caminho para uma futura comparação cruzada; vínculo 4924 não observado |
| FreightBase.invoicesMapping | JSON | Schema não declara chaves/tipos internos do mapping |
| CheckInOrder.id / sequenceCode | ID / Int | ID e código distintos; sem crosswalk com 10633 |
| InvoiceBase.id / number / series / key | ID / String / String / String | Número e chave técnica distintos; nenhuma igualdade presumida |

Não foi encontrado `InsuranceClaim` nem consulta raiz de inventário/sinistros no
schema recebido. `CheckInOrder` existir no schema não implica rota de consulta
disponível. Não foram executadas mutations para obter dados. As descrições dos
campos de identidade acima vieram nulas; não há garantia textual de estabilidade,
escopo ou imutabilidade para preencher essas lacunas.

## Pacote concreto de informação que falta

Estas solicitações são conteúdo preparado para o owner/fornecedor; não foram
enviadas a terceiros. Os nomes GraphQL abaixo foram recebidos do schema. Não se
inventam paths Data Export: o fornecedor deve declarar o path efetivo e o tipo.

**A — Contas a Pagar.** Declarar no template 8636 o identificador técnico da raiz
contábil, o ID técnico da parcela e seu vínculo com a raiz, além do identificador
ou chave composta aprovada do rateio. Conferir os campos GraphQL `DebitBase.id`,
`DebitInstallment.id/accountingDebitId/sequenceCode`, sem assumir igualdade entre
ID e código. Explicar `DebitBase.installments: [CreditInstallment!]`. Fornecer
grão, escopo e estabilidade de cada chave, cardinalidade raiz→parcela→rateio e
contraexemplo de expansão que preserve totais. Regras CAP-01–05 continuam integrais.

**B — Faturas.** Declarar a entidade e estabilidade de `/id` em 4924; fornecer
crosswalk explícito com `FreightBase.id/accountingCreditId` e `CreditBase.id`,
indicando ausências e relações múltiplas. Declarar IDs de documentos e dos filhos
em ambos os arrays; textos observados não serão promovidos a chave. Cobrir
CT-e/NFS-e, múltiplos Fretes, colisão, alias, rekey e replay. O owner deve escolher
e justificar a precedência **quando CT-e e NFS-e coexistirem**: o legado diverge
entre mapper e SQL; nenhuma das consultas resolve essa decisão FAT-02. Preservar
FAT-01–07, inclusive serie_nfse ausente/sem origem e regras temporais.

**C — Inventário.** Declarar a ligação do template 10633 a `CheckInOrder.id` e o
papel/escopo de sequence_code; expor identidade técnica do Frete e dos documentos
associados. Declarar se os textos do mapping representam número, série, chave
fiscal ou apresentação, e fornecer chave filha independente da posição/label.
Provar duplicidades, reordenação, remoção/retorno e cardinalidade de raiz/filhos.
O shape ARRAY de STRING já foi observado; não é necessário pedir outra fixture
para demonstrar essa observação. Manter INV-01–04 e não reutilizar hash legado.

**D — Sinistros.** Declarar entidade/ID estável da raiz no template 6392 e os IDs
da relação nota/ocorrência/Frete. Explicar `/sequence_code`,
`/icm_fis_ioe_number` e `/icm_fis_fit_corporation_sequence_number`, com escopo,
grão, colisões e cardinalidades. A inexistência de InsuranceClaim no schema
consultado não autoriza adivinhar endpoint ou usar número de nota como chave.
Preservar SIN-01/02, incluindo reducers, tratamento, horas e frescor.

Para todas as frentes, a resposta precisa informar origem/versão/data,
responsável pela semântica, template e escopo lógico não secreto, paths e wire
types, garantia ou limite declarado e exemplos sanitizados que distingam
colisão, expansão, rekey, exclusão/retorno e execução fora de ordem.
Uma amostra única ou o hash de um artefato não substitui essas garantias.

## Implementação, verificação e recuperação

As dependências locais anteriores continuam disponíveis, mas nenhuma identidade
foi integralmente comprovada. Portanto **E/F/G/H não foram liberadas**, e nenhum
domínio, mapper, migration ou runtime foi criado sobre uma identidade suposta.
São zero aceites novos: **65/115 = 56,5%**, com 50 pendentes e 193 rotas abertas.
Os critérios originais permanecem na [matriz B56](../bloco56/matriz-criterios.csv).

Foram implementadas três sondas de investigação, com testes offline, bounds,
credenciais somente via stdin do curl, sanitização e bloqueio de repetição por
ledger. Os GETs usam timeout 30 s/conexão 10 s, até 10 MiB, sem redirect/retry;
as queries estáticas de schema aplicam os mesmos limites. Não se gravaram
payloads, URLs de conta, tokens, IDs, hashes de IDs ou cursores. Os ledgers
reservam antes da chamada e registram o resultado antes da etapa seguinte.
Planos e resultados estão em [evidencias](evidencias/); origem privada em
`target/bloco56-continuacao/`. Não repetir esses requests já executados.

O validator da continuação verifica exatamente os sete deltas documentais/de validação,
snapshots anteriores, manifest B56 imutável, evidências, contagens e ausência de
aceite falso. O validator da preparação passa a conferir sua fotografia histórica
e compor os hashes atuais; os guards antigos exercitam a revisão anterior em
cópia isolada. Não há exceção genérica para Java, SQL ou manifests históricos.

Validação final: `target/bloco56-continuacao/final-verification.json`.
Diff/receipt próprios: `target/bloco56-continuacao/final/`. Testes Java B55 são
históricos; não se alega uma nova suíte Java para alterações de sondas/documentos.

Recuperação: conferir diff e hashes, então restaurar somente arquivos alterados
nesta fase a partir de `initial/` ou dos snapshots em
`docs/continuidade/historico/bloco56-preparacao`, preservando edições posteriores.
Não apagar evidências/ledgers; nenhuma recuperação de banco se aplica às leituras.
Qualquer alteração dos templates, execução de ETL, SQL, instalação ou grants exige
pacote próprio de aplicação/verificação/recuperação e adoção adicional. Não há
script de aplicação pronto porque os IDs/paths e a decisão fiscal ainda faltam;
preencher migrations ou permissões agora significaria inventar o contrato.

O scanner foi ampliado para ler .graphql/.jsonl como texto, mantendo todas as regras de detecção. A falha inicial por extensão não reconhecida está preservada em target/bloco56-continuacao/secrets-01.log; não foi um achado de credencial. Contraprovas cobrem texto limpo e segredo sintético detectado em ambas as extensões.
