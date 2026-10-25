# Checkpoint 0569 — versão real do binding e captura observada em preparo

24/10/2026, 23:59 BRT. Sucessor de 0568. As quatro etapas continuam ativas;
etapas 3/4 não estão aceitas. Não houve resposta humana à pergunta específica
de exceção DDL dos dois objetos de Fretes. Não interpretar delegação como DDL.

## Correção factual prioritária

A revisão do chat Fluxo identificou um erro na orientação anterior do Root e
nas propostas SQL: `source-contract-release-v1` é o domínio interno usado pelo
encoder de ContractCanonicalizer.release, não a versão retornada no fingerprint.
O método termina com `encoder.finish(contractVersion)` (fonte: linhas 90–106).
ContractExecutionBinding conserva release.contractFingerprint; o pedido principal
usa esse mesmo objeto em RuntimeWorkloadDefinition e ControlPlaneStart;
JdbcSqlServerControlPlane.startExecution vincula start.contract().version() ao
parâmetro 11 e seu SHA ao 12 (linhas 73–87). Logo o valor esperado no parâmetro
contract_version de ctl.usp_control_plane_start_execution é o subject real do release:

| Template | Versão do fingerprint / parâmetro contract_version |
|---|---|
|6389|2026-10-24.fretes-observed-curl.v1|
|6399|2026-10-24.p1b-manifestos-observed.v1|
|6906|2026-10-24.p1b-cotacoes-observed.v1|
|8656|2026-10-24.p1b-localizacao-cargas-observed.v1|

Esta cadeia de código foi revista pelo Root; falta uma prova offline específica
dos binds com os bytes qualificados219D. Nenhum SELECT novo do ctl foi feito.
Os hashes dos releases não mudam. O candidato Fluxo conserva a comparação exata
ao subject; a orientação Root de substituí-la pelo algoritmo foi retirada antes
de adoção. A revisão não muda a implementação de ContractCanonicalizer.

**Propostas Fretesv4 e Local26v1 não são elegíveis para adoção:** exigem versão
incompatível com o caller real. Banco recebeu a preparação de sucessoras v5 e
Local26v2, preservando originais, predicados, limites e superfície. As 37 provas
SQL readonly anteriores avaliaram corretamente os predicadosv4 com seus vetores;
não qualificam a tupla real do ETL. Não apagar esses resultados nem transformá-los
em execução da procedure. O v6 offline não havia afirmado readback37 e não
exercitou esse vínculo do procedimento. Nenhum ALTER foi aplicado.

## Entregas externas e execução em andamento

Fluxo mantém o executor protegido original, teto2GiB/limites finitos, para
candidato P1B/P2. Houve falhas de montagem dos testes e recuperação local;
**nenhum candidato novo foi aceito pelo Supervisor**. Não adotar src intermediário
ou declarar pacote qualificado antes da entrega/relatório final. Root só faz
leitura, preparação textual e documentação enquanto a vaga está ocupada.

Local26v1: revisão estática encontrou somente a procedure
stg.usp_stage_localizacao_carga_record como superfície necessária; a tabela JSON
e os nove CHECKs existentes não fixam17. Propõe ramo26 fechado, legado17
preservado, sem coluna nova; 62 casos propostos e nenhum executado. Ainda requer
correção de versão acima, revisão independente, qualificação e autorização
específica (a pergunta Fretes não cobre Localização). Diferença herdada nos
decimais com muitos zeros à esquerda permanece declarada, sem alegar ocorrência
nos37 reais. Entrega no diretório irmão delegacao-revisao-banco-20261024-01.

P3 estático concluído pelo chat Contratos, em p3-captura-observada: duas tabelas
novas ctl.observed_export_capture e stg.observed_export_page e três procedimentos
begin/append/seal. É proposta, zero DDL/SQL/testes. Preserva bytes HTTP completos,
multiplicidade, ocorrências globais técnicas, UNBOUND e raw/typed/issue dos quatro
mappers. Não pode usar o laboratório sintético existente como se fosse fonte real.
Cinco objetos precisam de decisão própria; nenhum está autorizado pela pergunta
dos dois objetos Fretes. A projeção não concede identidade/publicação/completude.

Contratos agora prepara candidato externo somente do core/modelo/projetor,
sem transporte/JDBC/DDL e inicialmente sem executar build. Banco prioriza a
correção das propostas de versão; depois prepara helpers externos separados
Manifestos/Cotações, investigando caminho de staging sem DDL existente. Não
presumir bloqueio desses dois relatórios pelo erro específico de Fretes.

## Estado preservado e próximos passos

JAR219D408FE35E4A95A36F6FA7B740000A8EDE640676AA47C3111EC34FC86E4AAB
permanece o último qualificado, sem implantação. As58provas/7IT e378casos por
composição conservam seu escopo; não somar recuperações/469checks comoJUnit.
AST44514/116337 é antigo e só será atualizado após a integração final.

Fonte1185HTTP/1186reservas (DataExport88,GraphQL1096,Raster1), SQL8conexões
encerradas,0DDL/0commit retido. Usuários18.980observados/18.978semrecusa/2nomes
recusados, semEOF/cursor recuperável; observador sucessor qualificado offline,
sem nova chamada. Raster108viagens/355paradas é recorte, sem aceite global.

Receber Fluxo e revisar seus bytes; qualificar binds reais offline; revisar as
sucessoras SQL sem aplicar; preparar as provas elegíveis de Manifestos/Cotações
e o coreP3; só avançar DDL após resposta humana que cubra a superfície exata.
Readback37Fretes, captura física das demais verticais, identidade/retentiva/
snapshot/paridade/cutover continuam abertos. Não emitir conclusão das4etapas.

Preservar SQL16.0.1000.6/instalação, contas/credenciais/permissões/identidades/
ACL/elevação, serviços/listeners/TLSglobal,V1/WMS/produção. C78rollback-only,
43RAW/53exclusões e MainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED mantidos.
