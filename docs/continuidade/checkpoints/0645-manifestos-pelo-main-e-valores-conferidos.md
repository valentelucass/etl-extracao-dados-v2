# 0645 — Manifestos pelo Main e valores conferidos

27/10/2026. A unidade `root-main-manifestos-0645-01` executou o Main verdadeiro
do V2 com o JAR A98F, extraiu o recorte de Manifestos Data Export de 17/10,
gravou temporariamente, leu os registros de volta e concluiu rollback e fechamento.
Uma comparação independente dos valores selecionados terminou sem divergências.
Isso fecha esta unidade de teste; não é carga permanente nem validação integral
de todas as entidades ou do histórico completo.

## Resultado real da extração e banco

- Relatório6399, 315 linhas físicas, 17 chaves distintas e quatro batches.
- Três requisições reservadas e três concluídas; fim da paginação observado.
- Source315 = staging315 = ResultSet315 = captura privada315.
- Main invocado e retornado comprovadamente pelo controller e pela evidência
  física, com produto A98F e Core44AE. A flag resumida da CLI não foi usada como
  autoridade para as contagens ou para provar a invocação.
- Uma conexão SQL, 74 verificações STRICT, footprint de 14 tabelas, 11 locks,
  dois rollbacks confirmados, dez consultas pós-rollback e oito ausências verificadas.
- Conexão, child, stdin e socket fechados; SQL16.0.1000.6 e identidade nativa
  preservados. Zero DDL, commit retido ou retry.
- Original0/child0/Native0; 43.489 ms e pico de 757.309.440 bytes, sob 1 GiB.

Prova reconciliada pelo Root:
`RootMainMftSourceBankQualification-root-main-manifestos-0645-01.json`, SHA
`36DF1D507A04B3A9E1B2FDEDD70D0467266245DE287A67027270432241F476A9`.
Reconciliador B852 executado pelo caller1DDE, ambos previamente revisados:
Original0/15.427 ms/137.871.360 bytes. Nenhuma chamada de origem ou SQL foi
feita pela reconciliação. O CAS acrescentou somente as duas folhas Source064501:
três reservas e três conclusões conhecidas e o novo par real da unidade.
Os históricos 5931/5932 permanecem intactos e não finais.

Auditoria independente somente de arquivos técnicos:
`main-mft-actual-technical-audit-files-only-0645-v1/review.json`, SHA
`2AFE82717CF060893FAEA74639EA869DAAC36DAF9A7B8E59899CE461A47096FB`.
Conferiu 18 referências, 14 frames, os joins Main/Core/produto/unidade/ocorrência,
os fechamentos e 26 folhas históricas Source sem alteração. Não leu payloads.

## Comparação dos valores

O comparador D25 já qualificado foi executado sobre a nova captura Main fechada,
sem novas chamadas à API ou SQL. Resultado real:

- 315 linhas comparadas por execução, batch e ordinal, preservando duplicatas.
- 6.300 células selecionadas comparadas, zero divergências.
- 29.956 nós do JSON interpretado da origem e 61.458 nós de JSON derivado
  comparados, zero divergências.
- Original0/8.461 ms, pico de 453.967.872 bytes.

Prova `main-mft-private-comparison-preparation0642-v2/root-execution/main-mft-actual0645-01/qualification.json`,
SHA `7460B8F69CE720EFABA1A76F93E69268DDF404A775BCF1906D9956BC9B173D9A`.

Limites explícitos: 945 células SQL e 16.380 campos tipados da origem continuam
UNVERIFIED conforme o contrato do comparador. Os campos brutos desconhecidos
continuam preservados no payload comparado; isso não cria uma regra tipada para
eles. bindingCoverage=NOT_CAPTURED, sentJdbcBindingsCompared=false,
rawHttpBytesCaptured=false, allFieldsVerified=false e externalEslOrExcelParityProven=false.
Não declarar comparação de todos os setters JDBC, bytes HTTP brutos, relógios
gerados, campos SQL não selecionados ou paridade com a plataforma/Excel.

O runtime independente de projeção continua Core40E/JAR153; o Source Main usou
Core44/A98. A ponte ZIP/classes já qualificada demonstra a composição de Main2,
sem transferir uma qualificação física antiga. A98 pertence ao cohort anterior
à adoção canônica dos quatro HTTP; esta execução não qualifica automaticamente
todo o código canônico pós68.

## Preparo e causas encerradas nesta unidade

Root selecionou explicitamente seis tuplas atuais com 70076 e treze ausências,
sem fallback, retag ou parada de PM2. Native0645/64FA/90DBC passou NOVOS
42 modelos, quatro casos de mutex e Capture6: Original0, dez amostras,
25 aquisições/liberações e zero erros. Prova5490, OriginalEBD3, baselineA297,
reportE726 e seleçãoE72 são atuais; nenhuma captura anterior foi herdada.

MainED3/72candidatos+518externos passou novos3+5+inspect:
Original0/13.020 ms/492.019.712 bytes, provaB8B3. PreparaçãoB356 foi revisada1DF7,
PrepareSpec45FA e FilePreflightF4D7 passaram; perfil28C0 mediu 8.812 ms no leitor
completo. Core44/A98, proveniência dos quatro beforeimages HTTP, hashes/leases e
limites30/345/360/120 permaneceram preservados.

A correção Int32 ou UInt32 dos dois predicados do recibo, comprovada pelos
quatro causais13947 e por ponte de igualdade literal, participou agora de uma
emissão real concluída. Compilação, review do emissor e dispatch foram concluídos.
O caminho técnico do review pessoal foi escolhido corretamente no B. Unidade,
UUID, CODE e janela foram novos e não renovados. As tentativas0644-01/02/03
continuam aposentadas, com suas falhas preservadas.

## Continuidade necessária

Última Source real agora é Main Manifestos0645-01, com prova36DF e comparação7460.
Raster0637 permanece histórica. Todos os specs antigos que ainda apontam
LatestRaster devem recusar antes de novo efeito. Futuro escopo exige tratamento
tipado de LatestMain e composição própria para Cotações/Coletas; não reutilizar
o emissor ou a janela fechados nem disparar outra Source automaticamente.

Os nove Data Export continuam com término nos recortes registrados. Coletas,
Manifestos e Cotações mantêm provas de banco com rollback; agora Manifestos tem
também prova explícita pelo Main. Fretes Data Export permanece separado de Fretes
GraphQL. Demais bancos, Usuários até EOF, estruturas, carga permanente e
comparação externa integral continuam abertos. A comparação anterior de 108
códigos/placas e previsões com Excel é parcial; os outros 528 não são faltantes
comprovados. Login ESL continua pendente, sem alteração do site.

Progressive Users/P3 continua Original1 sem causa interna retida; diagnóstico3E76
foi revisado350BB, mas não executado. Caller ainda WIP. Adapter, catálogo, guard
e engine são pendências internas; as 18SQL dos quatro grupos e as sete DDL Raster
continuam separadas e não executadas, sob as proibições vigentes.

Root foi o único executor de Original/API/SQL, editor canônico e responsável por
Git; os agentes prepararam/revisaram arquivos. SQL16, contas, credenciais,
permissões, serviços, listeners, TLS global, V1, PM2 e produção foram preservados.
Runtime canônico não mudou. Documentação0644 já publicada no be6afc3; esta
atualização requer scan dos quatro textos, runtime1779 inalterado e commit/push.
