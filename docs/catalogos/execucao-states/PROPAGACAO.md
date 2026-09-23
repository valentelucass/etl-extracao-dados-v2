# Propagação dos campos e independência dos esperados

## Valores de fronteira desta revisão

Os exemplos distribuídos desta execução usam `STATES_BOUNDARIES`. Os campos
CAP `interest_value`, FAT `third_party_ctes_value`, INV
`cnr_c_s_fit_total_cubic_volume` e SIN `customer_debits_subtotal` chegam como
números JSON de0,00000001 em A e0,00000002 em B. Seus destinos são,
respectivamente, SQL06/Juros, SQL01/Terceiros/Valor CT-es, SQL11/M³ e
SQL12/valor a pagar ao cliente, todos DECIMAL(28,8).

Raster `PercentualAtraso` recebe12345678,12345678/87654321,87654321 e alcança
SQL13/percentual_atraso_raster. `KmPercorridoEntrega` usa os valores pequenos
acima e é conferido diretamente no core das paradas. Esses atributos não
mudam os totais definidos dos cinco fatos; os cinco grãos e as19saídas são
comparados integralmente. Um esperado SQL13 deliberadamente errado é recusado.

Os mesmos conjuntos também contêm pares de raízes FAT com limites distintos
entre raiz e parcela. Os valores STRING contêm `:STRING:` permitido pelo contrato;
a concatenação anterior das três chaves fiscais colidia. A representação estruturada
mantém ambos os títulos e seus componentes. Séries sintéticas explícitas variam
por raiz e chegam a SQL01; uma série errada no oráculo é contraprova independente.
Isso não fornece a série NFS-e ausente do contrato nominal da fonte.

Os autores de entrada e de oráculo são distintos e recebem literais escolhidos
antes do SQL. Nenhum esperado é regenerado a partir da saída observada.
Os cenários de regressão abaixo preservam também os valores originais da base.

Resultados físicos e do JAR, testes, inputs e hashes são vinculados em
provas.json e verificacao-local.json. O fechamento depende do selo/readback
selecionados pelo FINAL-DELIVERY.json; este documento não substitui esses recibos.

## Dois conjuntos completos

A usa 2 raízes, início em 2037-08-11, revisão 3 e páginas de 2 entidades.
B usa 24 raízes, início em 2039-02-05, revisão 5 e páginas de 4 entidades.
As janelas têm três dias civis. USER conserva páginas de 20: B percorre 20+4.
Cada raiz de MAN/COL/COT tem três linhas físicas; FRE/LOC têm duas. As quatro
expansões têm dois componentes declarados e repetições físicas. Os resultados
esperados respeitam os grãos SQL, sem converter repetição em valor financeiro.

Os IDs usam bases distintas e passos 31/43. MAN→COL é reverso em A e cíclico em B;
as relações recebidas e os esperados fixam a correspondência inversa. A ordem de
captura de B também é reversa. Alterar apenas runId não produziria essas provas.

| Família/apoio | Alteração declarada A → B | Asserção no consumidor |
| --- | --- | --- |
| COL | id/sequence_code, datas e vínculo MAN→COL distintos; suplemento line2 A/B. | SQL03 confere chave, manifesto relacionado, datas e `Complemento`; MAT02 confere as duas modalidades e as tuplas de composição. |
| FRE | total 143,25 → 187,50; IDs e datas distintos; mesmos arquivos/release nos dois caminhos. | MAT03/MAT05 e SQL02/08/09 conferem total e vínculo. SQL02 conserva `Valor Frete` 12,125: ele tem outra origem contratual e não é substituído por total. |
| MAN | sequence_code, chave MDF-e e vínculo de coleta permutados; manifest_freights_total acompanha o total declarado. | SQL08/09 e MAT05 conferem raiz e relação; o total não é somado por linha expandida. |
| COT | sequence_code, datas e qoe_qes_total 176,50 → 291,25. | SQL05 confere `Valor frete`; a tarifa é outro campo e tem asserção separada. |
| LOC | corporation_sequence_number e datas, incluindo previsão, deslocados para o período B; LOC_FREIGHT explicita o destino. | SQL07 confere identidade, previsão e demais colunas; MAT03 confere a dependência de localização. Vínculo com destino inexistente é contraprova própria do JAR. |
| USER | IDs e nomes A/B; nome de cancelamento de COL aponta para raízes diferentes. | SQL19 compara todas as linhas em ordem lexical; SQL03 compara os dois nomes por vínculo. O caso B distingue ordenação lexical de posição numérica. |
| CAP | ant_ils_sequence_code, raiz/parcela/componentes e valor 163,75 → 277,25. | SQL06 compara duas linhas de componente por raiz e seus valores exatos; linhagem corresponde ao wire declarado. |
| FAT | corporation_sequence_number, documento, componentes e valor 163,75 → 277,25. | SQL01 compara componentes; MAT04 conserva um valor financeiro por raiz apesar de múltiplos documentos/linhas. |
| INV | sequence_code, filhos explícitos, datas e valor 163,75 → 277,25. | SQL11 confere duas linhas por raiz, `Valor de NF`, chaves e presença. |
| SIN | sequence_code, filhos explícitos, datas e valor 163,75 → 277,25. | SQL12 confere `Resultado final`, dimensões e linhagem por componente. |
| RAS | sequence_code, janela, placa SYN0008 → SYN0009 e duração 97 → 125 minutos. | SQL13 compara placa e 01:37 → 02:05; três observações do pai conservam seus filhos e uma parada explícita. |
| Referências | região CEP A/B e tarifa SP→RJ 1,53 → 2,17; vigência acompanha toda a janela. | SQL03 compara região; SQL05 compara `Min. Frete/KG`. Referência estrangeira, nova revisão não vinculada, seleção sobreposta e retry divergente recebem recusas físicas. |
| Termos financeiros | billingReferenceDate é início+1 dia em cada período; chave FRE explícita. | SQL02 compara `data_referencia_faturamento`; ausência/zero seguem as regras existentes, sem inferência da data técnica. |
| Suplementos | KM 16,875 → 29,375, complemento A/B, série fiscal explícita A/B e relações por chave. | SQL02 KM, SQL03 complemento/nomes, SQL01 NFS-e/Série; MAN desconhecido e componente FAT ausente impedem a materialização. |
| Relógio e contexto | Datas de fontes, referências, termos, control plane e expected acompanham a janela. | As 19 saídas conferem datas tipadas; dois scopes com as mesmas chaves/datas permanecem separados. |

