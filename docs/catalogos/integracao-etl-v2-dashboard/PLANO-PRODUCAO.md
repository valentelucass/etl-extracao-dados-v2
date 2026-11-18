# Caminho de implementação até produção — 0756

**Atualização 0757:** por pedido específico posterior do usuário, a recuperação explícita de períodos foi implementada antes da criação do banco. Consultar [RECUPERACAO-PERIODOS.md](RECUPERACAO-PERIODOS.md). Isso fecha a função e sua camada local de testes; não fecha P5/P6. O supervisor de 60 minutos, sua fronteira durável, a qualificação dos demais contratos e o ensaio físico continuam pendentes. A primeira implementação do banco permanece a criação de `ETL_SISTEMA_V2` após o fechamento de P0. As declarações de código preservado/rodada documental abaixo referem-se à revisão 0756.

Referência: 08/11/2026. Este roteiro complementa [a qualificação 0755](QUALIFICACAO-0755.md) e o [índice central](README.md). Preservar o contexto e as evidências anteriores. Estado: plano de implementação e de aceite, ainda sem instalação, carga, aplicação de patch ou mudança de consumidor.

**Conferência posterior solicitada pelo usuário:** ler [REGRAS-EXTRACAO-V1-V2.md](REGRAS-EXTRACAO-V1-V2.md). A expressão correta é extração na origem. O V1 usa D‑7..D0 inclusivo e espera 60 minutos após o fim do ciclo. Foram executados 21 testes unitários V1. Não reduzir a janela para sete datas nem transformar o intervalo em início fixo de hora sem explicitar a mudança. A política abaixo registra o requisito de recuperar todos os períodos pendentes, ainda não garantido pelo caminho normal V1.

## Resposta de prontidão

Há evidência suficiente para iniciar o fechamento técnico do pacote e preparar implementações por etapas. **O pacote ainda não está pronto para instalar, e não há prontidão de produção.** Antes da primeira implementação combinada — criação de `ETL_SISTEMA_V2` — precisam estar definidos os atributos do alvo e um caminho de instalação compatível com as regras vigentes. A disponibilidade da pasta `database` não satisfaz esse critério.

As soluções dos 134 campos foram especificadas e descritas pelo engine, com 76 verificações aprovadas no escopo declarado. Há 24 alertas de candidatos e 71 campos avaliados apenas em amostra; suas pendências permanecem. A correção de conciliação é um patch não aplicado. As publicações analíticas observadas no Shadow estão vazias. Esses resultados não aprovam o fluxo de produção.

## Decisões e restrições já estabelecidas

- Banco produtivo futuro separado: `ETL_SISTEMA_V2`; preservar `ETL_SISTEMA` e `ETL_SISTEMA_V2_SHADOW`.
- Instância existente; preservar versão 16.0.1000.6, edição, serviços, TLS, credenciais, usuários e permissões.
- Preservar os mappers e contratos de extração já qualificados. Criar o caminho produtivo explicitamente, mantendo as recusas do laboratório.
- Os dados próprios do portal permanecem em `DASHBOARDS`. O Java do dashboard continua lendo nomes simples; a rota física deve ser governada no banco.
- O [ADR 0007](../../adr/0007-execucao-one-shot-e-agendamento-externo.md) define execução única por disparo de um agendador externo. Não acrescentar loop ou daemon ao ETL.
- Até existir prova física de rotas e isolamento granulares, o [runbook de troca](../../runbooks/freeze-cutover-e-recuperacao.md) considera a unidade `DATABASE_WIDE`. Não prometer migração por entidade como se já fosse suportada.

## Pendência concreta de segurança e instalação

`database/migrations/V002__create_v2_database_roles.sql` cria roles, usuário sem login, altera ownership e aplica grants/denies. O runbook antigo também pressupõe identidades distintas e revogação de escrita durante o corte. Esses efeitos conflitam com a proibição posterior e vigente de alterar contas/permissões. O comentário inicial da V002 não substitui a leitura de seus comandos.

Antes de instalar, preparar um desenho que respeite as regras atuais e verificar o acesso efetivo das identidades existentes. Não executar a V002 automaticamente, não simplesmente pulá-la deixando dependências quebradas, não simular seu histórico Flyway e não trocar uma identidade de aplicação pela administrativa para contornar acesso. Se os acessos existentes forem insuficientes, registrar o impedimento exato, sem efetuar alteração proibida. O roteiro antigo não concede exceção à regra vigente.

Também há 30 CHECKs reais restritos a sintético; o pacote produtivo precisa de contratos reais de origem, tenant, identidade, referência e proveniência. Essa separação deve ser explícita e testável, preservando os contratos de laboratório; não remover travas em massa.

## Etapas, dependências e critérios de saída

