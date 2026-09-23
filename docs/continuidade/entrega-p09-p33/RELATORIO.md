# Entrega local P09–P33 — 22/09/2026

A construção local foi conferida por entidade, dimensão, fato e contrato. A correção adicional demonstrada foi a orientação do pacote P29. Executou-se também diagnóstico PMD offline; não houve motivo técnico para repetir a suíte Java/JDBC. As parcelas reais e operacionais continuam condicionadas às evidências abaixo. As parcelas locais elegíveis estão concluídas. O recibo de fechamento privado registra o readback, a varredura dos bytes finais e a reconciliação dos processos.

## Matriz P09–P33

| Etapa / critério canônico | Alcance local | Real / operacional pendente | Responsável por papel |
| --- | --- | --- | --- |
| P09 / V2-041 | Intake e contenção locais testados;19 contraprovas | Atestado G01 ausente;rotação/invalidação e continuidade não comprovadas | Segurança e Operações |
| P10 / V2-016b | Baseline/workflows/scanners locais existentes | Provider,remote/branch,conjunto aprovado,proteções,owners e CI/Gitleaks reais no SHA | Owner do repositório |
| P11 / V2-015d | Scan técnico/correções e JDBC12.8.2 qualificados;provas íntegras | Aceite nominal da baseline de vulnerabilidades | Segurança |
| P12 / V2-045b, V2-039a, V2-022 | Schema/runtime local e rollback provados;sem nova operação | Ambiente segregado,TLS/ACL,storage/capacidade,retenção,backup/restore/RTO/RPO e efeitos autorizados | DBA,Operações,Segurança,Compliance |
| P13 / V2-025d | Nove contratos Data Export + Usuários GraphQL + Raster versionados | Por fonte:contrato autenticado válido e rodada limitada se houver necessidade;G01 permanece bloqueado;atestado ausente | Fornecedor e owner de dados |
| P14 / V2-009b, V2-009c | Identidade local e recusas existentes;delta de fornecedor não recebido | 8636 raiz;4924 título/filhos;10633 componentes;6392 raiz/ocorrência;Raster parada;fiscal/financeiro | Fornecedor e Negócio |
| P15 / V2-035a | Importadores/releases/vigência e segregação testados | Baseline/export oficial por família e ratificação independente;importação material autorizada | Negócio e owners de referências |
| P16 / V2-009d, V2-029, V2-030, V2-031, V2-032, V2-034b | Onze entradas/mappers/runtime/staging/promoção/DQ/replay construídos e testados | Contrato/identidade/referência efetiva aceita por entidade para confrontar novo delta | Fornecedor e owner da entidade |
| P17 / V2-012a | Caracterização local e oráculos sintéticos independentes testados | Janela representativa,oráculo nominal independente e autorização da fonte/alvo | Owner de dados e Negócio |
| P18 / V2-047 | Planejador/partições/modos/watermarks separados testados | Horizonte,T0/Tcut,partições,fonte histórica,orçamento e autorização;P17 da entidade | Owner de dados,DBA e Operações |
| P19 / V2-046a, V2-046b | Manifesto→Coleta e Coleta→Frete:cardinalidade/órfãos/conflito/replay locais testados | Crosswalk nominal,janelas/histórico e SLA das relações consumidas | Owners de Manifestos,Coletas e Fretes |
| P20 / V2-012b | Comparadores set-based e oráculos independentes testados | Janelas reais fechadas/repetidas:conjuntos/chaves/nulos/status/datas/relações/somas/histórico | Owner de dados e Negócio |
| P21 / V2-035b | Seis dimensões,current/history e consumo local testados | Fonte aceita,grão/papéis/vigência/bindings/ausência/conflito/rekey;IDs estáveis de frota | Fornecedor e owners de dimensões |
| P22 / V2-013 | 33 responsabilidades classificadas:19 BLOCKED,5 DISABLED,9 NOT_APPLICABLE;nenhuma ENABLED | Política/owner nominal,completude,raiz/filhos,confirmações,limites e reativação | Negócio e owner de dados por responsabilidade |
| P23 / V2-013 | Apply sintético Coletas e recusas;universo declarado permanece preview-only por contrato | P22 aceito,snapshot completo e autorização específica de apply real | Negócio,DBA e Operações |
| P24 / V2-036 | Cinco fatos:grão/cardinalidade/idempotência/partições/recomposição locais testados | Paridade das entradas e regras nominais somente usadas pelo fato | Owners dos fatos e Negócio |
| P25 / V2-037 | 19 contratos SQL:18 externos + SQL-10 interno;metadata/linhagem/filtros testados | Manifesto nominal por consumidor,versão/compatibilidade e owner;SQL-10 com aceite interno | Owners consumidores e owner técnico SQL-10 |
| P26 / V2-012c | Oráculos locais set-based;A/B133comparações por cenário reaproveitados | Oráculo nominal por saída e divergências ratificadas;fiscal/somas/relações/labels/filtros | Negócio e owners consumidores/dados |
| P27 / V2-050 | Quatro famílias de escalas locais aprovadas,sem gargalo novo demonstrado | Volume/distribuição representativos,budgets/SLO,ambiente e autorização de campanha | Data Owner,DBA e Operações |
| P28 / V2-038, V2-022 | E2E/agenda/replay/cancelamento/concorrência/recuperação de controle locais testados | Paridades/SLO e provas autorizadas de durabilidade,COMMIT/crash/restore/recuperação | DBA e Operações |
| P29 / V2-039b, V2-015c | P07/P08 reaproveitados;README e envelope documental offline aprovados;PMD2regras aprovado | RC nominal da unidade,CI/segurança/licenças/SOs/configuração e aceites na mesma revisão | Release owner e Segurança |
| P30 / V2-048b, V2-014 | Topologia CUTOVER-DB-01 / DATABASE_WIDE preservada;procedimentos locais existentes | Tcut,writer único,fences,rota/PNR/recuperação e cobertura nominal integral da unidade | Donos de operação,corte e consumidores |
| P31 / V2-048b | Procedimentos e simulações locais existentes;nenhum ensaio material nesta rodada | G06:executores,alvo,janela/orçamento,ensaio e duas recuperações materiais/RTO/RPO | Donos de operação e corte |
| P32 / V2-014 | Gates/procedimento locais;nenhum corte autorizado | G07 nominal/datado,DoD integral,ensaio,backup/rota/abort e primeira publicação aceita | Responsável nominal pelo corte e consumidores |
| P33 / V2-040 | Inventário de responsabilidades e destino projetado;Raster incluído | G08,observação/retenção,cortes aceitos,destinos/owners e autorização retirada/arquivo | Owners do legado e consumidores,Operações |

