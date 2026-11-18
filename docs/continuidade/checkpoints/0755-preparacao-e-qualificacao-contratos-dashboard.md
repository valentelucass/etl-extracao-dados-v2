# Checkpoint 0755 — preparação e testes dos contratos do dashboard

## Identificação e objetivo

- Data de referência: 08/11/2026.
- Anterior: [0754](0754-mapeamento-sql-v2-e-contrato-dashboard.md), preservado. Seu hash e os novos arquivos estão no manifesto 0755.
- Pedido: “conclua todas essas pendencias com testes e avaliacoes minusciosas reais”, em continuidade aos 134 campos e à conciliação de Contas a Pagar.
- Unidade exercitada: especificar as adaptações e executar avaliação real de tipos, regras e valores agregados antes da criação/instalação. Preservado o combinado de criar o novo banco como primeira implementação posterior.
- Estado: preparação testada nas camadas descritas; implementação e qualificação integrada **não concluídas**. Não há aceite de compatibilidade integral.

## Autorização e limites

Foram executados SELECTs, CTEs sintéticas e funções de metadados na instância existente, por SQL auth protegida e TLS do runtime comum. Alvos: `master`, `ETL_SISTEMA` e `ETL_SISTEMA_V2_SHADOW`. Prazo de comando de dez segundos, sem retries automáticos; após falhas houve diagnóstico/adaptação do teste ou redução explícita do escopo. Credenciais, payloads privados e valores identificáveis não foram exportados.

Não foram executados DDL/DML, migrations, fonte externa, Main, aplicações/portas, agendadores, serviços, cutover ou alterações de contas/permissões/SQL Server. Código de extração, código do dashboard, configurações e SQL versionado não foram alterados. A correção da conciliação está em patch revisável, não aplicada. Scripts de preparação e testes ficam no `work` do chat; arquivos de repositório alterados são documentação.

## Alterações e decisões

Leitura detalhada obrigatória: [QUALIFICACAO-0755.md](../../catalogos/integracao-etl-v2-dashboard/QUALIFICACAO-0755.md) e [índice central](../../catalogos/integracao-etl-v2-dashboard/README.md).

Evidências em `C:\Users\suporte\Documents\Codex\2026-11-08\etl-v2-sql-server-dashboard\outputs`:

- `solucoes-134-campos-0755.json`, `matriz-958-contratos-0755.json`: especificações, dependências e critérios; sem aceite semântico.
- `sql-qualification-0755-attempt1.json`, `sql-field-profiles0755-attempt1.json`, `sql-field-profiles0755-attempt2.json`, `sql-profile-diagnosis0755.json`, `sql-qualification-followup0755.json`: resultados e falhas preservados.
- `resultado-qualificacao0755.json`, `avaliacao-candidatos-0755.json`, `restricoes-producao-0755.json`: verificações, candidatos recusados e restrições reais.
- `Correcao_Conciliacao_Dashboard_0755.patch`, `correcao-conciliacao-preparada0755.json`: alteração proposta de SQL e explicação do KPI; `git apply --check` passou após correção de EOL, sem aplicar.
- `Qualificacao_e_Solucoes_Dashboard_0755.txt`, `verificacao-preservacao0755.json`, `manifesto-qualificacao0755.json`: entrega, integridade e preservação.

Decisões: sem soma de todas as execuções, sem deduplicação por chave não provada, sem preenchimentos artificiais para aparentar paridade, sem remoção global de travas sintéticas. Contextos de publicação/reconciliação, metadata, noops e hash nas expressões são entradas futuras obrigatórias, sem valor padrão; descrever seus tipos não comprova sua disponibilidade.

Diferenças adicionais: NBSP na normalização de Faturas; status_processo é calculado por documento; candidatos YYYYMM/DATE/TIME inadequados; três valores de NF excedem 1024 e chegam a 2254 unidades UTF-16. Há 30 CHECKs reais limitados a sintético. O novo banco ainda não existe e as publicações consultadas estão vazias. Não há base para aceitar operação produtiva por um sucesso de compilação.

## Execução e evidência

| Critério | Camada / resultado | Limite |
| --- | --- | --- |
| Oito projeções / 134 campos | SQL Server descreveu tipos, sem erro e sem nomes duplicados | TOP(0), com parâmetros declarados; não compara dados nem instala views |
| 23 objetos candidatos | Descrição real aprovada | Não prova semântica |
| 46 casos | Executados no engine, expectativas conferidas | Dados sintéticos, não fornecedor/Java/HTTP |
| Quatro regras de referência | Zero divergências em 15.767 + 4.780 + 39.783 + 342 linhas | Quatro conjuntos V1, não entidades distintas nem comparação V2 |
| Conciliação | 17.131 linhas; regra atual 15.273; regra exata zero; nove casos aprovados | Sem filtros de endpoint; patch não aplicado |
| Perfil dos 958 campos | 887 campos no conjunto inteiro; 71 em amostra de até 200 linhas | Duas consultas completas atingiram o prazo; manter pendência integral |
| Resultado automatizado | 76/76 verificações passaram; 94 SELECTs bem sucedidos no conjunto final/diagnóstico | Não inclui aprovação produtiva; falhas anteriores não foram apagadas |
| Preservação | Recibo e manifesto separados | Manifestos 0754 continuam históricos; não foram reescritos |

Falhas preservadas: tentativa inicial do perfil encontrou três conversões proibidas, diagnosticadas no engine; o classificador deixou de executar conversões impossíveis e passou a registrar o candidato inadequado. Inventário e Raster Transit Time não receberam aceite de varredura integral após limite de leitura; amostras foram separadas. Primeiro patch não aplicava por EOL; foi corrigido e conferido sem aplicar. Nenhum desses fatos foi convertido em teste aprovado retroativamente.

Sem campanha própria de extração, aplicação ou efeito SQL em andamento ao fechar este bloco. Nenhum aceite de fornecedor, armazenamento JDBC, publicação produtiva ou interface foi encerrado. Resultados de Maven/JUnit, TypeScript/lint/Vitest e HTTP não foram inventados: não foram executados nesta preparação.

## Retomada imediata — até três ações

1. Fechar o pacote produtivo de instalação, escopo real/referências, precisão e segurança, incorporando os 30 CHECKs sintéticos e os alertas de NF/tipos; preservar contratos de laboratório e extração. Atributos e ordem devem estar explícitos antes de criar o alvo.
2. Seguir a implementação por dependência, começando pela criação de `ETL_SISTEMA_V2`; depois instalar/qualificar recebimento, referências e publicação não vazia. Testar entradas de publicação/reconciliação e relações fiscais/dimensionais com dados e escopo provados.
3. Aplicar e testar as adaptações do consumidor, incluindo patch de conciliação, regressões Java/frontend e fluxo SQL→API→tabela/exportação com filtros/ACL, recorrência e falha. Concluir os 71 perfis integrais por partições/plano revisado; não extrapolar a amostra.

Dependências concretas do aceite integral: implementação e dados publicados não vazios, atualmente inexistentes nessa camada. Esta revisão não é autorização para efeitos de segurança nem concessão de orçamento de fonte. Não parar as preparações independentes nem declarar compatibilidade por esforço. O objetivo final do usuário continua pendente até os gates de integração terem evidência própria.
