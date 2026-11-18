## Resultado final 0762 — onze entidades recebidas; Excel divergente; equivalência de 100% pendente

[Checkpoint0762](../../continuidade/checkpoints/0762-onze-entidades-requalificacao-e-confronto-excel.md). Requalificação final v5 completa: 80.981 registros/3.319.248 valores/88 partições, zero rejeições, HTTP→SQL integralmente conferido; view latest e 88 runs exatos, zero RUNNING. Código final v6: 59 atributos qualificados, 22 filtros, 120 testes; replay de 36.283 linhas/729.631 valores idênticos ao banco, incluindo dois ctd_ant_document STRING iguais ao Excel. Não houve nova execução de rede v6. O segundo disparo real após 3607.645s falhou Fretes; ajuste25 passou na terceira carga manual. Task Disabled/LastResult1/STOP preservado; nenhum loop permanente.

Excel: divergente; equivalência de 100% pendente. Template 11067: 39 cabeçalhos exatos; origem 209 / Excel 212; ocorrências completas iguais 208; somente origem 1 / somente Excel 4; equivalência integral=False; arquivo ESL 1778129. Template 12072: 20 cabeçalhos exatos; origem 36.074 / Excel 36.166; ocorrências completas iguais 35.953; somente origem 121 / somente Excel 213; equivalência integral=False; arquivo ESL 1778123. Diferenças conservadas; snapshot comum não comprovado. Dados anteriores/acessos/SQL16.0.1000.6/001/002/16pins0759 preservados. Sem publicação/corte dashboard/V1. Notas abaixo são históricas; não repetir fontes encerradas.

## Complemento 0760 — novas fontes documentadas antes da implementação Java

Consultar [catálogo de Ocorrências 12072 e Consolidação 11067](../novas-entidades-0760/README.md). Mapeados 59 campos/22 filtros, com cURL e Excel oficiais nos dois recortes completos e igualdade das 7.179 células/tuplas. Java e persistência destas fontes ficam para a etapa posterior solicitada. O recebimento 0759 permanece exclusivo de Faturas 4924; publicação integral e corte do dashboard continuam pendentes.

## Atualização0759 — recebimento e recorrência reais de Faturas comprovados

Ler sempre [ENSAIOS-0759.md](ENSAIOS-0759.md), [ADR0079](../../adr/0079-qualificacao-produtiva-do-recebimento.md) e [runbook](../../runbooks/recebimento-produtivo-0759.md). Intervalo1294registros, recuperação29/10e dois ciclos4862/3854com espera real60min08s aprovados. Banco10010ocorrências/20recebimentos/52campos; task própria encerrada/desabilitada. Demais entidades e publicação/consumo de negócio ainda pendentes; V1/dashboard preservados. Histórico abaixo preservado.

**Estado atual0758:** banco novo/acessos/fundação V001–V009 instalados e qualificados; preservar dashboard/V1. Ler [IMPLANTACAO-0758.md](IMPLANTACAO-0758.md) e [ADR0078](../../adr/0078-banco-produtivo-segregado-e-acesso-dedicado.md). Autorização de acesso já respondida/aplicada. Recebimento/publicação reais, supervisor/progresso produtivo e ensaio físico do consumidor/corte/retorno ainda pendentes; nenhuma troca foi feita. As declarações históricas abaixo são substituídas somente nesse escopo.

# Integração ETL V2, SQL Server e dashboard

**Implementação específica autorizada — 0757:** o usuário solicitou a função de recuperar períodos. Consultar [RECUPERACAO-PERIODOS.md](RECUPERACAO-PERIODOS.md) e [ADR 0077](../../adr/0077-recuperacao-explicita-de-periodos.md). A função e o comando explícito foram acrescentados ao V2, com conciliação durável e extração operacional existente. O acionamento automático, a fronteira durável do supervisor produtivo e o ensaio real continuam pendentes. Não houve criação do banco, alteração do dashboard ou ativação de agendamento. As declarações abaixo de alteração somente documental descrevem as unidades históricas 0754–0756; o 0757 autoriza exclusivamente este código de recuperação e seus testes.

