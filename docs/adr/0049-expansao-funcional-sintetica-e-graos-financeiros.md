# ADR0049 — expansão funcional sintética e grãos financeiros

12/09/2026. Construção local A–N adotada integralmente pelo usuário, revisada a
pedido durante a implementação. Nenhuma ratificação fiscal ou operacional.

EXP-01: a autorização adicional permite a mecânica sintética que o escopo B56
não permitia. Seus resultados históricos BLOCKED/UNRESOLVED permanecem intactos.
8636 conserva ocorrências físicas; 4924.id identifica linha escopada, não título;
10633 mapping ARRAY de STRING é observação do sucessor B56, sem chave filha;
6392 preserva sequência, minuta e ocorrência distintas. A construção não recebe
identidade real por fixture, hash, ordinal, nome ou igualdade textual.

EXP-02: envelope sintético versionado contém data intacto, provenance,
capture_occurrence técnico e binding lateral. O guard normal observa o envelope
e todos os campos tipados; o ordinal obrigatório satisfaz apenas o contrato de
ocorrência da captura. Não é campo oficial ESL, source key, raiz nem filho.
Somente quatro templates marcados syntheticOccurrenceCapture aceitam essa forma;
HTTP recusa esses templates antes de I/O. per limita linhas físicas, até100;
captura exige selo da auditoria completa, contagem e janela concordantes.

EXP-03: binding declara root/part/component, INTEGER ou STRING, moeda/unidade,
revisão, evidência e cardinalidade aplicável. Chaves filhas são escopadas na raiz.
Observação sem binding fica UNBOUND e não entra no core de laboratório. A chave
SQL é binária, rejeita padding, preserva Unicode/case/tipo; surrogate BIGINT é
separado. Reordenação não reidentifica filhos; arrays preservam todos os textos,
inclusive duplicados/vazios, e posições somente para inspeção física.

EXP-04: todos os campos catalogados/observados do recorte têm tipos explícitos,
presença ABSENT/NULL/VALUE, wire type e bruto; erro tem motivo de quarentena.
Não há metadata genérica. Precisão monetária local DECIMAL(28,8), sem arredondar;
moeda/unidade vêm do binding, nunca inferidas de símbolos. Valores de raiz e de
parcela só reduzem por igualdade comprovada da coorte; divergência é CONFLICT.
Rateio só soma se additive_allocation for declarado. Não há rateio derivado.

EXP-05: CAP usa máximo de criação e fronteiras civis de transação/liquidação;
FAT usa máximo das datas civis contratadas e instante CT-e; INV usa prioridade
performance→finished→started; SIN usa COALESCE(treatment,opening). O comparável
é (epochSecond,nano,inclusive), calculado uma vez pelo domínio e transportado
para dedupe/aplicação SQL. Data civil termina exclusivamente no início do próximo
dia em America/Sao_Paulo; instante conserva nanos. Empate divergente não escolhe
vencedor. Prova de comprovante INV é cumulativa, inclusive evidência antiga
válida; falso não reverte verdadeiro. Reativação exige revisão explícita.

EXP-06: MAT-04 tem uma linha local por título sintético escopado; emissão é
atributo/partição, não identidade. MAT-03 tem uma linha por Frete explicitamente
ligado; referência civil de faturamento é atributo/partição, não identidade.
Correção temporal atualiza a mesma chave e mantém histórico/recibo. Isso resolve
apenas no laboratório as divergências DDL×loader legadas. Grãos reais continuam
UNRESOLVED. UNIQUE, loader, projeção e reconciliação devem concordar.

EXP-07: documento de título ignora os placeholders faturado/aguardando faturamento,
com semântica idêntica em MAT-04 e consulta FAT local. As duas evidências fiscais
coexistem no staging; UNRESOLVED bloqueia consumidor duplo. Alternativas
SYNTHETIC_CTE/SYNTHETIC_NFSE são parâmetros explícitos, nunca defaults de negócio.
serie_nfse segue ABSENT/UNSOURCED_LEGACY. Data nula no full tem disposição, sem
eliminação silenciosa e sem interpretar ausência incremental como exclusão.

EXP-08: referências consumidas usam a fundação V2-035a, releases seladas e seleção
por escopo/vigência/versão. Identidade de filial/pagador é explícita; label não
identifica dimensão. Calendário sintético, labels e atribuição não viram seeds
produtivas. Falta/sobreposição bloqueia somente a saída dependente.

EXP-09: sessão SQL existente suprime commit e fecha com rollback; migrations
aditivas são instaladas fora da IT após master/Windows. Reabrir adapters prova
reidratação SQL da mesma transação. Outra invocação recompõe seu cenário porque
o anterior foi revertido; não há prova COMMIT/crash, serviço ou scheduler.

EXP-10: contagens, joins, coortes, fila, cargas e reconciliação ficam no SQL.
Java retém uma página≤64KiB e um lote≤100. Quatro pipelines e ambas as cargas
devem integrar o mesmo cenário, com hidratação mínima pelo pipeline existente.
Provas exigem três escalas concluídas, repetição intermediária, concorrência
nas procedures efetivas e oráculos manuais. Números locais não demonstram SLA
nem platô. Situação corrente e resultados reais estão no relatório/checkpoints.