| Etapa | Entrega concreta | Evidência necessária para avançar | Situação atual |
| --- | --- | --- | --- |
| P0 — pacote fechado | Alvo e atributos; instalação versionada; segurança compatível; inventário de scripts e contratos produtivos | Lista exata de SQL/arquivos, hashes, ordem, precondições, plano de recuperação e resolução de dependências. Definir collation/compatibilidade, baseline/V106/V107 e tratamento dos CHECKs sintéticos | Pendente; é o trabalho imediato antes de criar o banco |
| P1 — banco e estrutura | Criar somente `ETL_SISTEMA_V2`; depois instalar o pacote revisado em passo distinguível | Conferir nome/instância, atributos e objetos, constraints, índices, tipos, histórico real de instalação, acesso existente e preservação dos outros bancos. Instalação limpa e atualização devem produzir o mesmo schema aprovado | Não iniciado; primeira implementação combinada é a criação |
| P2 — recebimento produtivo | Perfil V2 específico para o novo alvo; gravar cada entidade com identidade e precisão corretas | Testes reais fonte qualificada→mapper→JDBC→armazenamento, incluindo ocorrências repetidas, campos ausentes/nulos, arrays, comentários longos, valores exatos e reversão de falha. Manter regressões de extração | Não iniciado no novo alvo |
| P3 — referências, histórico e publicação | Referências reais versionadas, carga inicial e publicação coerente de execuções completas | Cobertura temporal e de dependências; chaves e cardinalidades; atualização tardia; exclusão só com prova de cobertura; reconciliação de contagens/valores; reprocessamento sem duplicar; falha conserva última publicação válida | Desenho parcial; publicações consultadas vazias |
| P4 — consumo do dashboard | Contratos SQL estáveis, adaptações API/frontend e correção da conciliação | Regressões Java/frontend; tipos/nulos/offset/precisão; ACL/filial, filtros, ordenação e paginação; tabelas/exportações; KPIs e seus dicionários. Testes com publicação não vazia e resultados esperados independentes | Soluções e patch preparados; integração não exercitada |
| P5 — operação e recuperação | Agendador externo, exclusão de ciclos concorrentes, monitoramento, capacidade e recuperação | Ciclos sucessivos reais; duração compatível com a meta; limites do fornecedor; interrupção e retomada; tentativa repetida sem duplicar; alertas de falha/atraso; restore/replay medidos; espaço, índices, retenção e crescimento avaliados | Metas e prova operacional ainda pendentes |
| P6 — troca e acompanhamento | Ensaio de corte, rota verificável, retorno testado e aceite do responsável | Manifesto dos consumidores, unidade de troca, janela e ponto de retorno; teste antes/depois; publicação inicial íntegra; reconciliação posterior e responsável por acompanhar incidentes | Não iniciado; nenhuma autorização de corte inferida deste roteiro |

P2 e P4 podem ter desenvolvimento independente onde as interfaces estejam fechadas. A aprovação integrada de P4 depende de dados publicados em P3. Definir antes da ação qual banco/ambiente receberá cada teste; não iniciar a aplicação de desenvolvimento apontando para `DASHBOARDS` produtivo.

## Conteúdo mínimo de P0

1. Classificar migrations, baseline alternativo, validators com efeitos, propostas e arquivos históricos. Escolher um caminho de instalação reproduzível, sem executar recursivamente todos os SQL nem duplicar baseline e migrations.
2. Fechar atributos do novo banco e precondições. `Latin1_General_100_CI_AS_SC` é candidata por causa das precondições da V105; ainda exige decisão e validação no pacote. Não alterar collation da instância, V1 ou Shadow.
3. Alinhar baseline/manifesto até V105 com V106 e concluir o tratamento de V107. Hash do CHECK observado no Shadow não deve ser declarado automaticamente como hash do banco novo. O template atual não é instalável sem seus valores e precondições conferidos.
4. Resolver precisão, largura e publicação: valores com 14 casas; comentário Sinistros 1024/4000; arrays Faturas 32/110; NF até 2254 no V1; normalização NBSP; datas e chaves. Não corrigir apenas a view quando mapper ou armazenamento também limitarem o valor.
5. Definir os fornecedores de contexto exigidos nas expressões dos 134 campos: publicação comprometida, reconciliação, tombstone/data de exclusão, metadata, noops e payload canônico do hash. Parâmetro descrito em TOP(0) não é informação operacional já disponível.
6. Resolver o desenho de segurança e os contratos sintéticos conforme a seção anterior. Manifestar separadamente as dependências ainda não resolvidas, sem receber aceite produtivo por planejamento.

## Regras de publicação e recorrência a provar