**Regra de extração conferida no V1 e comparada ao V2 — 0756:** consultar [REGRAS-EXTRACAO-V1-V2.md](REGRAS-EXTRACAO-V1-V2.md). O V1 usa D‑7..D0 inclusivo (oito datas) e 60 minutos após o fim do ciclo. 21 testes unitários V1 passaram. O usuário esclareceu que se trata de extração na origem; a recuperação de toda parada longa é requisito a completar no caminho produtivo, não garantia já comprovada pelo loop V1.

**Prontidão e sequência até produção — 0756:** ler [PLANO-PRODUCAO.md](PLANO-PRODUCAO.md). O pacote técnico de instalação ainda precisa ser fechado antes da criação do banco. O plano separa implementação, publicação, consumo, operação, recuperação e corte; registra o conflito de segurança da V002/runbook com a proibição vigente de alterar contas/permissões. O usuário definiu extração na origem a cada 60 minutos, com janela retroativa de sete dias, reconciliação/recuperação de todos os períodos pendentes e meta de uma hora para atualização visível. Preservar o período padrão atual do dashboard (último mês). A janela normal não limita recuperação e a janela da tela não define retenção. Os detalhes reais do V1 estão na conferência acima.

**Atualização 0755 — consultar também [Preparação e qualificação](QUALIFICACAO-0755.md).** Os 134 campos receberam soluções explícitas e descrição de tipos no engine; 76 verificações passaram no escopo declarado. Foram encontrados alertas adicionais de normalização, largura de NF, candidatos de tipos e 30 CHECKs de escopo sintético. Compatibilidade integral, aplicação da correção e consumo produtivo continuam pendentes; as publicações consultadas estão vazias. Não tratar preparação e casos sintéticos como implantação qualificada.

Data do levantamento: 08/11/2026. Checkpoint: 0754.

**Leitura obrigatória em toda retomada desta integração**, conforme pedido do usuário. Ler este índice e os três relatórios indicados abaixo antes de preparar ou implementar mudanças no banco, na persistência do V2 ou no consumo pelo dashboard. Os arquivos `AGENTS.md`, `STATES.md` e `docs/continuidade/RETOMADA.md` continuam compondo a leitura do projeto.

Estado comprovado: levantamento estrutural e estático documentado, com consultas SQL somente de leitura. **Compatibilidade semântica, operação recorrente e consumo produtivo ainda não qualificados.** `ETL_SISTEMA_V2` não foi criado nesta etapa. A próxima etapa combinada é preparar as pendências e as adaptações; a primeira implementação posterior será a criação do novo banco.

## Objetivo e decisões do usuário

O sistema `etl-extracao-dados-v2` deverá extrair periodicamente, gravar em `ETL_SISTEMA_V2` e entregar dados organizados para `dashboards-etl`. Preservar a extração já qualificada e suas evidências. Discutir as adaptações necessárias do dashboard antes de implementá-las.

- `ETL_SISTEMA`: fonte atual do dashboard; preservar.
- `ETL_SISTEMA_V2_SHADOW`: ambiente de validação do V2; preservar sua identidade e seus controles.
- `ETL_SISTEMA_V2`: futuro banco de produção do V2, separado do Shadow.
- `DASHBOARDS`: mantém usuários, permissões da aplicação, sessões, metas, justificativas e configurações do portal.
- Não renomear Shadow nem fazer substituição global de nomes, chaves ou tipos para aparentar compatibilidade.
- A organização já existente em `ctl`, `stg`, `core`, `ref`, `mart`, `pub` e `recon` é a base do desenho. Nomes internos, inclusive `_lab`, têm contratos existentes; eventual evolução exige dependências e migração explícitas.

