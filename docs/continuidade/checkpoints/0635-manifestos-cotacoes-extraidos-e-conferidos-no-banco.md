# 0635 — Manifestos e Cotações extraídos e conferidos no banco

Em27/10/2026, Root concluiu duas unidades reais pelo gateway do JAR153, com
retenção privada da Source interpretada, ocorrências e ResultSet real. Não é
retenção do HTTP bruto ou dos setters JDBC. São recortes locais, não todo o histórico.

Manifestos6399,17/10/2026 BRT,per50 grupos:315 linhas físicas/17 chaves/4 batches,
páginas315/0;3 HTTP reservados e concluídos. Original0,child0,controller/nativeCLOSED,
50220ms/747724800bytes.315 Source=315 staged=315 readback tipado=315 registros
privados e mappings. ProvaA28A4CBC54E0EE9C641FAA2B09946C4432CEA530E68F4F066C5FA7D22BDCCF52.
Comparador privado independente executado Original0:315 linhas,6300 células,
29956 nós JSON Source e61458 nós derivados,zero diferenças.945 células SQL e
16380 ocorrências de campos tipados sem verificação; payload Source interpretado
inteiro comparado separadamente. Prova00AA493ECA9C029D48DE1B586E60B92F1AFFCE725ADC8306B688A72874805F76.

Cotações6906,22/10/2026 BRT,per100:287 linhas/285 chaves/3 batches,
páginas100/100/87/0;5 HTTP reservados e concluídos. Original0,child0,
controller/nativeCLOSED,54205ms/765104128bytes.287 Source=287 staged=287 readback
tipado=287 registros privados e mappings. Duplicidades de chave conservadas.
ProvaF478706E7927DE012A5947DE9DC29F66CD7BDA0B5D67736DEB02A241FC4B1CB5.
Comparador independente Original0:287 linhas,6888 células,10906 nós JSON Source
e13776 derivados,zero diferenças.287 células SQL e8036 ocorrências de campos
tipados sem verificação; sem alegação de todos os campos verificados.
Prova0A21A5F13CCD659BD3F6E62B128845F03B36B311B178055BEDE7FE328EB9657F.

Em cada unidade:2 STRICT/74 SELECTs/14 tabelas união/11 locks próprios,
rollback original+rollback de leitura=2 ACKs,10 POST SELECTs/ausência8;
conexão/child/stdin/socket fechados, SQL16.0.1000.6 inalterado. Nenhum DDL,
gravação de negócio retida ou promoção. Os315+287=602 registros e13188 células
comparadas sem diferenças não demonstram paridade externa ESL/Excel.

Última Source agora Cotações/F478; ledger acrescentou apenas
source0634AdditionalConfirmedAdapterReservations=3/lastActualSourcePair0634 e
source0635AdditionalConfirmedAdapterReservations=5/lastActualSourcePair0635.
Históricos preservados; sourceCountersFinal=false/unitCountersClosedKnown=true.
Não resetar contadores globais nem usar Coletas0626 como último par atual.

Sourcehelpers v4/0443393A aprovados independentemente73171288 e executados.
Três recusas anteriores conservadas:01 colisão PS Profile/profile;02 tipo UInt32
da memória rejeitado por teste UInt64;03 lease externo do próprio ledger causou
self-lock antes do Native/OriginalSource/HTTP/SQL. Correções isoladas preservaram
Core40E,wrapper3A5F,limites,hashes e predecessores. V4 entrega lease ao Native,
e reconciliador libera somente o arquivo mutável para CAS/Replace/readback.
Sem repetir unidades consumidas ou renovar janelas automaticamente.

Comparador v1 recusado antes da execução por fingerprint Coletas;v2 corrigido
fechou Original2 antesJava por caminhos classpath>259 e LongPathsEnabled=0.
Diagnóstico finito real preservado. V3/B9C08365 usa caminho estendido somente
na leitura/hash dos Core.class pinados, preservando caminhos públicos/normalização/
reparse/UNC/ADS e reader anterior. Own4 real Original0/6472A886; ambas comparações
reais acima Original0. Falhas não convertidas em PASS.

MFTOwn3Java+PS3/FP completos anteriores preservados. COTOwn3Java+PS3 real
A81E7ED7 e FP147/1334/322/3/26 real634519D7; sem repetir Core322/Main7/200/624.
Capturas prévias à adoção68 agora concluídas. Raster ainda aguarda unidade nova
de um POST para projeção14campos;own6/path3/counter1 reais preservados. Excel636
continua sem chave/entidade/filtro equivalentes comprovados, ESL login/2FA pendente.
Preparação readonly17 nomes de constraints e confronto Raster/Excel em revisão.
Adoção68/AST,UsersEOF,demais bancos,retenção permanente e fidelidade externa/
etapas3/4 continuam abertos. Proibição permanente DDL/grants/Flyway preservada;
PM2/V1/produção/credenciais intactos. Root único executor;commit e push.