EXP-11: dependências 6389/8656 reutilizam os mappers, casos de uso e staging
anteriores. A fonte expansion-dependency-v1 contém somente fixtures empacotadas;
seu fingerprint não altera o release relacional anterior. A aplicação local lê
esse staging e conserva a linhagem física. Um lote JDBC auxiliar mantém os
instantes tipados com nanos, pois DATETIME2(3) do staging anterior perde precisão.
Não altera a promoção operacional nem usa hash de atributo como identidade.

EXP-12: bindings FAT_DOCUMENT_FREIGHT/INV_FREIGHT/SIN_FREIGHT/LOC_FREIGHT são
revisões explícitas, transportadas em TVP até100. CAP não recebe ligação externa.
Documento é chave sintética declarada separada da linha e dos textos de arrays.
Cardinalidade vale entre componente de origem e destino:1:1 limita ambos;
1:N limita origem por destino;N:N conserva as arestas declaradas. Revisões antigas
ficam SUPERSEDED; mesmo vínculo/revisão divergente é recusado. Fonte ausente,
alvo ausente e conflito conservam disposições separadas.

EXP-13: a fila deduplica por run/alvo explícito, claim até100, lease1–60s,
três tentativas e próxima elegibilidade10s×tentativa. Clock é injetado.
EMPTY/TEMPORARY/INVALID/CONFLICT/ABANDONED são distintos; resultado CAPTURED
exige captura completa e observação do alvo. Hidratação extrai somente o alvo
declarado pelo pipeline. Reexecução da resolução não duplica fila ou raízes.
Concorrência física e planos ainda precisam das campanhas M; esses mecanismos
e as provas dirigidas não alegam durabilidade COMMIT/crash.

EXP-14: quatro projeções de detalhe usam o grão de componente corrente ativo,
ordem component_id, cursor exclusivo e limite1–100. A linhagem contém a raiz,
parte/componente sintéticos, execução e ocorrência. Valores de raiz/parcela
expostos nesse detalhe são atributos não aditivos; o resumo financeiro agrega
as raízes em SQL, sem somar repetições do detalhe. Quarentena ainda não superada
por revisão/frescor impede o consumidor afetado, preservando a observação anterior.

EXP-15: V042 reutiliza registro/selagem de V008 para labels, calendário, filial
e pagador. Somente a família EXPANSION_LABELS é acrescida, restrita a origem
DETERMINISTIC_LOCAL e escopo sintético. O guard de recibo anterior conserva as
oito famílias e acrescenta a contagem física dessa tabela. Seleções têm revisão,
vigência exclusiva e escopo do run; sobreposição e conteúdo divergente falham.
O token de pagador é literal da fixture, jamais hash inferido de documento real.

EXP-16: V044 vincula seis fingerprints de fonte imutáveis ao run e confronta
selagem com control plane, fonte, tenant, protocolo, modo/replay, janela e soma
de páginas, incluindo terminal vazia. O Windows SQL usa o fuso equivalente
E. South America Standard Time para conferir a janela produzida pelo Java
America/Sao_Paulo; o frescor de domínio permanece epoch/nano exato.

EXP-17: MAT04 consome a projeção FAT compartilhada, vínculos e labels selados.
Cada título exige igualdade dos atributos de título em todos os componentes;
divergência não escolhe uma linha vencedora. fit_ant_issue_date fornece também
o alias legado data_emissao_fatura e a data-base; não há um segundo path provado.
fit_ant_value fornece valor do título/alias valor_fatura; fallback explícito é
total do Frete da linha, depois zero. Fallbacks repetidos reduzem por igualdade,
nunca por soma de linhas. A referência mensal pode cair na emissão CT-e civil
em America/Sao_Paulo. Documento/placeholder usa a mesma função da consulta FAT.
Chave cliente cnpj:/nome: é atributo de apresentação com proveniência, sem virar
identidade de dimensão. Aging usa business_date do run, não relógio de parede.
Full conserva NULL_DATE e demais disposições bloqueadas. Incremental visita
capturas afetadas e datas antigas, sem apagar títulos ausentes. Totais financeiros
consideram somente READY, agrupados por moeda/unidade e título distinto.

EXP-18: MAT03 requer inputs que o staging operacional de Fretes não contrata
para aritmética: data de referência, elegibilidade, cortesia, classificação,
volume de fallback, pagador e unidade/moeda. A fixture lateral versionada
expansion-freight-terms-v1 declara esses inputs sintéticos por source key tipada;
ela atravessa parser tipado e lote da mesma captura de Frete, separada de /data.
O valor monetário continua vindo do Frete capturado; receita final não está na
fixture. Mesmo payload ESL com nova revisão dos termos pode corrigir a data.
Empate divergente dos termos é CONFLICT. Nenhuma revisão usa alias da minuta,
nome ou documento fiscal para ligar o Frete. Isso não ratifica a política real.