- Selecionar execução/revisão/partição coerentes; não somar fotografias de todos os ciclos nem escolher simplesmente o maior ID.
- Separar horário de negócio, atualização na origem, recebimento, materialização e publicação. O dashboard deve conseguir mostrar a atualização real e reconhecer dado atrasado.
- Conservar a última publicação íntegra se uma entidade ou dependência falhar. Uma publicação parcial não pode substituir silenciosamente um conjunto consistente.
- Preservar ocorrências e provar identidade entre ciclos antes de upsert; não deduplicar por sequence_code ou unique_id sem o contrato do domínio.
- Para exclusão na origem, uma ausência em página/ciclo parcial não basta. Exigir escopo completo e reconciliação aprovada; preservar a primeira data efetiva e a trilha.
- Provar recuperação após timeout, interrupção, resposta incerta, atualização tardia e reexecução. Conciliar estado autoritativo antes de repetir um efeito desconhecido.
- A carga histórica deve respeitar as diferenças de GraphQL/Data Export. Decidir fonte e cobertura por período; não copiar tabelas do V1 ou misturar fontes sem procedência e reconciliação.

## Decisões de operação que ainda precisam ser fechadas

| Decisão | Estado | Consequência |
| --- | --- | --- |
| Frequência de execução | Usuário definiu: a cada 60 minutos | Agendador externo, sem sobreposição de escritores do mesmo escopo; medir duração e registrar disparos atrasados, sem acumular processos |
| Meta de atualização no dashboard | Usuário confirmou: uma hora depois está bom | Medir tempo até a publicação visível, incluindo espera pelo ciclo, extração e materialização. Disparo a cada 60 minutos não prova por si só atraso máximo de 60 minutos; registrar eventual incompatibilidade medida, sem mudar a frequência silenciosamente |
| Histórico e período do dashboard | Usuário esclareceu: preservar o dashboard mostrando naturalmente o último mês, sem alterar esse comportamento | Preparar os dados para as janelas atuais e suas dependências, incluindo saldos/faturas anteriores quando usados pelos cálculos. Esse esclarecimento não define descarte de histórico ou retenção de um mês; dimensionar a carga inicial pelos contratos existentes |
| Retenção do histórico e das trilhas | Pendente para P5 | Dimensionar armazenamento e recuperação sem apagar evidências automaticamente |
| Tempo máximo de recuperação e perda de dados tolerada | Pendente para ensaio de recuperação | Medir restore/replay contra as metas; backup existente não comprova restauração |
| Responsáveis por operação, dados, consumidor e corte | Atribuição a fechar antes de P5/P6 | Definir quem recebe alertas, decide abortar e aceita o corte; não inventar nomes |

Não assumir resposta ou escolha pré-selecionada pelo tempo decorrido. Essas decisões não impedem preparar interfaces, scripts e regressões independentes, mas impedem declarar seus critérios operacionais aprovados.

O usuário inicialmente pediu explicação da pergunta de atualização; depois definiu a frequência de 60 minutos e confirmou a meta de uma hora. Essas respostas substituem a pendência anterior de frequência/meta. A preferência sobre o último mês preserva o comportamento da tela; não limita recuperação de falhas, retenção ou dependências de cálculo a esse período.

## Política de execução e recuperação definida nesta conversa

**Requisito do usuário:** a cada 60 minutos, baixar novamente os dados dos últimos sete dias, reconciliar completude e pendências e recuperar períodos perdidos durante paradas. O período padrão atual do dashboard permanece inalterado.

O desenho deve ter duas frentes complementares, com o mesmo controle de escopo e orçamento de origem:

1. **Extração normal na origem, com janela retroativa:** capturar novamente os dados e alterações recentes, inclusive registros já vistos. A regra V1 conferida é D‑7..D0 inclusivo, com oito datas civis; preservar como base e não reduzir para D‑6 por interpretação de “sete dias”. Fixar o corte temporal de cada ciclo e registrar o intervalo efetivamente consultado; dividir a janela conforme o contrato temporal de cada entidade. Fronteiras internas inclusiva/exclusiva e filtros de fonte inclusivos devem ser traduzidos explicitamente em `America/Sao_Paulo`. O dia corrente permanece aberto; não considerar um dia futuro inteiro reconciliado só porque a consulta termina na data seguinte.
2. **Recuperação de pendências:** consultar a última cobertura contínua comprovada e os intervalos incompletos de cada entidade/escopo. Recuperar todos os intervalos faltantes, mesmo anteriores à janela móvel de sete dias, em lotes limitados e retomáveis. A janela normal não é limite de recuperação nem política de retenção.

Exemplo: última cobertura aprovada no dia 1, parada a partir do dia 2 e retorno no dia 12. A consulta normal dos dias recentes não basta: os dias 2 em diante ainda sem prova de cobertura precisam entrar na recuperação. Uma parada de um dia também não pode deixar o dia sem processamento, mesmo quando a extração normal já cobre sua data.

