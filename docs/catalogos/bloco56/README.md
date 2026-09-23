# B56 — preparação local das quatro identidades e verticais condicionais

Escopo adotado em 08/09/2026: preparação, investigação estática dos artefatos
permitidos e implementação/testes locais das frentes com dependências comprovadas.
Direção: Java 17, Windows e SQL Server. Nenhum efeito físico B56 adotado.

## Resultado da triagem

As quatro decisões permanecem em hold. Os contratos, manifests de identidade,
matriz V2-017a e âncoras estáticas conferem com a evidência previamente catalogada.
Não foi apresentado novo contrato do fornecedor ou corpus representativo aprovado
que responda às lacunas. A triagem de integridade não constitui uma nova campanha
de caracterização nem uma reabertura do Bloco 34.

| Frente | Critério e estado | Componente condicionado | Prova que falta |
| --- | --- | --- | --- |
| A / P09 | V2-009b/8636: bloqueada | Decisão de identidade contábil | ID e wire type da raiz; grão, colisões e cardinalidade raiz/parcela/rateio |
| B / P10 | V2-009b/4924: não resolvida | Decisão de linha, título e vínculos | Estabilidade/escopo do ID; crosswalks, filhos, alias/rekey e precedência fiscal |
| C / P08 | V2-009b/10633: não resolvida | Decisão da raiz e mapping | Raiz, papel Frete/minuta, shape/tipos, componentes naturais e cardinalidade |
| D / P11 | V2-009b/6392: não resolvida | Decisão de grão | Sequência versus composição legada; papéis, colisões e cardinalidade |
| E | V2-029 bloqueada por A | Domínio, mapper/batch, staging/promoção SQL e enforcement | CAP-01–05 e aceite integral V2-029 após A |
| F | V2-030 bloqueada por B | Linha/título/documentos/filhos, mapper e promoção SQL | FAT-01–07 e aceite integral V2-030 após B |
| G | V2-031 bloqueada por C | Raiz/filhos, mapper, parsing e reducers SQL | INV-01–04 e aceite integral V2-031 após C |
| H | V2-032 bloqueada por D | Raiz/componentes, mapper e reducers SQL | SIN-01/02 e aceite integral V2-032 após D |

O mapa detalhado está em [matriz-criterios.csv](matriz-criterios.csv), com o texto
original das 18 regras e dos quatro aceites de implementação, componentes,
provas esperadas, limites e estado. São linhas de rastreabilidade; não são novos
itens do roadmap nem testes executados. A [triagem](triagem.json) contém os quatro
holds integrais, dependências e evidências. A matriz original de portabilidade e
os manifests de identidade/contrato permanecem intactos.

## Dependências e consequência para a implementação

V2-018/019/020/021/022a/023/043/044 estão concluídas no escopo local existente;
V2-011 fornece a base de Fretes para F/G/H. A fundação local de referências
V2-035a basta como dependência de E/F; não se exige fechar o pai produtivo.
Os quatro contratos V2-025b estão concluídos como contratos offline transitórios.
Isso conserva os gates de identidade próprios. Nenhuma das quatro verticais está
liberada para modelagem de domínio ou promoção nesta rodada.

Não há Java, migration, mapper, schema, relação ou reducer novo dessas verticais.
A preparação não é `IMPLEMENTADA_EM_SHADOW`; 65/115 (56,5%) permanece o progresso
comprovado. As cinco verticais operacionais do B55 conservam sua prova histórica.

## Input comum necessário

Entregar material pelo canal privado autorizado do projeto, com um índice
sanitizado. Não colocar payloads, IDs de negócio, documentos fiscais, credenciais
ou hashes de IDs em documentação pública. O índice de cada artefato deve informar:

- origem e responsável pela emissão; versão e data; template/release aplicável;
- conta lógica/tenant escopados por referência não secreta; janela e filtros;
- autorização de obtenção e de uso local; local de guarda e SHA-256 do arquivo;
- se é garantia do fornecedor, observação representativa, decisão aprovada ou
  fixture; quais afirmações sustenta e quais permanecem desconhecidas;
- owner/aceitante nominal ou time responsável, data e escopo da revisão humana.

Hash de arquivo verifica integridade; não prova autoria, autenticidade nem aceite.
Valores sintéticos não podem substituir uma observação representativa. A prova
deve preservar os tipos JSON e a possibilidade de relacionar observações dentro
do corpus autorizado sem publicar os identificadores. Uma tabela de contagens
isolada não permite conferir colisões ou vínculos.