Esta unidade altera documentação. Não altera Java, frontend, configuração, SQL versionado, banco, agendamento, serviços ou produção. Não executa extração, instalação, carga, endpoint ou cutover.

## Documentos e evidências que devem ser consultados

Diretório das evidências:

`C:\Users\suporte\Documents\Codex\2026-11-08\etl-v2-sql-server-dashboard\outputs`

Leitura humana, nesta ordem:

1. [Mapeamento_ETL_SISTEMA_V2.txt](C:/Users/suporte/Documents/Codex/2026-11-08/etl-v2-sql-server-dashboard/outputs/Mapeamento_ETL_SISTEMA_V2.txt): arquitetura física, recebimento das nove entidades, pacote SQL e pendências de instalação.
2. [Consumo_Dashboard_e_Fontes_V1_V2.txt](C:/Users/suporte/Documents/Codex/2026-11-08/etl-v2-sql-server-dashboard/outputs/Consumo_Dashboard_e_Fontes_V1_V2.txt): caminho das telas até o SQL, regras, diferenças entre fontes V1/V2 e limites da análise.
3. [Dicionario_Campos_Dashboard.txt](C:/Users/suporte/Documents/Codex/2026-11-08/etl-v2-sql-server-dashboard/outputs/Dicionario_Campos_Dashboard.txt): 958 pares objeto/campo expostos pelos contratos atuais, tipos, expressões e candidatos V2. Consultar as linhas do domínio que será tratado; o nome parecido não aprova equivalência.

Evidências detalhadas no mesmo diretório:

| Assunto | Arquivos |
| --- | --- |
| Instalação e objetos V2 | `inventario-sql-v2.json`, `mapa-objetos-v2.json`, `pendencias-preparacao-v2.json` |
| Recebimento e persistência Java/SQL | `contrato-recebimento-v2.json`, `referencias-persistencia-v2.json`, `verificacao-contratos-v2.json` |
| Banco observado e dependências | `sql-inventory.json`, `sql-catalog-details.json`, `sql-aggregates.json`, `sql-mapping-signatures.json`, `sql-dashboard-lineage.json`, `sql-v1-contract-profile.json` |
| Campo, expressão e candidato V2 | `linhagem-sql-campos-dashboard.json`, `matriz-consumo-dashboard.json`, `resumo-mapeamento-dashboard.json` |
| Tela, rota, DTO e frontend | `rotas-e-consumo-dashboard.json`, `contratos-api-campos-dashboard.json` |
| Origem Data Export/GraphQL e mappers | `comparacao-fontes-v1-v2.json` |
| Inconsistência de conciliação atual | `sql-dashboard-rule-probes.json` |
| Integridade da entrega do levantamento | `manifesto-entrega.json`, `verificacao-preservacao.json` |
| Continuidade e conferência das alterações documentais posteriores | `Continuidade_Preparacao_ETL_SISTEMA_V2.txt`, `verificacao-documentacao-0754.json`, `manifesto-documentacao-0754.json` |

`manifesto-entrega.json` e `verificacao-preservacao.json` registram a fotografia anterior à atualização documental solicitada pelo usuário. Não reescrevê-los para dizer que os documentos atuais ficaram inalterados. A atualização 0754 tem recibo separado. Os arquivos de catálogo têm definições SQL e metadados; não copiar credenciais, payloads privados ou dados de negócio para estes resumos.

Se uma evidência não estiver acessível, registrar a ausência e recuperar o arquivo verificado antes de usar sua conclusão. Caminho ou hash isolado não substitui o conteúdo nem concede aprovação.

## O que foi observado

Na instância `ROD-SRVW-001`, SQL Server Standard 16.0.1000.6, os bancos `ETL_SISTEMA`, `ETL_SISTEMA_V2_SHADOW` e `DASHBOARDS` estavam online. `ETL_SISTEMA_V2` e `DASHBOARDS_DEV` não existiam na consulta. A configuração consultada do dashboard aponta para `DASHBOARDS`.

