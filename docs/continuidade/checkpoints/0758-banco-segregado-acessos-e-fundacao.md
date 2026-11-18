# Checkpoint 0758 — banco segregado, acessos e fundação

Data: 08/11/2026, com verificação final após a virada local. Estado: implementação parcial concreta, infraestrutura/fundação testadas; **não é encerramento das quatro etapas de produção**. Consultar [implantação](../../catalogos/integracao-etl-v2-dashboard/IMPLANTACAO-0758.md) e [ADR0078](../../adr/0078-banco-produtivo-segregado-e-acesso-dedicado.md).

## Autorizações e preservação

Usuário pediu criação/validação, recebimento/recuperação reais, automação60min/progresso durável e validação do dashboard/preparação de troca e retorno. Respondeu diretamente **“Autorizar a exceção proposta”** ao desenho de acesso, depois reforçou que estará ausente e que o dashboard/V1 deve continuar funcionando. A exceção foi executada; não repetir a pergunta. A proposta original, o hash e a resposta humana estão nos outputs do chat.

Só `ETL_SISTEMA_V2` recebeu criação, schema e controle de acesso, além do único login novo dedicado aprovado. Não houve SQL/DML/DDL no V1, Shadow ou DASHBOARDS. Windows/ACLs, SQL2022 Standard16.0.1000.6, serviços e TLS preservados. Senhas/permissões antigas e rota produtiva preservadas; nenhum restart/deploy/corte/scheduler V2. Não usar sa como identidade de aplicação, reabrir RAW privado ou executar scripts antigos em massa.

## Entrega física e de código

- Bootstrap contêiner: collation `Latin1_General_100_CI_AS_SC`, compat160, FULL/RCSI/CHECKSUM; tamanhos128/64MiB, crescimento64MiB. O bootstrap vazio não é um validator aplicável ao banco depois de instalar segurança/schema.
- V001/V002 originais: owner sem login, seis roles, sete schemas. Login `etl_v2_runtime_prod` somente runtime no novo banco; segredo novo DPAPI externo; nenhum privilégio administrativo/DDL ou acesso V1/portal. Login antigo do dashboard somente CONNECT/SELECT pub no novo banco, sem alterar seus acessos antigos.
- V003–V009 originais: 75 tabelas compartilhadas verificadas,77 totais; CHECKs/FKs confiáveis. Diário manual real com dez passos, view operacional da instalação; sem Flyway fictício, dados reais ou aplicação V010–V106.
- Seis casos SQL de controle/falha/rollback passaram usando metadados sintéticos; readback independente confirmou zero linhas de teste restantes. Health entrypoint executado com a identidade dedicada.
- Dashboard: conciliação por igualdade positiva normalizada, tooltip atualizado e regressão; driver do próximo build13.4.0.jre11 após falha TLS da12.4.2. Backend44casos, TypeScript/lint/encoding e63suítes/401testes frontend passaram; nove casos no engine para a regra. Produção não reiniciada.
- Java17/JDBC13.4, TLS validado: 26 objetos atuais,958 nomes de colunas,26 rotas V1, leitura da instalação V2 e runtime sem privilégio alto conferidos. Isto não é comparação de valores/semântica ou teste de tela/API.
- Templates não executáveis de corte/retorno:26rotas/958campos; recusa incondicional e proteção contra drift/perda de grants, parsing SQL160. Sem ensaio físico ou migration automática; futura migration pertence ao dashboard Flyway.

## Evidências principais

Pasta: `C:/Users/suporte/Documents/Codex/2026-11-08/etl-v2-sql-server-dashboard/outputs`.

`criacao-banco0758-attempt2.json`, `autorizacao-seguranca0758.json`, `seguranca-instalada0758.json`, `acesso-dashboard-pos-seguranca0758.json`, `fundacao-instalada0758.json`, `controle-sql-falha-rollback0758.json`, `jdbc-real-dashboard-v1-v2-0758-attempt4.json`, `conciliacao-sql-real0758.json`, `testes-dashboard0758-attempt2.json`, `testes-dashboard0758-attempt3.json`, `pacote-corte-retorno0758.json`, `testes-preparo-corte0758.json`. Falhas anteriores de launcher/TLS preservadas. Recibos de memória em work. Manifesto/verificação final da unidade devem ser lidos junto dos resultados; nenhuma evidência anterior foi substituída por sucesso posterior.

## Próximas três ações

1. Fechar o contrato produtivo das entidades, fontes/tenant, referência/calendário real, precisão/larguras/arrays, proveniência e publicação. Conectar o novo alvo/identidade por caminho explícito, preservando recusas de Shadow e extração existente. Não tratar a aprovação de acesso como implementação desse caminho.
2. Qualificar origem real→mapper→JDBC→armazenamento/reconciliação/publicação, com valores/contagens independentes, repetição, falhas, períodos fora de D-7 e retomada física. Não usar fixtures/capturas históricas sem o boundary/proveniência original, nem ignorar quota/holds atuais para obter um teste.
3. Integrar supervisor externo one-shot60min e fronteira durável por escopo; testar ciclos sem sobreposição, parada/recuperação, alertas/capacidade/backup; então paridade SQL/API/tela/exportação, ensaio de corte/retorno e aprovação de troca. A janela normal é D-7..D0 inclusiva, oito datas; o último mês da tela permanece igual.