Para reabrir somente uma frente, seu material deve responder às lacunas dela.
Não é necessário aguardar as quatro. Sem material pertinente novo, conservar o
hold; repetir duas travessias ou observar unicidade em amostra não prova garantia
universal, estabilidade, tenant ou completude.

## Pacote A / 8636 — Contas a Pagar

Referência: `identidade-contas-a-pagar/manifesto.json`,
`identity.outcome.unblockEvidence`; CAP-01–05; V2-029.

Solicitar ao fornecedor o path e o tipo JSON do identificador estável da raiz
`accounting_debit`, seu escopo e comportamento em alteração/reemissão. Confirmar
se o template atual o publica; se não publica, indicar a revisão oficial que o
exponha. Não escolher outro template nem executar fonte por inferência.

Obter dicionário de parcela/rateio/centro de custo/plano contábil: papéis, chaves
naturais, nulidade, relacionamento e cardinalidade por raiz. Esclarecer o wire
type de `ant_ils_sequence_code`: histórico Integer e DTO String com coerção são
evidências diferentes. O contraexemplo antigo já refuta a candidata como chave
da linha física e deve continuar presente.

O corpus representativo repetido deve permitir comparar raiz/parcela/rateio:
replay exato, alteração, múltiplas parcelas/rateios, campos contábeis divergentes,
nulo/ausente e mudança de escopo. Travessia e oráculo devem usar exatamente os
mesmos filtros `issue_date + created_at`. Não descartar variações por keep-latest.
Para cada valor financeiro, identificar se pertence à raiz, parcela ou rateio e
qual reducer evita multiplicá-lo na expansão; fixar moeda/unidade/precisão/escala.

Verificação posterior: tipos/presença, colisões, estabilidade no escopo declarado,
cardinalidade e refutação dos contraexemplos, seguida da decisão de identidade.
Só então modelar E. Identidade não libera sweep nem prova `per`/completude.

## Pacote B / 4924 — Faturas por Cliente

Referência: `identidade-faturas-por-cliente/manifesto.json`,
`identity.outcome.unblockEvidence`; FAT-01–07; V2-030.

Solicitar garantia versionada do que `id` identifica e de sua estabilidade/escopo.
O aceite existente alcança apenas a linha escopada. Identificar separadamente o
título lógico e fornecer evidência representativa de linha ↔ título ↔ CT-e/NFS-e
↔ Frete, com chaves e papéis explícitos. `fit_ant_document`, número fiscal,
`billingId`, `unique_id` e hashes antigos não constituem crosswalk aprovado.

Especificar shape/tipo e identidade dos elementos de `invoices_mapping` e
`fit_fte_invoices_order_number`, duplicatas, coleção ausente/nula/vazia, ordenação
e cardinalidade. Incluir título com vários Fretes/documentos, replay, correção
do documento, mudança de alias, escopos distintos e colisão/ambiguidade.

O responsável financeiro deve decidir nominalmente a precedência quando CT-e e
NFS-e coexistem, incluindo alias/rekey, cancelamento e histórico. A divergência
entre mapper e SQL legados deve ser respondida; a decisão não pode ser obtida
por votação de fixtures. Até lá, o caso duplo permanece `UNRESOLVED`.
`serie_nfse` permanece `ABSENT/UNSOURCED_LEGACY` sem path comprovado ou retirada
nominal; sua ausência não justifica inventar um requisito da API.

Verificação posterior: relações e cardinalidades contra o corpus, conflitos e
replay determinísticos; decisão de identidade e regra fiscal; só então modelar F.

## Pacote C / 10633 — Inventário

Referência: `identidade-inventario/manifesto.json`,
`identity.outcome.unblockEvidence`; INV-01–04; V2-031.

Solicitar identidade versionada da raiz ou observações representativas repetidas
aprovadas que resolvam sequência versus composição legada. Exigir o papel
versionado de `cnr_c_s_fit_corporation_sequence_number`: raiz, atributo, filho ou
referência de Frete/minuta, com regra explícita de resolução por escopo.

Obter schema/shape real versionado de `cnr_c_s_fit_invoices_mapping`: tipo do
campo e dos elementos, paths, componentes naturais, presença/nulidade e significado
da expansão física. `Object` no DTO não discrimina array, objeto ou texto. Não
usar a fixture artificial existente para escolher um parser remoto.