Os 26 synonyms de `DASHBOARDS` apontam para objetos existentes em `ETL_SISTEMA`. O caminho principal é:

`Tela React → serviço HTTP → controller/service Spring → repository JDBC/JPA → objeto de DASHBOARDS → synonym → view/fato/dimensão do V1`.

Não basta criar nove tabelas ou mudar a conexão. O dashboard também depende de fatos materializados, calendário, referências, auditoria, regras de exclusão, normalização e dados auxiliares Raster. As integrações externas e os dados próprios do portal precisam continuar com sua procedência correta.

O inventário abrange 431 classes de backend, 216 rotas, 211 blocos SQL, 188 tipos de DTO/1059 campos e 209 interfaces TypeScript/1209 campos. Há 87 vínculos estáticos entre rota, retorno Java e interface importada. Os 958 pares SQL incluem colunas expostas pelos objetos, não apenas colunas comprovadamente utilizadas em uma execução de tela.

Correspondências candidatas: 707 por nome exato, 34 por normalização de rótulo, 83 por alias/projeção e 134 que exigem adaptação ou desenho explícito. **Nenhum desses grupos recebeu aceite semântico.** Os 134 não significam necessariamente ausência de informação na origem. As 699 dependências retornadas pelo engine não traziam atribuição por coluna; as expressões foram relacionadas por leitura estática das projeções SQL.

No Shadow, havia pequenas cargas nas cinco entidades base; as estruturas de expansão e as 22 views de `pub` observadas estavam vazias. Isso não demonstra recebimento integral atual, publicação pronta ou prontidão de produção. As provas offline anteriores de conteúdo, inclusive Faturas 0753-155, continuam válidas apenas no seu escopo; não equivalem a prova JDBC, Main, agendador ou dashboard.

## Recebimento das fontes e consumo atual

| Entidade | Rota principal identificada no V2 | Corte de origem | Consumo principal atual do dashboard |
| --- | --- | --- | --- |
| Coletas | Data Export 6908 | `picks.request_date` | `vw_coletas_powerbi` |
| Fretes | Data Export 6389 | `freights.service_at` | `fato_fretes_faturamento`; Performance usa `vw_fretes_powerbi` |
| Manifestos | Data Export 6399 | `manifests.service_date` | `vw_fato_manifestos_dash` e fato de apoio |
| Cotações | Data Export 6906 | `quotes.requested_at` | `vw_cotacoes_powerbi` |
| Localizador | Data Export 8656 | `freights.service_at` | `vw_localizacao_cargas_powerbi` e transformação adicional de Tracking |
| Contas a Pagar | Data Export 8636 | `issue_date` | `vw_contas_a_pagar_powerbi` |
| Faturas por Cliente | Data Export 4924 | `service_at` | `fato_gestao_vista_faturas` |
| Inventário | Data Export 10633 | `started_at` | fatos/indicadores e verificações estruturais |
| Sinistros | Data Export 6392 | `opening_at_date` | `vw_sinistros_powerbi` e indicadores |

No V1, Coletas e Fretes tinham extração GraphQL; as outras sete entidades já usavam Data Export. No V2, as nove rotas principais inventariadas são Data Export. Users permanece como apoio GraphQL; há também mappers GraphQL auxiliares de Coletas/Fretes, e Raster usa integração própria. Conferir a composição efetiva de execução antes de afirmar quais auxiliares estão ativos. A presença de uma classe não prova ativação.

As cinco entidades base têm estruturas específicas de `stg/core`. Contas a Pagar, Faturas, Inventário e Sinistros compartilham o recebimento de expansão: observação física, campos tipados, arrays e composição de raiz/parte/componente. Preservar `execution_id + occurrence` no recebimento. A identidade lógica estável entre execuções ainda precisa de contrato e prova; a chave atual da raiz inclui `run_id`.

## Pendências para a preparação seguinte

