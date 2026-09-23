# Checkpoint0237 — reconciliação e contraprovas JDBC

Data UTC:2026-09-22T04:52:13.964Z. Anterior:0236-avanco-seguranca-local.md;SHA25651ef973de906f3144a63cfe5c85580dcd2242b4424f3b1ac3f7f2ab35e9f4ffa. Estado EM_EXECUCAO;nenhum P novo aceito.

Objetivo efetivo: concluir P09–P33 e requalificar P07/P08. Anexo fd173c60-bdaf-4b05-a5bb-66191a276b32 e reforços atuais adotados; não autorizam produção,credencial,publicação,corte ou aceite nominal inventado. Base3841arquivos sem drift e recibo0236 CLOSED conferidos. Inventário/cópias:target/qualificacao-p07-p33-20260922-01/initial.json e before/.

RED executado em três tentativas locais:Raster25/12falhas;Coleta/Bounded59/36falhas;LaboratorySqlBudget4/1falha,sem erro/skip. Fonte/limites/esperados constam nos requests pinados em checkpoint-0237-pins.json. Cada Maven600s,Java17/512MiB,sem perfil físico/SQL/rede externa. Rasterwrapper parou posteriormente em QUAL_CONCURRENT_SOURCE_CHANGE;resultado unitário não foi perdido. Snapshot budget01 recusou mudança concorrente antes de Maven;02 derivado do before mais teste explícito. Falhas preservadas,nenhuma é escondida por GREEN.

Fontes corrigidos:JdbcRasterBatch,ColetaTemporalLaboratorySession,BoundedRuntimeDataSource,LaboratorySqlBudget;LocalAnalyticQuotesRuntime ganhou alias local legível da mesma exceção. Novas regressões e provas de sanitização/ownership em execução na tentativa pos0236-security-green-01;resultado ainda pendente. ADR0054 e disposição PMD técnica separam relatório bruto de revisão específica;V2-015c/nominal permanece aberto. Matriz privada possui74linhas/25etapas+11entidades+8referências+6dimensões+5fatos+19contratos,sem promoção de preparação a implementação.

Ordem física nova:physical/authority.json+ledger.json,43200s/24h,teto7200s porVerifyPhysical,duas tentativas integrais/duas corretivas,rede0,alvolocalhost/ETL_SISTEMA_V2_SHADOW integrado,sintéticos/rollback,semDDL/commit. Ledger sem reservas nesta fotografia. Nenhum efeito desconhecido deSQL;processos próprios se reconciliam pelos process.json das tentativas. Recuperação:before/snapshots;sem reset,limpeza ou alteração de terceiros.

Próximas ações:1.observar GREEN e corrigir eventual falha local,conferir formatter/PMD/disposições;2.pré-flight reservado e P07físico dos fontes finais,antesP08;3.pacote completo,matriz,evidências/sucessão/scanner e entrega consolidada. Dependências externas por fatia permanecem em matrix-audit.json;39/45 e67/115 intactos.
