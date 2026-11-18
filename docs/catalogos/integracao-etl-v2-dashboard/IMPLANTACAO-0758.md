# Implantação segregada — unidade 0758 em andamento

**Estado mais recente:** fundação V001–V009 e view operacional instaladas; 77 tabelas, diário real com dez passos, sem dados reais de negócio. JDBC13.4/Java17 confirmou os 26 objetos atuais, 958 nomes de campos e 26 rotas originais para V1. Correção de conciliação e driver do próximo build do dashboard preparados/testados, sem deploy. Ler os complementos abaixo e [ADR 0078](../../adr/0078-banco-produtivo-segregado-e-acesso-dedicado.md). As quatro etapas solicitadas ainda não estão integralmente concluídas.

## Autoridade atual e proteção do V1

O usuário autorizou criar e validar `ETL_SISTEMA_V2`, testar recebimento e recuperação reais, integrar a execução de 60 minutos com progresso durável e validar o consumidor/preparar corte e retorno. Em resposta direta à proposta de acesso, autorizou expressamente a exceção: **“Autorizar a exceção proposta”**. Depois reforçou que estará ausente e que a comunicação atual dashboard/V1 deve ser preservada. Não repetir a pergunta já respondida.

A exceção substitui a proibição histórica apenas para o login novo `etl_v2_runtime_prod` e os usuários/roles/permissões delimitados ao banco novo. Preservar as demais identidades, senhas, acessos dos bancos antigos, Windows/ACLs, TLS, serviços, versão/edição SQL. O dashboard continua na rota atual. Não executar `iniciar-prod.bat`, trocar sinônimos/configuração produtiva, parar V1 ou simular publicação pronta.

## Efeitos físicos confirmados

- Banco separado criado e validado: SQL Server existente 2022 Standard `16.0.1000.6`; collation `Latin1_General_100_CI_AS_SC`, compatibilidade 160, FULL, RCSI, CHECKSUM; crescimento fixo 64 MiB. Recibo `criacao-banco0758-attempt2.json`.
- Fundação V001 e V002 originais, hashes conferidos, instaladas em transação no banco novo. Sete schemas pertencem ao owner sem login `v2_schema_owner`; seis roles preservam o desenho original. Instalação manual registrada em `ctl.production_installation_step`; **não foi criado ou falsificado histórico Flyway**.
- Login SQL novo `etl_v2_runtime_prod`, senha nova protegida por DPAPI CurrentUser fora dos repositórios, sem alteração de ACL. Usuário apenas no novo banco, membro apenas de `v2_runtime`. Conexão nova real: sem sysadmin/dbcreator/db_owner/CREATE TABLE/escrita direta em core; sem acesso a ETL_SISTEMA/DASHBOARDS. Recibo `seguranca-instalada0758.json`.
- Login existente `usuario_etl` mapeado no banco novo com CONNECT e SELECT em `pub`. Teste real com a credencial pertencente ao dashboard: conecta em DASHBOARDS como antes; conecta no novo banco sem db_owner/DDL/escrita core/stg. Nenhuma senha copiada/persistida. Recibo `acesso-dashboard-pos-seguranca0758.json`.
- Catálogo de principais/roles/permissões do servidor, excluindo somente o login novo aprovado, e atributos dos bancos protegidos conferidos antes/depois: preservados. O grant novo não muda os sinônimos nem o destino consumido pelo dashboard.

## Qualificação executada

Bootstrap: dez casos offline (parser SQL160, CREATE dinâmico e recusas de adulteração), mais criação/validação física. Segurança: sete casos offline (parser dos dois SQL originais e cinco adulterações recusadas antes de credencial/SQL), inventário de leitura, instalação e autenticação efetiva das duas identidades. Recibos de memória em `work`, sob o executor compartilhado de 2 GiB. Falhas/tentativas anteriores preservadas.

## Limites e trabalho restante

O banco contém fundação/segurança e o diário real de instalação. Ainda não contém as entidades e publicações produtivas. Acesso aprovado não equivale a recebimento qualificado. A enumeração atual de ambientes admite exclusivamente sombra, o runtime independente admite dois perfis e a entrega atual termina em artefato local, sem SQL. Existem ainda 30 CHECKs sintéticos e V105/V106 com alvo SHADOW exclusivo. Essas recusas devem permanecer intactas; não aplicar 106 scripts recursivamente nem remover travas em massa.