As linhas F01–F10 correspondem ao arquivo `pendencias-preparacao-v2.json`; as demais detalham o consumo e os riscos descobertos na ampliação do levantamento. Todas permanecem pendentes de preparação/qualificação, sem correção implementada nesta etapa.

| ID | Pendência comprovada ou contrato a definir | Entrega esperada da preparação |
| --- | --- | --- |
| F01 | Runtime e preflight aceitam Shadow; mudar apenas a URL não habilita produção | Desenho de perfil explícito para `ETL_SISTEMA_V2`, preservando a extração e as recusas do perfil Shadow |
| F02 | Dez migrations e referências Java contêm alvo Shadow/laboratório | Lista exata de dependências e proposta de evolução por arquivo; sem substituição global |
| F03 | Diretório tem V001–V106; baseline e manifesto terminam em V105 | Um caminho de instalação reproduzível, manifesto compatível e equivalência entre caminhos, sem reescrever história aplicada |
| F04 | Precondições da V105 diferem das collations observadas no Shadow | Definição do novo banco e prova planejada de todas as precondições; `Latin1_General_100_CI_AS_SC` é candidata, não decisão executada |
| F05 | Comentário de Sinistros aceita 4000 no mapper; armazenamento tipado observado permite 1024 | Alinhamento revisado da V106 e validação de recebimento sem truncamento |
| F06 | Dois arrays de Faturas aceitam 110 no código; CHECK atual limita posições a 0–31 | Finalizar template V107, hash real do CHECK e validação limitada aos campos de Faturas; placeholder atual impede execução |
| F07 | Views analíticas dependem de `run_id`, `reference_revision` e estado de publicação | Contrato de publicação completa por entidade/partição e conjunto coerente de dependências; manter última publicação válida se uma carga falhar |
| F08 | Dashboard depende de 26 contratos com views, fatos e dimensões | Matriz de compatibilidade por campo e rota; definir a camada de publicação e as adaptações SQL/API/frontend necessárias |
| F09 | Identidade física não equivale à lógica; há multiplicidades legítimas | Chaves e granularidade provadas por domínio, recorrência, atualização, exclusão e reprocessamento sem perder ocorrências |
| F10 | Referências e segurança não surgem prontas com a estrutura | Procedência/versão dos catálogos e políticas; separar efeitos de segurança da V002 e respeitar a proibição vigente de alterar contas/permissões |
| D01 | Tipos e chaves têm representações distintas | Contratos explícitos de IDs, documentos, nulos e labels; Coletas `ID` textual/numérico e `Volumes` int/bigint incluem dependência do validador Java |
| D02 | Precisão de armazenamento e publicação varia | Matriz de precisão, escala, unidade e arredondamento; incluir Contas a Pagar e valores com 14 casas, cuja prova offline não qualifica DECIMAL(28,8)/JDBC |
| D03 | Datas de negócio, offset, captura, snapshot e publicação têm significados diferentes | Contrato por campo; períodos do dashboard em `America/Sao_Paulo`, início inclusivo/fim exclusivo; preservar competência e `snapshot_em` |
| D04 | Deduplicação, exclusão e cálculos variam por tela | Regras aprovadas para Coletas, Fretes, Manifestos e Faturas; preservar tabela/exportação, ACL/filial, filtros, paginação e ordenação estável |
| D05 | Calendário, dimensões, auditoria/saúde e Raster são contratos adicionais | Plano de proveniência e disponibilidade de cada apoio, sem inventar zeros/NULL para preencher lacunas |
| D06 | Overview de Contas a Pagar usa `LIKE '%conciliado%'`, que inclui “Não conciliado” | Tratar como inconsistência atual e propor correção explícita; não homologar o comportamento como regra do V2 |
| D07 | `tentativas_medias` de Coletas está fixado em zero na consulta atual | Definir a regra de negócio desejada antes de adotar o campo da nova fonte; sincronizar dicionários de KPI/gráficos se houver mudança |
| D08 | Fluxo completo e agendamento ainda não foram exercitados | Plano de testes de recebimento SQL, publicação, API/tela/exportação, falha/retomada, concorrência e recorrência; depois plano de corte e recuperação |

