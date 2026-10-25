# Checkpoint 0574 — Raster medido, catálogo qualificado e integração em testes

25/10/2026. Sucessor de0573. Etapas 3 e 4 abertas. Nenhuma extração global,
retenção ou implantação aceita por estes resultados.

## Usuários encerrado

O fechamento de0573 permanece: 2.000 chamadas, 40.000 registros observados,
39.997 válidos e três quarentenas; hasNextPage=true. O teto local interrompeu a
leitura, sem EOF. Recibo original e releitura integral de8.001 snapshots confirmados.
Não há cursor persistido nem retomada automática. A ocorrência anterior949/18.980
é distinta. RootUsersClosureReview-20261025.json fixa as evidências originais.

Revisão somente leitura de V1/V2 confirmou individual/IndividualInput, enabled=true,
id/name/pageInfo. V1 atual acrescenta updatedAt pela janela planejada; não envia
type=UserIndividual. A ausência de tempo no V2 é deliberada no snapshot BACKFILL/
REPLAY (ADR0019 e contrato local). Não acrescentar filtro para aparentar completude.
Nova candidata externa de até5.000 chamadas/100.000 nós/5h está somente em preparo,
com orçamento conservador de RAM, leitor integral e qualificação próprios. Nenhuma
nova chamada de Usuários ou orçamento operacional foi liberado.

## Raster: prova nova e problemas encontrados

raster-field-observation-candidate passou16 cenários offline reais; recibo original
exit0/sem timeout/8.647ms. Nova unidade live-10250001 fez um POST do dia22/10 e
fechou HTTP200/108 viagens/355 paradas; recibo14.630ms, pico278.372.352bytes sob1GiB.
Os49 atributos foram medidos,46 têm valores. Sequencial e códigos IBGE de origem/
destino não apareceram. Todas108 viagens passaram pelo parser;230 das355 paradas
têm issues: latitude227 e longitude229. Não atribuir causa sem nova prova causal.
Posição final tem355 coordenadas sem issue. Candidatos de raiz108 distintos/zero
duplicatas; (pai,Ordem)339 distintos,16 repetições e multiplicidade máxima4.
Isso não aceita identidade global nem completude da fonte. Contratos investiga o
parser/precisão e prepara estatísticas de causa em RAM, sem arredondamento silencioso.
RootRasterFieldClosureReview-20261025.json fixa recibo/closure/hash; nenhum valor,
ID, nome, coordenada ou digest de negócio foi persistido nesta observação.

## Integração P3 e testes preservados

Produto E05 permanece150JUnit+7IT, sem implantação. Root P3q01 falhou na compilação
principal: blank final local em construtor compacto. Sucessor mínimo usa helper
privado com as mesmas aridades28/53/26/44 e todos os limites antes da cópia.
q02 compilou o principal e falhou por importação assertFalse ausente em teste;
sucessor acrescenta somente a importação. q03 compilou774 principais/698 testes
e executou419JUnit: uma falha, zero erros/skips. A revisão independente demonstrou
que INFO_429 é o resultado específico correto; STOP ocorre antes de headers/corpo,
not-a-delay é ignorado e não existe segundo envio. Sucessor do teste exige INFO_429
e flag STOP imediata, preservando recusa de novo driver e requests==1.

Coorte atual p3-root-cohort-v5.json:
1CBBE1C623C105FA207CE8AB7439AD7D201050BC693936E5F9A17DDF86735F98.
44 overlays,186 pins,40 classes selecionadas. q04 em execução no único executor
Root (sessão87151,2GiB/15min). Não iniciar outro Java/Maven/SQL/fonte/índice até
recibo e fechamento. Não há PASS de q04, pacote P3, adoção ou integridade7IT novos.
Frozéns dos autores e todas as três rodadas falhas permanecem preservados.

## Banco e próximos passos

Catálogo V3 22F7816E8A2537552F6BB25254584B8FB8FE2F871E376197E3D583AC54999AE7
passou44 grupos offline reais, exit0/sem timeout/9.665ms, pico356.126.720bytes sob1GiB.
Nenhuma SQL/provider/observação nativa nessa qualificação. A unidade física futura
continua restrita a35SELECT/13tabelas/13módulos; sem fetch adicional de corpos,
DDL/DML ou pin positivo por JSON. Trial precisa reler contexto/locks na sua conexão.

Auxiliar E05Bv2 falhou na compilação do callback Iterable. v3 corrigiu e executou
85 cenários aprovados, mas a verificação posterior recusou logs/etl-dataexport-v2.log
criado fora de runs; exit1 e candidato NÃO qualificado. v4 causal está em preparo,
sem retro-selar a rodada anterior. Composição física Manifestos/Cotações continua
pendente de qualificação e catálogo atuais. Grade readonly37Fretes/60Local tem
runner18JUnit preparado, zero executado; composição física separada em construção.
JDBC P3 tem40 testes Fake preparados, dependentes do pacote P3 qualificado real.

Contadores reconciliados:3.193HTTP=95DataExport+3.096GraphQL+2Raster;
3.194 intenções locais. Banco mantém8 conexões fechadas, zeroDDL e zero commit
de negócio retido. Exceção Fretes2objetos sem resposta humana; Local26 e P3cinco
objetos são escopos distintos. C78 rollback-only e todas as restrições permanentes
de SQL16.0.1000.6/instalação/contas/identidades/ACL/permissões/serviços/listeners/
TLSglobal/V1/WMS/produção preservadas.43RAW/53exclusões e MainHTTPUNKNOWN/
MISSING_NOT_RECONSTRUCTED intactos. AST44514/116337 aguarda integração final.
