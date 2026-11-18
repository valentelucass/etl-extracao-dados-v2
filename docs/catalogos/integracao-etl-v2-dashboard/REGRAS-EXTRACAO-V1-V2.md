# Regras de extração na origem — conferência V1 × V2

**Evolução 0757:** implementação da função/comando de [recuperação explícita de períodos](RECUPERACAO-PERIODOS.md), autorizada pelo usuário depois desta conferência. Percorre partições históricas além de D-7, concilia antes de repetir e só avança após publicação durável. BACKFILL por data de negócio, sem filtro de atualização adicional; não modifica a janela normal ou os templates. Acionamento automático e prova física produtiva seguem pendentes. O levantamento e os 21 testes V1 abaixo permanecem históricos e inalterados.

Revisão 0756, em continuidade ao pedido do usuário de conferir as regras existentes, inclusive no V1. **Extração** significa realizar novas consultas na origem e receber os dados novamente para atualizar o destino; não significa apenas ler o que já está gravado no SQL.

## Resultado da conferência

O V1 implementa a rotina descrita pelo usuário, com detalhes relevantes: configuração padrão de intervalo de 60 minutos e janela inclusiva D‑7..D0, além de reconciliação diária e fila persistida de pendências. Os 60 minutos são contados após o fim do ciclo, e D‑7..D0 cobre oito datas civis, não sete datas nem exatamente 168 horas.

O V2 tem componentes de extração, planejamento temporal e reconciliação, mas a composição produtiva equivalente não está comprovada ou habilitada. Não criar outra regra por suposição nem apresentar os componentes de laboratório como um serviço produtivo horário já pronto.

## Regras verificadas no V1

| Regra | Comportamento confirmado no código/configuração | Referência no V1 |
| --- | --- | --- |
| Configuração padrão | `etl.daemon.intervalo.minutos=60`; `etl.intradia.lookback.offset.dias=7` | `src/main/resources/config.properties:182` e `:184`; `ConfigEtl.java:72` e `:86` |
| Próxima execução | O próximo início é calculado como `fim.plusMinutes(intervaloMinutos)`, após extração, materialização e reconciliação | `comandos/cli/extracao/daemon/LoopDaemonRunHandler.java:344` |
| Janela normal | Início da consulta = data de referência menos sete dias; fim = data de referência, inclusive | `plataforma/auditoria/aplicacao/ExecutionWindowPlanner.java:115` |
| Entidades da janela | Coletas, Fretes, Manifestos, Cotações, Localizador, Faturas, Contas a Pagar, Inventário, Sinistros e Raster usam a strategy intradia no planner do fluxo completo | `ExecutionWindowPlanner.java:57` |
| Usuários é exceção | Usa o dia do watermark confirmado; sem watermark, fallback de 90 dias; a consulta é incremental de usuários | `features/usuarios/aplicacao/UsuariosExecutionWindowStrategy.java:15` e `:26` |
| Completude e confirmação | Fluxo completo só confirma entidades com auditoria da execução, `apiCompleta()` e status confirmável | `aplicacao/extracao/FluxoCompletoUseCase.java:677` e `:724` |
| Watermark antigo | Afeta início de confirmação; **não amplia a consulta normal D‑7..D0** | `ExecutionWindowPlanner.java:112–142`; teste `deveManterConsultaD7MesmoComWatermarkMaisAntigo` |
| Reconciliação diária | Agenda D‑1 quando a data diária ainda não foi agendada. A rotina agenda ontem, sem um laço que gere todos os dias que se passaram durante uma parada | `comandos/cli/extracao/reconciliacao/LoopReconciliationService.java:151` |
| Alvos e limite | Onze alvos explícitos por data; padrão de duas tentativas de reconciliação por ciclo; ordem por data/API/entidade | `LoopReconciliationService.java:336`; `ConfigLoop.java:29`; `config.properties:307` |
| Falha do ciclo | Agenda datas desde o início do ciclo menos um dia até o fim do ciclo, somente para entidades/API identificadas na falha | `LoopReconciliationService.java:246`; `config.properties:312` |
| Persistência da fila | Estado em arquivo Properties, com `pending_targets`, data agendada, última data de sucesso e erro; alvo só é removido após a conclusão assíncrona | `LoopReconciliationStateStore.java:37`; `LoopReconciliationService.java:183` |
| Falha sem escopo | Não cria pendência genérica quando não identifica entidade/API. Pendências legadas apenas por data também são ignoradas pelo carregador | `LoopReconciliationService.java:256`; `LoopReconciliationStateStore.java:81` |
| Fechamento mensal | No primeiro dia do mês, tenta extrair o mês anterior por intervalo; registra estado da competência e evita repetir sucesso | `LoopDaemonRunHandler.java:440–479` |
| Complementos históricos | O fluxo também tem backfill/hidratação referencial de Coletas; os registros operacionais documentam reconciliação histórica e expurgo lógico separados da rotina intradia | `FluxoCompletoUseCase.java:146` e estado/runbooks do V1; não verificado como serviço ativo nesta rodada |