A dependência vale apenas para a fatia consumida. Ausência não bloqueia universalmente fatos/saídas; SQL-04 exige sua política. Qualificação por entidade não muda o corte DATABASE_WIDE. Raster permanece na responsabilidade final, condicional e desabilitado por padrão.

## Entidades e dimensões

| Entidade | Construção local comprovada | Input real restante |
| --- | --- | --- |
| Usuários | current/history, presença tri-state, snapshot sem incremental inventado; cenário B percorre 24 usuários em páginas 20+4. | Oráculo nominal GraphQL, correspondência escopada, completude do snapshot e paridade current/history/dimensão; histórico exige fonte própria autorizada. |
| Manifestos | raiz, pick e MDF-e com grãos separados, presença/frescor/reducers, composição relacional explícita. | Crosswalk nominal raiz/pick/MDF-e, competência/frota/filial, oráculo e histórico autorizado; órfãos e paridade real. |
| Coletas | presença, frescor, staging/promoção, vínculos e quatro observações de snapshot explícitas; ausência real desligada. | Oráculo independente e snapshot completo; COL-07/MC/temporal nominal, usuários/referências pertinentes; bootstrap e paridade datas/fuso/relações. |
| Fretes | mesma release declarada nos dois caminhos, frescor único, performance e precisão temporal preservadas. | Crosswalk CF, aliases, semântica/referências financeira e fiscal, origem de frescor e paridade real; updated_at não ratificado. |
| Cotações | captura, promoção, presença/frescor e tarifa por release/vigência explícita. | Oráculo do contrato e release tarifária autorizada por rota/vigência/owner, paridade cotação/conversão. |
| Localização de Cargas | identidade corporation_sequence_number, presença/frescor e vínculo com Frete declarado. | Origem nominal do frescor, vínculo minuta/Frete e filial destino, paridade léxica/temporal. |
| Contas a Pagar | 28 campos, raiz/parcela/rateio explícitos, filtro issue_date+created_at, frescor e reducers sem multiplicar montante. | ID tipado da raiz accounting_debit e prova raiz-parcela-rateio; ant_ils_sequence_code não identifica raiz ou linha física. Referência financeira/carteira e paridade do mesmo conjunto. |
| Faturas por Cliente | 53 campos, título/documentos/filhos, fiscal dual e série recebida explicitamente sem fallback. | Entidade/estabilidade do ID da linha, título/documento/Frete e identidade dos filhos; precedência CT-e/NFS-e e fonte própria da série NFS-e. |
| Inventário | 26 campos, filhos físicos, comprovante cumulativo e vínculo explícito; sem identidade por posição. | Identidade raiz/componentes/Frete, significado dos textos, estabilidade sob reordenação/retorno e cardinalidade. ARRAY de STRING já observado em fonte real. |
| Sinistros | 44 campos, sequência/minuta/ocorrência separadas, arrays/reducers e horas brutas preservadas. | Identidade da raiz, papéis/cardinalidades e formato/fuso de customer_communication_time; paridade financeira/temporal. |
| Raster condicional | pai/paradas/ledger, completude local, limite/repartição, histórico/cancelamento e Transit Time; desabilitado por padrão. | Contrato/canal Raster autorizado, tipos/escopo/estabilidade de CodSolicitacao e identidade de parada; Ordem sob reordenação/inserção/remoção, completude/frescor e paridade real. |

