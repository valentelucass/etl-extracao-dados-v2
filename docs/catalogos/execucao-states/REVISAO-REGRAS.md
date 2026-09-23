# Confronto das75regras

Cada regra mantém seu texto e classificação originais no JSON; decisões locais posteriores prevalecem somente no alcance declarado. A revisão não concede aceite nominal por associação de família.

| Regra | Resultado da revisão |
| --- | --- |
| CAP-01 | Travessia local declarada e oráculo usam o mesmo artefato/contexto; tradução real conjunta issue_date+created_at e raiz contábil continuam G03. Não usar per como contagem de raízes. |
| CAP-02 | ContaPagarRules.payment preserva true=PAGO e demais=ABERTO; labels e projeção SQL06 usam a referência selada. |
| CAP-03 | Frescor único máximo de criação e fronteiras civis de transação/liquidação. Decimais numéricos pequenos corrigidos no parser comum; Juros conferido em SQL06. |
| CAP-04 | Raiz/parcela/rateio entram por binding explícito, nunca por inferência de ant_ils_sequence_code. Snapshot real e permissão de exclusão continuam ausentes. |
| CAP-05 | Namespace e labels versionados preservados; classificação desconhecida conserva bruto. A referência sintética não fornece owner nominal. |
| COL-01 | ID6908 tipado/escopado permanece fonte; sequence_code não o substitui. Linhas físicas e raízes são contadas separadamente. |
| COL-02 | Mapper e laboratório temporal conservam precisão, origem do fallback e bruto inválido; SQL recebe a comparação tipada. |
| COL-03 | Terminais/retroatividade e proteção contra regressão permanecem nas provas de domínio/SQL; ordem de página não escolhe vencedor. |
| COL-04 | done e finished conservam labels distintos e terminalidade; não foi alterada apresentação. |
| COL-05 | Mecânica sintética opt-in de candidata/confirmação/reaparecimento preservada. Integral só produz preview e recusa apply. |
| COL-06 | Incompletude, página perdida ou distribuição de linhas errada recusam ausência; capturas independentes permanecem auditáveis. |
| COL-07 | MC/CF consomem bindings e componentes explícitos. Falta de relação não cria associação por posição, nome ou documento. |
| COL-08 | Referências regionais seladas preservam prioridade CEP e cidade/UF, com vigência e recusa de lacuna dependente. |
| COL-09 | Updated_at complementar não vira watermark. Janela/plano/replay persistidos conservam fronteira e revisão explícitas. |
| COL-10 | Números legados não foram promovidos a defaults. Calibração com owner/volume/janela real pertence a G03/G05; budgets locais continuam parâmetros técnicos. |
| COL-11 | Bruto, código/label, ação e tentativas continuam separados nos consumidores de Coletas; nenhuma nova regra de terminalidade. |
| COT-01 | Mapper e promoção usam a precedência temporal local comum; empate divergente não usa chegada/ordem de arquivo. |
| COT-02 | Tarifa vem de referência versionada/vigente, consumida por SQL05; lacuna/sobreposição e revisão estrangeira têm recusa. |
| FAT-01 | Linha4924 e título lógico permanecem identidades distintas. Corrigida colisão da concatenação de raiz/parte/componente no preflight fiscal; separadores STRING são preservados por tupla estruturada, com A2/B24 e série por raiz em SQL01. Grão real continua G03. |
| FAT-02 | EXP-07/ANA-06 definem política sintética explícita, mantendo ambas as evidências no staging e recusando UNRESOLVED duplo. Não foi ratificada precedência fiscal real. |
| FAT-03 | Relações FAT_DOCUMENT_FREIGHT são explícitas; fit_ant_document não identifica Frete nem multiplica receita. |
| FAT-04 | Revisões, cardinalidade e replay permanecem no SQL; identidades de filhos são escopadas na raiz. |
| FAT-05 | Frescor usa máximo contratado, fronteira civil exclusiva e instante CT-e; não há keep-last, arredondamento ou relógio do host. |
| FAT-06 | Labels CT-e e remoção literal de namespace são preservados com bruto; não foi criado catálogo de status novo. |
| FAT-07 | Origem real de serie_nfse continua ABSENT/UNSOURCED_LEGACY. Referência lateral explícita sintética não se torna campo da API. |
| FRE-01 | 6389.id continua chave técnica tipada; a sequência de negócio permanece atributo. |
| FRE-02 | V099 e frescor/replay mantêm CT-e criado→emitido→criação→serviço e terminais; updated_at não ganha autoridade por inferência. |
| FRE-03 | 16regressões V099 executadas: omissão terminal, NULL, frescorCTE, parcial, fallback oficial e outro tenant. Nenhuma migration reaplicada. |
| FRE-04 | pick_item_id/CF são resolvidos por vínculo explícito; candidate paths não viram FK automática. |
| FRE-05 | Ausência Fretes continua desabilitada nominalmente; incremental não comprova exclusão e defaults legados não foram adotados. |
| FRE-06 | CT-e/receita/referências são consumidos no laboratório por SQL02/fatos. Paridade real e owner continuam G03/G04. |
| FRE-07 | Parsing temporal estrito e quarentena de ambiguidade preservados; não foi copiada a heurística US/BR da V1. |
| INV-01 | Raiz e componentes naturais são declarados; hash legado não vira identidade. Reordenação mantém filhos. |
| INV-02 | Precedência performance→finished→started permanece única entre captura/dedupe/promoção. |
| INV-03 | Evidência de comprovante é cumulativa; revisão falsa/mais antiga não apaga true válido. |
| INV-04 | Relação INV_FREIGHT explícita e quarentena de parsing preservadas. M³numérico pequeno alcança SQL11 após a correção comum. |
| LOC-01 | corporation_sequence_number e binding explícito identificam a observação local; pesos/valores têm parsing estrito. |
| LOC-02 | Captura/projeção conservam atributos; previsão usa fit_dpn_delivery_prediction_at, conforme ANA-13, e referências vinculadas. |
| LOC-03 | Volume consome Localização vinculada e fallback Frete no SQL, sem associação por nomes; alvo inexistente é contraprova do JAR. |
| LOC-04 | Valor inválido conserva disposição/quarentena; nenhum NULL silencioso foi introduzido. |
| LOC-05 | Frescor/conflito e recomposição são governados por revisão/contrato; não foi copiado overwrite por hash ou keep-last legado. |
| LOC-06 | Normalização/status desconhecido permanecem no contrato local com bruto e sem terminalidade inventada. |
| LOC-07 | status_branch_nickname da API continua UNSOURCED_LEGACY; binding CURRENT_BRANCH explícito é canal lateral distinto, conforme ANA-13. |
| MAN-01 | Raiz, pick e MDF-e mantêm componentes tipados/escopados e assimetrias em quarentena; número/status/ordem não criam chave. |
| MAN-02 | Coortes/reducers separados por raiz/filho, mesma precedência UTC, sem MAX/SUM genérico de observações conflitantes. |
| MAN-03 | Relações MC/CF e seus órfãos são explícitos; materialização não usa TOP1 por semelhança. |
| MAN-04 | Competência preserva departured→created, presença/NULL e proveniência; inválido bloqueia dependente, sem relógio de extração como fallback. |
| MAN-05 | Exclusão sentinela e receita/capacidade são consumidas no SQL com raízes/Fretes distintos e papéis vinculados. |
| MAN-06 | Frota e classificação vêm de release/identidade/papel/vigência declarados; nomes/CNPJs não viram chaves ou seeds reais. |
| MAN-07 | Sete métricas e capacidade derivada mantêm igualdade/coorte/zeroVALUE e conflito residual, sem soma da expansão. |
| MAT-01 | ANA-02/11 fixa grão local Frete+PE/CB; datas são atributos/partições. Oráculo confere indicadores e recomposição; grão real ainda G03/G04. |
| MAT-02 | ANA-02/16 fixa dia/filial/Geral; contribuições tipadas precedem agregação. Filial múltipla requer atribuição explícita, sem primeira lexicográfica. |
| MAT-03 | EXP-06/18/19 fixa uma linha por Frete explicitamente ligado; calendário/termos versionados, receita sem fan-out e revisão posterior. |
| MAT-04 | EXP-06/07/17 fixa título escopado, placeholder compartilhado, business_date injetada e disposição para data nula; sem filtro full silencioso. |
| MAT-05 | ANA-02 e MAN01–07 conservam raiz/competência/coorte e Fretes distintos antes de somar; nenhuma síntese MAX/SUM sobre produto de filhos. |
| PUB-01 | SQL02 consome atributos tipados, vínculos e referências; regras PE/CB e rótulos têm consumidores próprios, com oráculo de tupla. |
| PUB-02 | SQL03/04 conservam compatibilidade da ausência opt-in e vínculos de usuário; integral não recebe capacidade de exclusão. |
| PUB-03 | SQL05 separa conversão/documento/comentário e tarifa vigente; ausência, NULL e caso emitido são distinguidos. |
| PUB-04 | SQL06/01 usam referências/política fiscal sintética compartilhada; divergência de placeholders não é copiada e decimalNUM chega à coluna correta. |
| PUB-05 | SQL07 usa labels/alias governados e preserva desconhecido; nenhum status passa a indicar exclusão por suposição. |
| PUB-06 | SQL11/12 consomem detalhe corrente, labels e vínculos de filial; M³ e valor a pagar ao cliente recebem números JSON exatos nesta revisão. |
| PUB-07 | SQL08/09 e seis dimensões preservam grãos/identidades declaradas; SQL10 é monitor interno, não publicação/grant externo autorizado. |
| PUB-08 | SQL13 conserva precedência/limite0–43200 de transit-time; percentual decimal é exato e Ordem opcional não identifica parada. |
| RAS-01 | ANA-03 sucede o candidato legado cod_solicitacao+ordem: identidade local é binding de viagem/parada, Ordem é atributo. Atomicidade pai/filhos preservada. |
| RAS-02 | Fuso/Clock/revisão e reativação explícitos permanecem; não foi copiado ZoneId.systemDefault ou overwrite cego. |
| RAS-03 | Cap500 não comprova terminalidade; repartição e janela mínima/incompletude são recusadas conforme contrato, com sucesso independente preservado. |
| RAS-04 | MANTER por ADR0039, desabilitado por padrão e limitado ao laboratório; falha não vira conclusão integral. |
| RAS-05 | Sentinela1900, DST/offset e limites de duração têm disposições; esta revisão corrige somente precisão decimal e não cria regra temporal. |
| RAS-06 | Fallback i+1 da V1 foi lido e não copiado. Identidade real/estabilidade sob reordenação continuam G03. |
| SIN-01 | Sequência/minuta/ocorrência e arrays preservados; identidade real continua G03, sem hash legado. |
| SIN-02 | Frescor treatment→opening único, valores/labels preservados; customer_debits_subtotalNUM chega exatamente a SQL12. |
| USR-01 | Runtime existente, current/history e SQL19 mantêm uma identidade por user_id, hash/no-op e reexecução. |
| USR-02 | Desativação nominal continua fechada; usuários de cancelamento/destruição usam vínculos sem multiplicar o grão. |
| USR-03 | USERS_SNAPSHOT/GraphQL transitório, enabled=true e BACKFILL/REPLAY preservados. Nenhum incremental temporal foi inventado. |
| USR-04 | 9901 permanece somente audit key; seleção id/name e ausência de updatedAt preservadas. Nenhuma chamada DataExport de usuário. |

Os2437registros de campos mantêm as classificações:786implementados localmente,361mapeamentos declarados,442opacos bloqueados,214rastreados por expressão SQL,20retiradas preservadas,19transitórios e595vínculos condicionados. Isso não significa2437campos executáveis completos. O gate do catálogo compara cada ID/path/destino/classificação com a base; fontes, oráculos completos e provas dirigidas conferem o alcance executável.