As referências abreviadas da tabela pertencem a `C:\Users\suporte\Documents\projetos\script-etl-automacao\src\main\java\br\com\extrator`, salvo indicação de `resources`, teste ou outro caminho. O inventário de evidência inclui arquivos/hashes/linhas para reprodução.

**Exemplo de contagem:** no dia 12, D‑7 é o dia 5; a consulta abrange os dias 5, 6, 7, 8, 9, 10, 11 e 12. Não substituir a regra atual por início no dia 6 para chamar o resultado de “sete dias” sem explicitar a mudança.

**Exemplo de intervalo:** se um ciclo termina às 10h20, o próximo é previsto para 11h20, não necessariamente às 11h00. A duração do trabalho se soma ao intervalo entre inícios. Manter isso separado da meta desejada de atualização em aproximadamente uma hora.

## Recuperação após parada: alcance real e requisito do usuário

Uma parada de um dia normalmente fica dentro da janela normal D‑7..D0, então os dados desse dia voltam a ser extraídos pela consulta regular. Pendências já persistidas também continuam na fila.

Uma parada longa é diferente. Se o último ciclo foi no dia 1 e o ETL retorna no dia 12, a consulta normal começa no dia 5. O planner não amplia essa consulta a partir do watermark antigo; a reconciliação diária agenda o dia 11, não automaticamente os dias 2, 3 e 4. O simples fato de existir watermark e reconciliação não comprova cobertura desses dias. A extração por intervalo pode recuperar escopos antigos explicitamente, mas não foi observado no caminho automático analisado um preenchimento integral de toda lacuna de parada longa.

O campo `lastSuccessfulReconciliationDate` guarda a maior data reconciliada com sucesso; não equivale a uma fronteira contínua de todas as entidades. A fila pode ainda conter alvos anteriores ou outras entidades do mesmo dia. No V2, a retomada deve considerar as lacunas e escopos, não usar esse máximo como prova de cobertura integral.

O requisito novo do usuário para o V2 é cobrir **todo período pendente**, inclusive anterior à janela normal, sem avançar a cobertura sobre falha ou intervalo não extraído. Isso deve ser um critério explícito do supervisor produtivo, reaproveitando os controles existentes e preservando o contrato de origem. Não copiar a limitação do caminho normal do V1 como se fosse garantia de recuperação integral.

O fechamento mensal também depende de a rotina passar pelo primeiro dia do mês. O código analisado retorna nos outros dias; não demonstra uma fila automática de competências perdidas numa parada que atravesse essa data. Incluir esse caso no desenho e no ensaio de recuperação.

## Datas de negócio e alterações

A janela de sete dias para trás é uma seleção por campo específico de cada fonte, não uma busca universal de toda alteração feita na última semana:

| Entidade | Fonte principal V1 | Campo de seleção identificado no contrato |
| --- | --- | --- |
| Coletas | GraphQL | `requestDate` — o helper confirma consulta por data de solicitação |
| Fretes | GraphQL | `serviceAt`, que aceita intervalo no catálogo GraphQL; manter a diferença entre data do serviço e atualização |
| Manifestos | Data Export 6399 | `manifests.service_date` |
| Cotações | Data Export 6906 | `quotes.requested_at` |
| Localizador | Data Export 8656 | `freights.service_at` |
| Contas a Pagar | Data Export 8636 | `accounting_debits.issue_date`, com controles específicos do template |
| Faturas por Cliente | Data Export 4924 | `freights.service_at` |
| Inventário | Data Export 10633 | `check_in_orders.started_at` |
| Sinistros | Data Export 6392 | `insurance_claims.opening_at_date` |