O corpus deve permitir analisar colisões raiz/filho em replay e alteração de
mapping/minuta/started_at, expansão cruzada, ordem de elementos e múltiplos Fretes.
Informar quais valores/pesos/volumes são repetidos e quais são aditivos, com
unidades e regras. Testar grão e cardinalidade sem substituir chaves por hash do
mapping mutável. A ausência de colisão no corpus delimita apenas esse corpus.

Verificação posterior: raiz e papel da minuta, schema, componentes naturais,
colisão e cardinalidade; decisão versionada antes de parser/reducer/domínio G.

## Pacote D / 6392 — Sinistros

Referência: `identidade-sinistros/manifesto.json`,
`identity.outcome.unblockEvidence`; SIN-01/02; V2-032.

Solicitar identidade versionada da raiz ou observações representativas repetidas
aprovadas. Esclarecer os papéis versionados de `sequence_code`,
`icm_fis_ioe_number` e `icm_fis_fit_corporation_sequence_number` e distinguir
invoice, ocorrência e minuta. Coocorrência escalar não prova filho ou relação.

Comparar a sequência com todos os componentes do grão legado no corpus:
mesma sequência com invoice/ocorrência/minuta diferentes, replay, alteração,
componentes ausentes/nulos, escopos distintos e divergências financeiras.
Determinar cardinalidade e se cada valor pertence à raiz ou a um componente.
Registrar formatos de horas/datas, timezone e precisão para manter bruto e
tipado, sem confundir hora civil com instante.

Verificação posterior: análise comparativa de colisões, estabilidade no escopo,
papéis e cardinalidade; decisão de grão antes de simplificar identidade ou modelar H.
O texto original que admite hash somente como alias não aprova um alias específico:
o manifesto rejeita o hash legado até evidência versionada de vínculo.

## Aplicação, verificação, recuperação e limites

O pacote atual aplica apenas documentação e validadores locais, vinculados por
[manifesto.json](manifesto.json). Não existe migration B56 pronta: seu grão ainda
é desconhecido. `physicalEffects`, `rightsDelta`, `eligibleVerticals` são vazios;
orçamento/reservas/HTTP/SQL/instalações B56 são zero. Não se solicita autorização
genérica para preencher esses campos depois.

Depois de uma identidade ser comprovada, implementar e testar os componentes
locais daquela vertical conforme o aceite original e a matriz deste pacote.
Preparar a migration aditiva e a paridade de baseline sem executá-las. Concluir
código/testes autorizados antes de pedir adoção do pacote físico concreto.

Um eventual pacote físico deve fixar revisão/hashes de todos os arquivos,
alvo/contas e catálogo vigente conferidos, DQ/referências, capacidades e diferenças
exatas de scopes/grants, ordem de aplicação, verificação em conexão independente,
recuperação e dados que permanecem. Deve quantificar operações, páginas, bytes,
linhas, concorrência e duração a partir do plano concreto. Não herdar os 32 grants,
16 scopes, 125 unidades B55 ou 18 B54, nem renovar sua vigência. A direção local
continua `localhost/ETL_SISTEMA_V2_SHADOW` com Windows auth; isso não significa que
tenha sido feita conferência SQL atual ou adotada uma instalação B56.

Fonte real exigiria V2-041 e adoção específica de fonte/janela/teto, além do
controlador revisado. Está proibida nesta execução. O caminho de desbloqueio
solicitado agora é material já obtido e autorizado para análise estática local.

Verificação offline da entrega:

```powershell
pwsh -NoProfile -File scripts/validation/Test-Bloco56Preparacao.ps1
pwsh -NoProfile -File scripts/validation/Test-Bloco56PreparacaoGuards.ps1
pwsh -NoProfile -File scripts/validation/Test-ContinuidadeAgentes.ps1 -IncludePrivateEvidence
pwsh -NoProfile -File scripts/validation/Test-ContinuidadeAgentesGuards.ps1
pwsh -NoProfile -File scripts/validation/Test-Bloco55Integrated.ps1 -IncludePrivateEvidence -RequireComplete
pwsh -NoProfile -File scripts/validation/Test-Gpt56ChatTrail.ps1
```

Para recuperação de arquivos, usar `target/bloco56/initial/` e o diff próprio
`target/bloco56/final/bloco56-only.patch`. Conferir o hash corrente contra o
`after` antes de restaurar cada arquivo alterado; preservar qualquer edição
posterior. Os cinco snapshots documentais anteriores também estão em
`docs/continuidade/historico/pos-bloco55/`. Não executar reset/clean, apagar
evidências ou aplicar recuperação de banco. Um resultado de ferramenta perdido
exige conferir os receipts próprios antes de repetir a ação.