## Replay, invariâncias e presença

`IntegralArtifactReplayIT` executa captura, replay da mesma revisão e uma revisão
posterior. As três comparações incluem cinco fatos e 19 saídas. O replay mantém
a revisão da fonte, mesmo que a operação tenha outro número. A revisão posterior
altera FRE total para 166,375, `Criado em` para 10:00-03 e `CT-e Criado em` para
11:00:00.1234567-03. O timestamp corresponde à precisão datetime2(7) do SQL;
nanos adicionais do wire permanecem na prova de origem. CAP/FAT/INV/SIN conservam
seus valores nesta revisão: a correção de FRE não autoriza alterar seus montantes.

Os cenários completos comparam valores, NULL e linhagem recebidos. As regressões
de presença dos consumidores são exercitadas separadamente e não são apresentadas
como mutações do conjunto integral:

- `QualificationDeclaredWirePresenceTest`: MissingNode é ABSENT, distinto de NULL
  e VALUE. A contraprova expôs e motivou a correção do comparador.
- `AnalyticLaboratoryQuotesIT`: campo ausente preserva valor efetivo, NULL limpa,
  observação antiga não regride; status, emissão e referência expirada têm decisões
  próprias, conferidas em SQL.
- `ExpansionLaboratoryAdversarialIT`: ABSENT, NULL, zero numérico, texto zero e
  decimal inválido atravessam staging/SQL em cada uma das quatro expansões.
- `RelationalLaboratoryMatrixIT`: tipos e presença da identidade, conflito de
  coorte, finalização, filhos MDF-e e captura parcial têm recusas físicas.
- `ExpansionLaboratoryProjectionsIT`: string vazia/faturado/aguardando faturamento
  usam a semântica explícita de placeholder; vazio não cria documento fiscal.
- `AnalyticLaboratoryUsersIT` e as provas current/history existentes conservam
  USERS_SNAPSHOT e não desativam usuários por ausência de uma página/captura falha.

SQL04 é legitimamente vazio nos dois conjuntos: o universo integral permite
preview, com apply recusado em Java e SQL. A contraprova positiva é
`AnalyticLaboratoryCollectionSweepIT#firstObservationIsCandidateSecondConfirmsAndReappearanceClearsAbsenceFields`:
duas observações completas geram uma linha de 13 campos, e a reaparição a remove.
Esse teste histórico não habilita apply no contrato integral.

## Autoria do oráculo

`IntegralArtifactFixtures` escreve os inputs. `IntegralArtifactOracleFixtures`
escreve os esperados com regras separadas, literais históricos e cálculos locais
explícitos; não abre conexão SQL e não chama mapper de produção para obter o
resultado. A autoria registra os hashes transitivos antes de SQL e antes das
mutações de teste. Os exemplos distribuídos conservam seus arquivos e hashes.

O autor pode consultar o catálogo para nomes/ordem/tipos das colunas; os valores
esperados não vêm da consulta correspondente. UUID da execução, instante observado
e linhagem têm comparadores específicos, limitados. Monitoramento compara os
20 papéis/eventos de cada ciclo, sem ignorar a linha inteira.

`IntegralOracleRejectionIT` altera valor, chave, cardinalidade, precisão e valor
do fato MAT03, mantendo a captura física. Cada mutação falha; as saídas não
afetadas continuam passando e o oráculo íntegro passa antes/depois. O processo
do JAR tem uma contraprova adicional de valor incorreto em SQL02.