Prosseguir com contrato produtivo explícito, preservando mappers/extração já qualificados; testes reais de recebimento e falha; agendador externo condicionado à qualificação; progresso contínuo por entidade; contratos e referências do dashboard, comparação e ensaio de retorno. A troca real não está autorizada por inferência. P1 estrutura completa e P2–P6 continuam em andamento/pendentes, sem declaração de prontidão.

Evidências e autorização: pasta `C:/Users/suporte/Documents/Codex/2026-11-08/etl-v2-sql-server-dashboard/outputs`. Proposta revisada e autorização direta preservadas em arquivos distintos, sem editar retroativamente a proposta.

## Complemento — fundação compartilhada e falha física controlada

V003–V009 originais foram instaladas em uma transação, com manifesto próprio e parsing SQL160 prévio. Controles de execução, staging genérico, ciclo de vida, observabilidade, Users e referências: 75 tabelas esperadas verificadas, 77 no total incluindo allowlist/diário. CHECKs/FKs habilitados/confiáveis e execuções vazias. A identidade dedicada executou o health entrypoint real. Recibos `fundacao-pacote0758.json` e `fundacao-instalada0758.json`.

`controle-sql-falha-rollback0758.json`: seis casos físicos aprovados. Duas chamadas de registro idempotente, conflito de espécie recusado com SQL51301, rollback, transação encerrada, SELECT livre em ctl recusado com SQL229 e readback administrativo independente sem linha de teste restante. Esse ensaio usa metadados sintéticos declarados, sem HTTP de origem nem dados reais das entidades. Não substitui P2/P3 ou a recuperação física de períodos.

## Complemento — consumidor preservado e próximo build

O driver atual 12.4.2 recusou o certificado no Java17; os recibos das duas tentativas estão preservados. A versão 13.4.0.jre11 já instalada passou com TLS validado; fonte oficial de implementação apresenta diferença no parsing seguro do CN ([Microsoft13.4](https://github.com/microsoft/mssql-jdbc/blob/v13.4.0/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerCertificateUtils.java), [Microsoft12.4](https://github.com/microsoft/mssql-jdbc/blob/v12.4.2/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerCertificateUtils.java)). A ligação causal exata do formato do CN local não foi ensaiada isoladamente; não a declarar comprovada apenas por esse diff.

O POM do dashboard agora fixa 13.4.0.jre11 para o próximo build; 44 testes backend passaram após a mudança. O teste físico `jdbc-real-dashboard-v1-v2-0758-attempt4.json` usou o artefato Maven13.4, conferiu os 26 objetos e todos os958 nomes de campos atuais, os 26 synonyms originais, a leitura da view de instalação no banco novo e a identidade dedicada sem privilégio elevado. Não foi alterado o POM/driver C78 do V2 ou a configuração produtiva atual do dashboard.

Correção da conciliação aplicada no fonte e explicação do KPI: igualdade positiva normalizada; nove casos SQL aprovados. Backend44, TypeScript/lint/encoding e frontend63suítes/401casos aprovados. `testes-dashboard0758-attempt2.json`, `testes-dashboard0758-attempt3.json`, `conciliacao-sql-real0758.json`. Produção não foi reiniciada/implantada.

## Complemento — preparação de corte e retorno

`pacote-corte-retorno0758.json` contém 26 rotas e958 contratos de coluna. Templates `corte-dashboard0758.sql.template` e `retorno-dashboard0758.sql.template` incluem alvo, transação, lock, drift, verificação de objetos/colunas e recusa de permissões explícitas sobre synonyms para não descartá-las silenciosamente. Ambos contêm **THROW incondicional** e não são executáveis nem migrations descobertas automaticamente. Parsing SQL160 aprovado; não houve ensaio físico, aplicação de template ou corte. A futura migration executável pertence ao Flyway do dashboard, com baseline correspondente, depois de qualificação e aprovação próprias.

## Situação dos quatro marcos

1. **Banco criado/validado; fundação compartilhada e acessos instalados.** Schema produtivo completo ainda depende dos contratos de domínio posteriores.
2. **Recebimento real e recuperação física: pendentes.** Nenhum dado real foi carregado no banco novo; os seis casos de falha são do controle SQL, e os78 testes0757 são offline.
3. **Execução automática60min/progresso produtivo por entidade: pendentes.** Não foi criado ou ativado agendador para um runtime que ainda recusa o alvo; regra D-7..D0/oito datas e recuperação além dessa janela preservadas.
4. **Comunicação atual/infraestrutura JDBC verificadas e preparação de código/rotas concluída nesta camada.** Publicação V2 não vazia, paridade SQL/API/tela/exportação, ensaio físico de corte/retorno e troca real continuam pendentes. O dashboard continua consumindo V1.
