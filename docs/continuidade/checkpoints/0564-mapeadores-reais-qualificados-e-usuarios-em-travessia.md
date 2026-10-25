# Checkpoint 0564 — Mapeadores reais qualificados; Usuários em travessia

24/10/2026. Sucessor0563;continua a ordem de concluir as quatro etapas antes do
balanço final. Nenhuma conclusão global,publicação ou implantação é inferida.

**Código qualificado.** JAR A49EA79ED41D1E9B444A264CC1C7CF47C73BD968F8153C516325CE3617A98999,
em `quatro-etapas-20261024-01/localizacao-observed-profile/snapshot/target`.
262 testes locais novos nessa qualificação PASS;27 casos Manifestos PASS preservados
e conferidos;45 Fretes anteriores permanecem qualificados em componente não alterado.
Composição334 casos distintos,mais7 IT do pacote atual. Strictcompile,formatter,
Checkstyle,integridade de classes/recursos no JAR PASS. Sem fullverify/cobertura nova.
Falha anterior de relatório por path Windows>260 e falha instrumental do regex com
pipe no cmd preservadas;recuperação usou paths estendidos/seleções individuais.
AST nativo44514 nós/116337 arestas,batch0/sem timeout,sem clustering/LLM.

- ADR0071/RG-FRETE-019:18 presenças tipadas compatíveis com SQL,service_at preservado
  integralmente em payload/evidência;45 testes/7IT do pacote anterior preservados.
- ADR0072/RG-MAN-019:mft_s_n_sequence_code aceita INTEGER nesse path exclusivo e
  preserva o número,sem torná-lo identidade. Decimais/booleanos continuam recusados.
- ADR0073/RG-LOC-019:factory observed20261024 conserva os26 paths observados e a lista
  invoices_mapping limitada. Default17 e seus gates permanecem;nenhuma adoção Main.
- ADR0074/RG-SIN-019:nos2 horários nomeados,aceita ISO offset date-time como relógio
  escrito pela fonte,conservando data/offset completos no bruto;sem conversãoUTC.

**Prova real pós-correções.** Stage4-data-v4 concluiu12 HTTP200,duas passagens
terminais por relatório,per50/per100,página2 vazia. Manifestos315/315 válidos e
Localização37/37 válidos em ambas. Sinistros60 observações por passagem:53 sem
issues e7 com comentário>1024 caracteres;horários passaram,bruto dos7 preservado
sem truncamento (máximo2241,dentro do limite bruto4000). Não ampliar coluna1024
sem contrato/migration. Essas issues são de campo,não perda da observação inteira.
Comparação adicional em Java com BigDecimal/BigInteger confirmou valores exatos
e multiplicidades iguais,em RAM;tokens numéricos brutos de Localização vieram
dos slices originais do JSON. Nenhum payload/ID/digest de negócio salvo em artefato.

Somadas às provas anteriores,os9 Data Export têm leituras terminais nos recortes
declarados:Coletas1,Fretes37,Manifestos315,Cotações287,Localização37,Contas13,
Faturas37,Inventário207,Sinistros60. Cotações usa22/10,Sinistros01–23/10,demais17/10;
Contas usa interseção issue_date/created_at. Não é todo o histórico nem prova global
de chave,grão,exclusão ou completude de entidade fora desses filtros.

**Raster real.** POST único de observação22/10 passou pelo cliente/parser existentes,
HTTP200,108 viagens/355 paradas,limites499/1000. URL montada com a base de serviço
existente,como no V1;primeiro preflight recusado por path incompleto preservado,
zero HTTP naquele preflight. Sem ratificar identidade pai/filho ou terminalidade
global do fornecedor e sem SQL/adoção produtiva.