Seis dimensões: Filiais, Clientes, Veículos, Motoristas, Plano de Contas e Usuários. Seus bindings/regras nominais continuam em P21; nomes/placas/coocorrência não substituem IDs estáveis. DAG detalhado na matriz JSON.

## Fatos e contratos

| Fato | Dependências próprias | Parcela nominal restante |
| --- | --- | --- |
| MAT-01 Fretes operacional | P20-FRE; P20-LOC; P15-PAGADORES_DOCUMENTOS_FILIAIS | Grao Frete x PE/CB, minuta/ranking, origem de performance e referencias de pagadores/documentos/filiais ratificados |
| MAT-02 Coletores | P20-FRE; P20-MAN; P20-INV10633; P15-ALIASES_FILIAIS | Filial empresarial e fallback MAN/INV por dia/classificacao Geral; identidade Inventario e paridade das entradas |
| MAT-03 Faturamento | P20-FRE; P20-LOC; P20-FAT4924; P15-CALENDARIO_FILIAL | Relacao FAT/FRE, grao financeiro, calendario e atribuicao retroativa de filial nominais |
| MAT-04 Faturas | P20-FAT4924; G03-4924; G04-SEMANTICA | Identidade de titulo/documento, precedencia fiscal e serie NFS-e com fonte propria |
| MAT-05 Manifestos | P20-MAN; P20-COL; P20-FRE; P19-MC; P19-CF; P15-FROTA | Relacoes MC/CF reais, frota/papeis/capacidade nominal e paridade das entradas |

| Contrato | Nome | Público | Dependências próprias |
| --- | --- | --- | --- |
| SQL-01 | vw_faturas_por_cliente_powerbi | Externo | P20-FAT4924; P24-MAT04; G04-SEMANTICA; G04-REFERENCIAS |
| SQL-02 | vw_fretes_powerbi | Externo | P20-FRE; P20-LOC; P24-MAT01 |
| SQL-03 | vw_coletas_powerbi | Externo | P20-COL; P20-USR; G04-ABSENCE |
| SQL-04 | vw_coletas_excluidas_origem | Externo | P20-COL; P22-COL; P23-COL |
| SQL-05 | vw_cotacoes_powerbi | Externo | P20-COT; P15-TARIFAS |
| SQL-06 | vw_contas_a_pagar_powerbi | Externo | P20-CAP8636; G03-8636 |
| SQL-07 | vw_localizacao_cargas_powerbi | Externo | P20-LOC; P20-FRE; G04-REFERENCIAS |
| SQL-08 | vw_manifestos_powerbi | Externo | P20-MAN; P24-MAT05 |
| SQL-09 | vw_fato_manifestos_dash | Externo | P24-MAT05; P19-MC; P19-CF |
| SQL-10 | vw_bi_monitoramento | Interno | G04-INTERNAL-MONITORING |
| SQL-11 | vw_inventario_powerbi | Externo | P20-INV10633; P20-FRE |
| SQL-12 | vw_sinistros_powerbi | Externo | P20-SIN6392; P20-FRE |
| SQL-13 | vw_raster_sm_transit_time | Externo | G03-RASTER; P20-RASTER |
| SQL-14 | vw_dim_filiais | Externo | P21-FILIAIS |
| SQL-15 | vw_dim_clientes | Externo | P21-CLIENTES |
| SQL-16 | vw_dim_veiculos | Externo | P21-VEICULOS; G04-FROTA |
| SQL-17 | vw_dim_motoristas | Externo | P21-MOTORISTAS; G04-FROTA |
| SQL-18 | vw_dim_planocontas | Externo | P21-PLANO_CONTAS; P20-CAP8636 |
| SQL-19 | vw_dim_usuarios | Externo | P20-USR; P21-USUARIOS |