EXP-19: MAT03 tem uma linha por dependência Frete tipada no run. Linhas fiscais
participam somente por vínculo explícito; vários documentos não multiplicam
receita. O calendário selecionado recua a data conforme sua regra versionada.
Filial exige atribuição de pagador na mesma revisão de referências. Volumes vêm
da Localização vinculada; sem esse valor, usam o fallback lateral declarado.
Mais de uma Localização não escolhe uma arbitrariamente. Cancelamento conserva
a proveniência FAT/status/resultado ou Frete com CT-e explicitamente vinculado.
O bloqueio requer conjuntamente “bloqueio” e “anulação” ou “isolamento”.
Cortesia, inelegibilidade, inatividade, cancelamento e data nula geram receita
zero quando os termos são válidos; dependências e políticas desconhecidas
mantêm receita nula e disposição específica. READY é o único estado somado
como receita elegível, por moeda/unidade. Exclusões conhecidas de valor zero
continuam fora de READY e contam na coluna blocked do recibo, sem virar erro SQL.
V048 compara bytes dos atributos textuais ao detectar alterações, preservando
padding e caixa entre collations das referências e dos termos. A falha original
de collation da V047 permanece registrada, e a migration aplicada foi preservada.

EXP-20: V049 persiste plano por run/modo/revisão/ordinal e data. Cada partição
reserva seis UUIDs de captura e os recibos das duas cargas. O executor reaberto
lê esses slots no SQL; captura completa anterior ao attach não é repetida.
Um erro dentro da captura reverte seu savepoint, enquanto o plano registra a
fronteira e uma categoria sanitizada na transação externa quando esta continua
válida. Nenhum tratamento mascara uma transação condenada pelo SQL Server.
Replay identifica explicitamente a revisão BOOTSTRAP completa de origem;
plano igual é idempotente e alteração exige outra revisão. A primeira partição
incompleta permanece visível quando as posteriores terminam antes dela.
Não avança watermark produtivo. Trata-se de reidratação SQL na mesma transação,
sem promessa de recuperação após COMMIT ou crash.

EXP-21: ExpansionLaboratoryMain é uma entrada companheira no mesmo artefato,
seguindo o laboratório relacional existente. O Main padrão continua dormente.
scenario/hydrate/replay/status/query exigem --synthetic-expansion-lab e as duas
travas da sessão local. Configuração é validada antes de abrir conexão: somente
fixtures empacotadas, até 256 raízes no conjunto, três dias, página até 16 e
consulta até 100 linhas. Cada invocação prepara e reverte seu próprio cenário.
status demonstra a dependência pendente; hydrate executa outro plano limitado
na mesma sessão para resolvê-la. A seleção de uma das seis consultas exerce seu
adapter tipado e publica apenas a quantidade retornada, além da reconciliação.
As referências cobrem explicitamente a janela de captura mais sete dias de
cada lado, pois datas financeiras das fixtures diferem da partição de captura.

EXP-22: V050 exclui valores de componentes inativos da redução corrente de
raiz/parcela. Observações e história permanecem disponíveis; comprovante INV
continua cumulativo. A raiz é ativa se algum componente está ativo. Reativação
exige sinal explícito e nova revisão; uma revisão posterior também supera a
quarentena de uma tentativa anterior. Contraprova física encontrou o defeito
da redução, e a correção foi feita sem alterar V039. O teste FAT foi corrigido
separadamente para que o timestamp controlado superasse as demais datas do
frescor, preservando a regra de máximo já implementada.

EXP-23: a campanha de medição usa 16/64/256/64 raízes por vertical, páginas de
16, heap de 512 MiB e teto de 240 segundos por caso. O recorte menor considera
as 151 colunas de origem, suas representações físicas e as duas cargas SQL.
ManagedPageGauge e MeasurementDiagnostics da fundação V2-050 medem páginas,
retenção, bytes, heap e duração. A sessão conta preparações de statements e
criação de statements ao encaminhar as chamadas reais ao driver; esses números
não são apresentados como contagem de todos os comandos executados internamente
por uma procedure. Planos reais são capturados com STATISTICS XML quando
disponível, sem grants. Resultados e limitações serão vinculados à campanha
concluída; o desenho da medição por si só não é prova executada.

## EXP-24 — Arrays de Sinistros no contrato físico

A prova independente dos 151 campos encontrou três arrays de Sinistros ausentes da lista CHECK da V038. A V051 amplia somente a lista fechada para rcfdc/rctac/rctrc; mantém posições 0–31, tipos, ordenação e duplicatas. Fonte: campos-tipados.json/B56_CONTINUACAO_OBSERVED. Consumidor: staging e comparação exata, sem inferência de vínculo. Prova: ExpansionLaboratoryFieldCoverageIT; a tentativa physical-fields-01 falha foi preservada. Owner nominal e semântica real dos itens continuam não ratificados.