**Usuários.** Primeira unidade20/80calls fechou após1.220 válidos ao encontrar um
nome recusado;sucessor completou80 páginas/1.600 IDs sem repetição,1.599 válidos,
1 INVALID_NAME_VALUE,sem terminal. Consultas nativas20/1000 com mesma query/filtro
retornaram20 cada,prefixos iguais;query literal exata do V1 também retornou20.
O perfil Main20 não foi ampliado. A comparação literal é diagnóstico read-only
com whitelist exatamente vinculada à query do V1,não aceite pelo parser tipado
que recusa literais;primeiro preparo recusado permanece preservado.
Introspecção read-only2calls confirmou IndividualConnection apenas edges/nodes/
pageInfo,sem contagem total. V1 usa updatedAt por dia;V2 aqui observa snapshot
enabled=true,sem filtro temporal:não comparar seus volumes como escopos iguais.

**EXECUÇÃO ATIVA:** `UsersFullStreaming.ps1 -Mode live`,nova ocorrência independente
dimensionada após conhecer o volume,sem reabrir a unidade80. Artefatos em
`quatro-etapas-20261024-01/users-full-streaming`;classes/plan/JAR qualificados,
origem/credencial existente/3s,uma tentativa,20nodes/call,máximo2000calls/40000nodes/
7200s,10MiB por resposta. Executor original1GiB/125min,uma vaga protegida. Não
iniciar outro batch/SQL/TDS enquanto ele ocupar a vaga. Contagens/últimos10eventos
e ledger técnico somente;IDs/cursors/names permanecem RAM. No checkpoint21:06BRT,
201 páginas/4020 observados,4019 válidos/1nome recusado,sem terminal ou429.
Arquivo process.json identifica exclusivamente o controlador/Java próprios.
Observar o job/receipt antes de qualquer ação dependente;não repetir ou renovar
automaticamente se encerrar em erro/teto. Nenhuma automação/background agendada.

**Banco pendente.** A prova Fretes continua recusada por SQL52123:proc/constraint
somente admitem a marca sintética,mapper preserva DATAEXPORT_6389_SERVICE_DATE_MAPPING.
Não falsificar a marca. Proposta concreta de2 objetos e restauração preservadas
em sql-proposal,zero DDL aplicado. Pergunta assíncrona ao usuário pede exceção
específica à proibição de DDL/Flyway do AGENTS;**sem resposta/aprovação até aqui**.
Não executar mudança dependente. Catálogo completo anterior não contém histórico
Flyway;é proibido inferir adoção ou executar migrate/baseline/repair/clean nele.
Uma aplicação futura deve preservar só os2 objetos autorizados e sua rastreabilidade,
sem bootstrap/novo objeto ou permissão por inferência. Etapa3 NÃO encerrada.

Antes do stream atual:232 chamadas novas nesta campanha (84 Data Export,147 GraphQL
incluindo diagnóstico/metadata,1 Raster),zero429 observado. Controles GraphQL Java
reteram sucesso/gateway,sem status numérico em cada receipt;não inventar HTTP200
onde o status exato não foi retido. SQL permanece5 conexões de0563,nenhuma nova
desde então;nenhum registro de negócio retido,nenhum DDL/migration/cutover/deploy.
Schema/SQL16.0.1000.6/instalação/contas/credenciais/permissões/identidades/ACL/serviços/
listeners/trust/V1/WMS/produção preservados. C78 rollback-only,MainHTTPUNKNOWN,
gates43RAW/53exclusões e a falta de aceite global das identidades permanecem.

1. Observar até o encerramento do stream e ler contagens/terminal/receipt reais.
2. Reconciliar coorte atual,código/JAR e documentação;nenhuma atualização falsa por início.
3. Com decisão humana específica,qualificar e testar a alteração SQL proposta em
   rollback,validar adoção somente se coberta,e repetir fonte→staging→readback→rollback.
4. Etapa4 ainda exige captura/banco e identidade por entidade,adoção de releases
   observados em consumidores existentes e limites de Raster;leitura/mapeamento
   exitosos não encerram esses critérios. Concluir independentes sem encobrir bloqueios.