Os19 contratos possuem673 colunas; o cenário confere971 metadados físicos, um universo distinto. Faltam manifesto aprovado de cada uma das18 saídas externas e aceite próprio de SQL-10; referências nominais, regras e oráculos precisam ser aceitos por consumidor.

## Provas, revisão e limites

- Reconciliação:3798 arquivos iguais ao manifesto0233,176 pins conferidos,checkpoint0233 pelo SHA solicitado e ledger anterior CLOSED íntegro.
- P07 reaproveitado:2162 casos unitários/quatro skips históricos;492 integrações/105 classes,zero falhas/erros. Build,Enforcer,Spotless,Checkstyle e JaCoCo aprovados na revisão0233.
- P08 reaproveitado:730 membros/nove componentes,ZIPs idênticos;seis comandos do JAR extraído;A/B sete etapas,133 comparações/231 previews cada;oito variantes,8+21 guardas e25 casos do envelope.
- Subconjunto conferido nesta inspeção:60 XMLs únicos/363 casos aprovados; sobreposições entre agentes deduplicadas. Isso é verificação dos recibos, não nova execução física.
- Executados nesta rodada:ContractOnly G01 (19 contraprovas,exit0) e scanner self-test(18 casos,exit0). Resultado de autenticidade do atestado não avaliado;arquivo ausente por presença,sem ler segredos.
- Nenhum Java,SQL,migration,baseline,POM,dependência,assertion,timeout ou massa alterado. Sem fonte/banco/produção/COMMIT/publicação/CI externo.

## Correções e recuperação

README corrigido: DLL12.8.2; scan técnico realizado distinto de aceite nominal pendente; apoio PACKAGED_ANALYTIC_SUPPORT_V1 restrito à compatibilidade v1,com onze entradas explícitas em v2/v3/SEQUENCE. O envelope documental sucessor é qualificado offline; não se declara novo smoke JDBC ou novo P08 físico.

A reconciliação inicial classificou incorretamente o snapshot inteiro como runtime e reportou quatro diferenças. O manifesto final0233 prova que todas eram suas mudanças documentais já qualificadas; o recibo FAIL inicial foi preservado e a correção de escopo passou. Nenhum defeito funcional adicional foi demonstrado.

Recuperação: snapshots before/ e histórico da sucessão, diff específico da rodada. Nenhum arquivo preexistente apagado,nenhum manifest/ledger antigo reescrito. O pacote0233 continua intacto e sua prova pertence àqueles bytes.

## Inputs externos consolidados

Os16 requisitos anteriores permanecem discriminados na matriz; a ampliação P09–P33 acrescenta recortes já canônicos, sem inventar gates:

| Papel | Artefato mínimo necessário | Critérios |
| --- | --- | --- |
| Segurança/Operações | Atestado G01 sanitizado/autenticado:seis classes,consumidores,invalidação,continuidade writer,recuperação/três scans;Gitleaks aprovado/versionado | P09 |
| Owner repositório | Provider/remote/branch/conjunto aprovado/proteções/owners;autorização e depois recibos CI/Gitleaks por SHA | P10 |
| Segurança | Aceite nominal da baseline técnica e eventuais exceções | P11/P29 |
| Fornecedor/owner dados | Contrato/identidade/crosswalk/completude por fonte;oráculo independente,janelas e histórico autorizados | P13/P14/P17–P20 |
| Negócio/referências | Baselines oficiais por família,versão/proveniência/vigência/grão,cobertura e ratificação segregada;decisões fiscal/frota/rekey | P14/P15/P21/P24 |
| Negócio/owner por responsabilidade | Política de ausência,raiz/filhos/completude/confirmações/reativação;autoridade específica de apply | P22/P23 |
| Owners consumidores | Manifestos18 saídas externas + escopo/owner SQL-10;oráculos/paridade nominal por saída | P25/P26 |
| DBA/Operações/Compliance | Ambiente/TLS/ACL/storage/retencão,budgets/SLO/volume representativo;autorização por efeito e provas backup/restore/durabilidade/RTO/RPO | P12/P27/P28 |
| Release owner/Segurança | RC/configuração/segurança/licenças/SOs e aceites na mesma revisão | P29 |
| Donos operação/corte/consumidores | Tcut/topologia DATABASE_WIDE/rota/fences;G06 ensaio material;G07 corte nominal/datado;G08 observação/retenção/retirada/destinos | P30–P33 |

Não há input nominal novo fornecido nesta sessão. O usuário pediu verificação rápida por testes; essa instrução não atesta eventos externos nem autoriza novos efeitos materiais. Nenhuma mensagem enviada a terceiros.

## Artefatos

- [Matriz por25etapas,11entidades,6dimensões,5fatos e19contratos](matriz.json).
- [Resultado e recibos desta rodada](resultado.json).
- [Checkpoint intermediário0234](../checkpoints/0234-p09-p33-reconciliacao-e-frentes-conferidas.md).
- [Prova P07/P08 anterior](../../catalogos/requalificacao-pos0227/resultado.json).
- [Ordem,provas privadas e recuperação](../../../target/conclusao-p09-p33-20260922-01/WORK.md).

## Fechamento desta execução

PMD7.17.0/plugin3.28.0 já disponíveis:652 arquivos Java analisados,652 execuções de cada regra HardCodedCryptoKey e InsecureCryptoIv,zero violações/erros e zero drift do fonte.1tentativa/15,06s,Java17,Maven offline/settings vazias privadas. Não satisfaz SAST integral:Segurança e release owner ainda precisam definir ferramenta/ruleset versionados,limiar,baseline/exceções,aceitante e RC.

Sucessão:10 contraprovas históricas+15 novas PASS. Tentativas01/02 recusaram um path acentuado porque o child PowerShell usava IBM850;recibo/stack/diagnóstico preservados. UTF8 restrito ao child resolveu a causa;tentativa03 PASS em39,321s,sem mudar módulo após o selo,sem aumentar limite. A trilha integrada passou. Nenhuma falha técnica local conhecida ficou sem tratamento.

Novo ZIP documental:730membros/7941140bytes,duas cópias byte-idênticas. SHA-256 `837ad53e5bb9e21120e65c0d14c117fa28d1fd5dc5c7f792c17b58e681df1546`;manifesto `5465cbedd115bb88cd94a04f1d4f902f18d033a68478494c398bf955ee582962`.725membros preservados;diferenças são README,provenance,três pins de revisão das campanhas e os dois arquivos de envelope. SBOM/extração e JAR inspect+três planos passaram. A/B/run/JDBC não repetidos no envelope novo;provas físicas anteriores pertencem aos bytes antigos e só são reutilizadas para os componentes invariantes.

Não restou trabalho local funcional elegível demonstrado. Os efeitos seguintes exigem inputs/autorização externos já identificados por etapa;nenhum bloqueio de uma entidade foi imposto a outra independente. Release,corte e retirada continuam impedidos pelos gates nominais/materiais,sem redução de escopo ou aceite humano inventado.

- [ZIP documental](../../../target/conclusao-p09-p33-20260922-01/p29-docs-primary-01/qualification.zip).
- [Recibo do pacote](../../../target/conclusao-p09-p33-20260922-01/p29-docs-result.json).
- [PMD executado](../../../target/conclusao-p09-p33-20260922-01/p29-pmd-security-01/result.json).
- [Validações executadas](validacoes.json).
- [Diff da rodada](../../../target/conclusao-p09-p33-20260922-01/delta-final.diff) e [arquivos adicionados](../../../target/conclusao-p09-p33-20260922-01/added-files.json).
- [Checkpoint0235](../checkpoints/0235-p09-p33-entrega-local-conferida.md).
- [Fechamento autoritativo/processos](../../../target/conclusao-p09-p33-20260922-01/closed-receipt.json).
