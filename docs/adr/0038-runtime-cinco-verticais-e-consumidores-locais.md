# ADR 0038 — Cinco verticais no runtime e consumidores locais

Data: 08/09/2026. Escopo adotado pelo owner: Bloco 55 A–J, seção 3 do
[prompt](../runbooks/prompt-bloco-55-runtime-cinco-verticais-astra.md).
Complementa ADRs 0024, 0028 e 0032–0037. A fonte é uma fixture em loopback;
a identidade Windows, os consumidores JDBC, a auditoria e o SQL são físicos.

## Composição e contratos

`RuntimeVertical` seleciona exatamente cinco workloads. Coletas/Fretes conservam
seus contratos e fingerprints anteriores. `LocalFiveVerticalRuntime` compõe
Manifestos, Cotações e Localização com adapters e batches tipados, portas concretas
de staging/promoção e o mesmo dispatcher, streamer, DQ e recovery existentes.
Template desconhecido não cai em Fretes. O identificador técnico da paginação
continua separado da chave tipada de domínio. Nenhum enum torna a fonte real apta.

As três extensões aceitam somente BACKFILL/SHADOW_UPSERT no namespace autorizado.
O contrato `bloco55-v1` fixa datas civis sintéticas inclusivas, fuso
America/Sao_Paulo e janela interna com fim exclusivo. Os campos são
`manifests.service_date`, `quotes.requested_at` e `freights.service_at`.
Precisão/inclusão/DST reais continuam BUSINESS_DECISION_PENDING. Não há
`by_updated_at` herdado, nova dependência ou modo incremental para as três.

## Persistência e recuperação

V022 é aditiva sobre V001–V021. Manifestos escreve observações brutas em batch;
o SQL reduz coortes de raiz, pick e MDF-e entre páginas. Presença NULL/VALUE em
conflito impede promoção; zero é valor; métricas complementam somente dentro
da mesma coorte. A chave/número MDF-e permanece um par da observação. Candidatos
reduzidos do chamador e DML direto não são entradas autorizadas do runtime.
Os filhos conservam a relação com a raiz, sem resolver Manifesto→Coleta.

Cotações exige `referenceReleaseId` positivo e explícito. O escopo de autorização
inclui esse campo adicional, sem alterar os 22 campos anteriores para os demais
workloads. O selo persiste a release/fingerprint; recuperação compara o mesmo
material. A única release sintética tem duas tarifas, ratificadas para SHADOW.
Localização conserva parsing, status e proveniência; fallback de Fretes permanece
diferido. Cotações não recebe cálculo financeiro fora do contrato tarifário.

Consumers revalidam consumo, ocorrência, entidade/workload, namespace, modo,
lease, vigência, contrato e DQ. SESSION_CONTEXT isolado não autoriza. STATUS é
somente leitura. Nova invocationId sobre ocorrência conhecida consulta o SQL;
publicação já confirmada retorna sem construir a fonte. Lease expirada não é
tomada, e falha parcial não é promovida.

V023 corrige os nomes físicos/projeções tipadas da captura e acrescenta índices
dos filhos por execução. A projeção em `recon.runtime_vertical_output` é imutável
e nasce na transação da publicação; comparar recibo antigo não lê o core corrente
de outra ocorrência. Ambas as migrations foram qualificadas em rollback antes
de aplicação; seu conteúdo aplicado é imutável.

## Comparação, medição e operação

O comparador SQL usa expectativas escritas independentemente dos mappers e lê
apenas snapshots publicados das ocorrências próprias. Conjuntos, contagens e
somas permanecem no SQL. Mutações dos contracasos afetam somente tabelas
temporárias revertidas. O resultado máximo é comparação sintética local.

A medição usa os cinco pipelines/mappers/batches reais com sink de teste nas
escalas 16/256/4096 páginas, oito registros por página, uma JVM de 512 MiB.
Referências fracas limitadas conferem liberação; um mutante de retenção é
recusado. Duração/heap são diagnósticos. Os planos estimados dos nove consumers
SQL são inspeção local, sem DML persistente, com até 1 MiB por plano de statement.
Essas duas camadas não provam desempenho nem platô de heap produtivo.

O launcher manual valida integralmente manifesto e requests congelados antes
do efeito. Executa no máximo 20 requests, cinco workloads e quatro janelas,
serialmente, com autorização/reserva individual. O SQL continua dono do estado.
Interromper após um número explícito e invocar novamente não reescreve o plano
nem cria scheduler, daemon, PID file ou capacidade global de lote.

## Direitos, correção e aceites

O delta adotado é exatamente sete EXECUTEs SERVICE e seis scopes BACKFILL:
32 grants e 16 scopes históricos no total. Mapping SERVICE v17/OPERATOR v1,
scope original 1 v10, demais originais v1 e vencimento
`2026-10-07T22:34:30.615Z` permanecem. A revisão protegida é
`app-bloco55/a586b69ca27ade40`, com escrita Administrators/SYSTEM e RX restrito.

O primeiro gerador calculou incorretamente três fingerprints DQ. As publicações
foram bloqueadas. Depois de pacote revisável e autorização explícita do owner,
três revisões corretas foram acrescentadas e as inválidas revogadas: seis linhas
históricas, três ativas. O pacote original, a falha e a correção são preservados.
Não houve renovação, novo grant ou descarte dos dados.

Os critérios canônicos de G06/G07/G08 são avaliados para o mecanismo Windows/SQL
adotado. Fonte real não é pré-requisito desses critérios. A aprovação do laboratório
não atribui ao administrador autoridade sobre negócio, fonte, compliance ou
produção. A matriz no runbook identifica o que cada prova satisfaz e os pais
que continuam abertos. A integração A–J tem um único subaceite local em V2-022.

Recuperação após commit é correção aditiva/versionada ou revogação explícita
somente das novas concessões. Não reinstalar baseline, reparar Flyway, editar
migration aplicada ou apagar históricos. B53/B54, revisões protegidas anteriores
e os 18 créditos restantes de B54 são preservados.