Manter por entidade, origem/tenant, contrato/filtros e partição: tentativa/execução; intervalo e corte; páginas e terminalidade; ocorrências e contagens conforme a granularidade do fornecedor; resultado de qualidade/persistência; publicação; erro/pendência e fronteira de cobertura contínua. Conferir totalizações só quando o contrato da fonte as fornecer e no mesmo significado — entidades distintas não são sempre linhas físicas.

Uma falha, resposta incerta, limite de paginação ou partição incompleta não avança a fronteira de cobertura. Ter sucesso num dia posterior não permite pular um dia anterior pendente. Distinguir extração concluída de publicação concluída: não expor uma combinação parcial de dependências no dashboard. O dia corrente pode ter uma fotografia publicada válida até um corte sem receber aceite de dia fechado.

Reexecução precisa preservar ocorrências legítimas, atualizar a mesma identidade lógica provada sem duplicação indevida e manter histórico de tentativas. Ausência numa janela incompleta não comprova exclusão. Ao retornar, conciliar estado durável antes de repetir um efeito de resultado desconhecido. O backlog deve ser paginado e ter progresso persistido, sem ultrapassar os tetos do fornecedor/VM ou iniciar processos concorrentes do mesmo escopo. Se o trabalho não couber no ciclo, manter pendências e atraso visíveis; não declarar sucesso nem descartar os períodos antigos.

**Base existente confirmada por leitura de código:** `RuntimeTemporalPlanner` planeja recuperação a partir de `firstUnpublished`; `RuntimeTemporalCoordinator` mantém `contiguousEnd` sem atravessar lacunas e distingue ações de recuperação/replay; `ctl.runtime_temporal_policy`/`ctl.runtime_temporal_window` existem nas migrations. Contudo, `RuntimeTemporalPolicy.Cadence` atual só possui `CIVIL_DAY` e `CIVIL_MONTH`; o planner só emite janelas cujo fechamento/vencimento já ocorreu. A extração recorrente do dia corrente e o supervisor produtivo de pendências não estão qualificados por isso. Preparar a integração produtiva desses controles, mantendo as regras e limites de replay do laboratório, sem apresentar o componente existente como agendamento horário já implementado.

Testes obrigatórios antes de P5:

| Cenário | Resultado a provar |
| --- | --- |
| Dois ciclos consecutivos extraindo na origem a janela D‑7..D0 | Alterações atualizadas, sem perda de ocorrências nem duplicação indevida |
| Parada de 24 horas | Cobertura de todo o intervalo pendente após retorno |
| Parada maior que sete dias | Recuperação também dos dias anteriores à janela normal |
| Falha numa página ou entidade | Intervalo pendente, fronteira sem avanço indevido e última publicação íntegra preservada |
| Dia posterior completo, anterior incompleto | Fronteira contínua para antes da lacuna; recuperação do intervalo que falta |
| Queda após gravar e antes do recibo/publicação | Conciliação do resultado antes da repetição; reexecução sem duplicação indevida |
| Dia corrente e mudança de dia/fuso | Corte registrado, intervalos sem buracos e dia aberto não marcado como fechado |
| Ciclo maior que 60 minutos ou backlog grande | Sem sobreposição, limites preservados e atraso/pendências visíveis, com progresso retomável |
| Atualização antiga fora dos sete dias | Política de detecção/sweep ou consulta por atualização explicitada; não prometer capturá-la apenas pela janela de data de negócio |
| Mudança na origem e visibilidade no dashboard | Tempo total medido contra a meta de uma hora, incluindo extração/materialização/publicação |

Esses são critérios a implementar e executar, não testes novos aprovados nesta rodada documental. Cadência e janelas são requisitos definidos; capacidade real e cobertura fora da data de negócio dependem da prova de origem e do caminho produtivo.

## Marco para iniciar e marco para entrar em produção

**Início da primeira implementação:** P0 fechado no escopo da ação concreta, com nome e atributos do alvo, pacote e dependências conhecidos, sem efeitos proibidos pendentes de execução automática. Criar o banco não liga o ETL nem muda o dashboard.

**Entrada em produção:** P1–P6 com evidências próprias aplicáveis, pendências bloqueantes resolvidas, publicação não vazia e coerente, consumidor testado, rotina operacional e recuperação ensaiadas. Preparação documental, 76 verificações anteriores ou aceite offline de conteúdo não substituem esses marcos.

Esta rodada somente consolida prontidão e sequência. Nenhum código de aplicação, configuração, SQL versionado, banco, identidade, permissão, agendamento, serviço ou rota foi alterado. O estado dos bancos citado aqui vem da avaliação 0755; não foi feita nova consulta SQL nesta rodada.