A inconsistência D06 foi reproduzida por SELECT sobre o conjunto completo da view atual: 17.131 linhas; expressão LIKE conta 15.273; igualdade com “Conciliado” conta zero. Havia 15.273 “Não conciliado” e 1.858 NULL. Não foram aplicados período/ACL de endpoint e não foi executada chamada HTTP. Não generalizar o número como resultado de todas as telas.

## Como conduzir a próxima etapa

**Próximo trabalho: preparação técnica das pendências. Não iniciar pela criação do banco nesta retomada de preparação.** A sequência abaixo registra o combinado do usuário; não marca implementação como concluída.

1. Produzir uma matriz revisável por contrato/campo: origem e expressão V1, campo/projeção V2, granularidade e chave, tipo/precisão/unidade, nulo/status, data/fuso, exclusão, dependências, regra de publicação, consumidores e teste de aceite. Tratar as 134 adaptações explícitas, os 83 aliases, os 34 rótulos e também conferir os 707 nomes coincidentes. Separar fato observado, decisão proposta e dúvida de negócio.
2. Preparar o pacote de implantação: ordem e escopo dos SQL, precondições/collation, baseline/manifesto/V106/V107, efeitos de segurança da V002, referências, perfil de produção isolado, publicação recorrente e adaptações do dashboard. Entregar lista de arquivos afetados, decisões e testes necessários, impacto e recuperação. Proposta preferencial para avaliar: publicação analítica estável em `pub`, com contratos explícitos para os synonyms existentes e adaptações do consumidor onde forem necessárias. Não conectar o dashboard diretamente a todas as execuções de `stg/core`.
3. Fechar o roteiro de implementação por dependência. A primeira implementação será criar somente `ETL_SISTEMA_V2` no alvo existente, com atributos previamente definidos e leitura de confirmação; instalar o pacote preparado será etapa distinguível da criação. Depois qualificar recebimento/recorrência, publicação e consumo, antes de qualquer corte. Criação de banco, carga, agendamento e redirecionamento do dashboard não são a mesma ação.

Não executar todo o diretório `database` recursivamente. Existem migrations, baseline alternativo, validators, propostas, evidências e scripts históricos. Alguns validators escrevem dados sintéticos, mesmo quando usam rollback. A preparação deve classificar cada arquivo e explicitar seus efeitos. Não falsificar histórico Flyway de uma instalação manual nem executar baseline e migrations equivalentes em duplicidade.

## Critérios para avançar às implementações

- Cada contrato necessário tem procedência, transformação, granularidade, tipos e regra de publicação definidos, ou uma dependência explícita que impeça apenas a implementação afetada.
- O pacote de criação/instalação tem alvo, ordem, precondições, efeitos e recuperação verificáveis. Pendência de segurança é resolvida respeitando as regras vigentes; não ignorar V002 nem executar seus usuários/grants automaticamente.
- As mudanças propostas preservam os contratos de extração já qualificados e isolam a produção dos perfis Shadow. Controles não são desativados globalmente.
- O plano de consumo cobre SQL, DTOs, frontend, filtros, autorização, tabela, exportação, indicadores e seus dicionários. Nome/tipo compatível sozinho não satisfaz o critério.
- Os testes previstos distinguem conteúdo offline, armazenamento físico, publicação e resposta real da aplicação. Nenhuma dessas camadas recebe aceite por evidência de outra.

Preservar SQL Server 16.0.1000.6, contas, credenciais, permissões, TLS, serviços e dados atuais. As alterações preexistentes dos três repositórios não pertencem a esta unidade e não devem ser revertidas ou incluídas em commit automático. Esta documentação não concede novo orçamento de extração nem autorização para deploy/cutover.