Alterar hoje um registro com data de negócio de muitos meses atrás não garante sua presença na consulta D‑7..D0. Preservar as rotinas complementares aplicáveis e definir a cobertura de alterações antigas por contrato, reconciliação histórica ou filtro de atualização efetivamente suportado. Não trocar globalmente datas de negócio por `updated_at`.

## Comparação com o V2 existente

- O ADR0007 determina uma execução por disparo e agendador externo. A decisão arquitetural difere do daemon V1, mas pode preservar suas regras de dados.
- `RuntimeTemporalPolicy.Cadence` admite dia/mês; `RuntimeTemporalPlanner` gera janelas fechadas e aplica lookback ao início da extração. Isso não demonstra um disparador produtivo de 60 minutos nem extração intradia atual pronta.
- `RuntimeTemporalBinding` mantém `businessStart/businessEnd` na partição de negócio. `RuntimeOperationalRequest` só adiciona intervalo de atualização sobreposto quando existe binding temporal e modo INCREMENTAL. **Lookback de atualização e consulta de todas as datas D‑7..D0 não são a mesma regra.**
- No catálogo genérico `DataExportTemplate`, Coletas/Fretes têm filtro de atualização contratado; os outros sete têm `updatedAtFilter=null` e são tratados como `laboratoryBackfillOnly`. As nove entidades usam Data Export nas rotas principais V2 identificadas, enquanto Users é apoio GraphQL. Não habilitar modo incremental para todas por convenção.
- Há diferença entre o caminho genérico e a extração independente já qualificada: o `DataExportPageRequest.filters()` genérico inclui `created_at` adicional para Contas a Pagar, enquanto `ContasPagarIndependentRequest` explicita `createdAtScopeEnforced=false` e extração de ocorrências pela emissão. A integração produtiva precisa preservar o contrato qualificado aplicável, sem reintroduzir filtro que exclua ocorrências.
- `RuntimeTemporalCoordinator` não avança a fronteira contínua sobre lacunas e distingue início, recuperação e replay explícito. Sua política atual tem limites por rodada e estados que exigem replay explícito. O supervisor produtivo automático deve ser integrado com autoridade/limites próprios; o componente isolado não prova recuperação agendada já operacional.

## Validação executada nesta rodada

Executados offline no V1, com Maven já instalado e quota de memória da VM:

- `ExecutionWindowPlannerTest`: **12 testes**, zero falhas/erros/skips.
- `LoopReconciliationServiceTest`: **9 testes**, zero falhas/erros/skips.

Os relatórios são novos nesta execução, com timestamps e hashes em `outputs/testes-regras-v1-0756.json`. Incluem teste explícito de consulta D‑7 mesmo com watermark mais antigo, manutenção de pendência após falha, limite por ciclo, escopos específicos e espera de conclusão assíncrona. Usam relógios, auditoria e executores controlados: não são uma extração real na API, teste SQL ou prova de daemon produtivo ativo.

A primeira tentativa não iniciou o Maven por forma de passagem dos argumentos ao processo; foi preservada no recibo `testes-regras-v1-0756-attempt1.json` e corrigida por lançador PowerShell sob a mesma proteção. Nenhuma execução de fonte foi usada como teste. O Maven gerou somente artefatos de build/teste; os fontes e configurações do V1 não foram editados.

Foram conferidas apenas as chaves não secretas de intervalo/lookback/reconciliação nos arquivos `.env` locais previstos e nas variáveis acessíveis; não foram encontradas sobreposições dessas chaves nessa conferência. A precedência em `ConfigSource` inclui system properties, `.env`, ambiente e properties. Não foram inspecionadas as propriedades de uma JVM produtiva em execução nem o conteúdo do JAR implantado; os valores confirmados são do código/configuração deste checkout, não uma certificação do processo em produção.

## Consequência para as implementações

Usar esta conferência como base do P0. Manter “extração na origem” na documentação da rotina. Declarar a janela D‑7..D0 e a semântica do intervalo de 60 minutos com precisão; preservar os contratos de fonte já qualificados. Integrar o supervisor produtivo de recuperação, incluindo paradas maiores que a janela e fechamento mensal perdido, com cobertura contínua e publicação coerente. Esses comportamentos ainda precisam de testes integrados no novo alvo, sem alterar o dashboard quanto ao seu período padrão do último mês.
