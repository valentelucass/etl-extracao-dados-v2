# Trilha completa de chats e seleção de modelo GPT-5.6

Este documento materializa todas as fatias de chat atualmente deriváveis do [`STATES.md`](../../STATES.md). O `STATES.md` continua sendo a fonte de verdade para dependências, critérios de aceite e evidências; esta trilha é o índice operacional completo para escolher e fechar cada chat.

A trilha é completa para o roadmap conhecido em 2026-09-04. Evidência externa, drift de fornecedor ou uma decisão de negócio pode criar uma nova fatia, mas nenhum escopo já conhecido pode ficar escondido em expressões como “repetir por entidade” ou “uma saída por vez”.

## Mensagem única para abrir um chat

Copie uma linha completa com `STATUS=AGORA` ou `STATUS=CANDIDATO` e envie:

```text
Leia integralmente STATES.md e docs/runbooks/trilha-de-chats-gpt-5-6.md.
Execute exatamente esta linha da trilha:

COLE_AQUI_A_LINHA_COMPLETA_DA_TRILHA
```

O Codex deve confirmar o repositório `etl-extracao-dados-v2`, reler `AGENTS.md`, `STATES.md`, esta trilha e `../CONTEXTO_GLOBAL.md`, validar as dependências e executar somente a fatia copiada.

## Painel rápido

| Campo | Valor atual |
| --- | --- |
| Última sincronização | 2026-09-04 15:10:43 -03:00 |
| Último bloco funcional concluído | Bloco 30 — V2-009b — identidade e grão 6906 |
| Próximo bloco oficial | **Bloco 24 — V2-009b — identidade e grão 6399** |
| Modelo do próximo chat | **GPT-5.6 Sol — Ultra** |
| Fonte de verdade | **STATES.md** |
| Checkboxes no STATES.md | **88: 30 concluídos e 58 pendentes** |
| Fatias abertas materializadas nesta trilha | **213** |
| Rede no próximo bloco | **Proibida; V2-041 continua em hold** |

O número de fatias da trilha é maior que o número de checkboxes do `STATES.md` porque tarefas repetíveis foram expandidas por entidade e saída. Isso não duplica trabalho: ao concluir uma fatia que ainda não tem checkbox próprio no estado, o chat deve criar o subcheckbox exato sob a tarefa-pai e marcá-lo nos dois arquivos.

## Estados permitidos

- `CONCLUIDO`: escopo aceito no `STATES.md`, com evidência registrada.
- `AGORA`: única linha autorizada como próximo bloco oficial.
- `CANDIDATO`: pode ser repriorizada pelo usuário, mas somente se as dependências estiverem satisfeitas.
- `EXTERNAL_HOLD`: exige artefato, autorização ou ação humana nova.
- `CONDICIONAL`: só existe se a decisão precedente habilitar o escopo.
- `MARCO_CONSOLIDADO`: histórico anterior sem reconstrução artificial de números de bloco.

Linhas abertas permanecem `[ ]`. Uma tarefa parcial, `READY_FOR_BASELINE_COMMIT`, `FOUNDATION_OFFLINE_COMPLETE` ou `IMPLEMENTADA_EM_SHADOW_CONSUMER_CONTRACT_PENDING` não recebe `[x]` para o pai que ainda tem aceite pendente.

## Seleção de modelo

A [documentação oficial do GPT-5.6 Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra) o descreve como o modelo de equilíbrio entre inteligência e custo, com raciocínio até `max` e suporte às ferramentas de código. A [documentação oficial do GPT-5.6 Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol) o posiciona como flagship para trabalho profissional complexo.

### Terra xhigh

Usar para execução local, limitada e repetível quando identidade, cardinalidade e regra sensível já estiverem congeladas:

- contratos offline sem decidir identidade, dinheiro ou reducer;
- mapper, fixture, validator, migration e testes contra desenho aceito;
- vertical simples ou execução mecânica de vertical já decidida;
- caracterização V2-012a, dimensões derivadas e gate V2-050;
- correção mecânica delimitada.

### Sol ultra

Usar para decisões cuja falha possa alterar identidade, dinheiro, estado transacional, segurança, contrato consumidor ou operação:

- identidade/grão complexos, aliases, rekey e cardinalidade;
- reducers financeiros, fiscais ou de expansão;
- lease, recovery, checkpoint, publicação atômica e concorrência;
- relações entre entidades, bootstrap, sweep, paridade e E2E;
- contratos SQL consumidores, fatos, release e cutover.

Quando uma tarefa mistura decisão e execução, a decisão fica em Sol e a implementação repetível subsequente fica em Terra. Terra deve escalar antes de improvisar qualquer decisão marcada como risco Sol.

## Sincronização obrigatória com STATES.md

1. Antes do trabalho, validar a linha contra o estado canônico.
2. Ao concluir, atualizar primeiro o `STATES.md`: checkbox exato, evidência, testes, riscos e próximo passo.
3. Se a fatia ainda não tiver subcheckbox próprio no `STATES.md`, criá-lo sob a tarefa-pai; nunca marcar o pai agregado por uma única entidade ou saída.
4. Somente depois trocar a mesma linha desta trilha para `[x] STATUS=CONCLUIDO`.
5. Se ficou parcial, manter `[ ]` nos dois arquivos e registrar o restante.
6. Manter exatamente uma linha aberta com `STATUS=AGORA`.
7. Número de bloco é atribuído quando a linha é promovida. Não renumerar Blocos 1–26 nem fabricar o histórico ausente.
8. Manutenção desta trilha não consome bloco funcional.

### Cobertura dos checkboxes agregados

Os itens abaixo permanecem abertos no `STATES.md`, mas não criam um chat adicional só para marcar o pai. O pai fecha quando todas as rotas e evidências indicadas estiverem aceitas; se uma fatia condicional virar `NOT_APPLICABLE`, o aceite dessa decisão também precisa constar no estado.

| Checkbox no STATES.md | Cobertura integral na trilha |
| --- | --- |
| V2-016 | V2-016a já entregue localmente; G02 fecha V2-016b e o agregado após baseline/remote autorizados. |
| V2-017a | G03 registrou o baseline local `c59489cc70be9c113cdf444118dd1342c0b13903` e fechou V2-017/V2-017a; V2-040 ainda confirma a matriz final. |
| V2-015 | G04 fecha V2-015a, V2-015b já foi concluída, Z03 fecha V2-015c e G05 fecha V2-015d. |
| V2-042 | V2-042a já foi concluída; G06 e G07 fecham V2-042b/c e então o pai. |
| V2-045 | V2-045a já foi concluída; G09 fecha V2-045b e então o pai. |
| V2-022 | P02 fecha o runtime offline V2-022a; G08 fecha V2-022b e então o pai. |
| V2-025 | Histórico fecha V2-025a e as sete fatias ESL de V2-025b; G11 fecha V2-025d e RAS-02 cobre V2-025c se Raster for mantido. |
| V2-009 | Histórico fecha V2-009a; P01/P06–P11 fecham V2-009b, RAS-03 cobre V2-009c e as execuções verticais abaixo incorporam V2-009d. |
| V2-009d | Usuários já está aplicado; V02, V04, V05, V07, V09, V10, V12, V14 e V16 aplicam constraints/enforcement nas nove ESL; RAS-05 cobre Raster se mantido. |
| V2-035 | G10 fecha V2-035a; a fatia interna de Usuários já foi entregue e D01–D05 fecham as outras dimensões de V2-035b. |
| V2-046 | R01 e R02 fecham V2-046a/b e então o pai. |
| V2-012 | Q-*-01/Q-*-03 cobrem V2-012a/b das dez entidades, RAS-06/RAS-08 cobrem Raster condicional e todas as rotas O-*-01 cobrem V2-012c por saída. |
| V2-034 | RAS-01 decide; RAS-02–RAS-11 executam a ramificação “manter”. Na ramificação “retirar”, o aceite formal encerra ou marca como não aplicáveis as fatias posteriores. |
| V2-039 | Z01 e Z02 fecham V2-039a/b e então o pai. |
| V2-048 | V2-048a já foi concluída; Z04 fecha V2-048b e então o pai. |

## Histórico executado

Cada checkbox `[x]` abaixo espelha exatamente um checkbox `[x]` canônico do `STATES.md`. Para os Blocos 1–21, o número individual não foi reconstruído: essas linhas registram tarefa concluída, não alegam correspondência de um chat por checkbox.

- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-016a | ESCOPO=baseline local versionável | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-015b | ESCOPO=gate progressivo de dados | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-018 | ESCOPO=configuração tipada, bootstrap e composition root | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-019 | ESCOPO=fundação de schema e baseline Flyway local | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-020 | ESCOPO=control plane, auditoria, lock, replay e watermarks | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-020a | ESCOPO=fundação offline fail-closed do control plane | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-020b | ESCOPO=protocolo atômico de publicação, watermark e recovery | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-021 | ESCOPO=kernel de staging e promoção set-based | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-021a | ESCOPO=staging, quarantine e candidate set fail-closed | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-021b | ESCOPO=aplicação atômica do candidate set ao core | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-042a | ESCOPO=threat model e boundary provider-neutral deny-all | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-043 | ESCOPO=resiliência e política de falha compartilhadas | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-044 | ESCOPO=drift de contrato antes da promoção | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-045a | ESCOPO=lifecycle local bounded e fail-closed | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-023 | ESCOPO=observabilidade, integridade e Data Quality fail-closed | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-024 | ESCOPO=adaptador GraphQL transitório | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-025a | ESCOPO=contratos da primeira onda e Usuários | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCO=22 | TAREFA=V2-025b/6399 | ESCOPO=contrato offline Manifestos | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCO=23 | TAREFA=V2-025b/6906 | ESCOPO=contrato offline Cotações | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCO=25 | TAREFA=V2-025b/8656 | ESCOPO=contrato offline Localização de Cargas | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCO=26 | TAREFA=V2-025b/10633 | ESCOPO=contrato offline Inventário | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | ROTA=P03 | BLOCO=27 | TAREFA=V2-025b/8636 | ESCOPO=contrato offline Contas a Pagar | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | ROTA=P04 | BLOCO=28 | TAREFA=V2-025b/4924 | ESCOPO=contrato offline Faturas por Cliente | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | ROTA=P05 | BLOCO=29 | TAREFA=V2-025b/6392 | ESCOPO=contrato offline Sinistros | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | ROTA=P06 | BLOCO=30 | TAREFA=V2-009b/6906 | ESCOPO=identidade e grão 6906 - Cotações | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | ROTA=G03 | TAREFA=V2-017a | ESCOPO=matriz de portabilidade versionada localmente | SHA=c59489cc70be9c113cdf444118dd1342c0b13903 | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-009a | ESCOPO=identidade e grão da primeira onda | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-033 | ESCOPO=Usuários e histórico em sombra | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md
- [x] STATUS=CONCLUIDO | BLOCOS=1-21 | TAREFA=V2-048a | ESCOPO=desenho database-wide, unidade e fences | NUMERO_EXATO=NAO_RECONSTRUIDO | EVIDENCIA=STATES.md

## Fila completa — preparação e identidade

- [ ] STATUS=AGORA | ROTA=P01 | BLOCO=24 | TAREFA=V2-009b | ESCOPO=identidade e grão 6399 - Manifestos | MODELO=SOL_ULTRA | DEPENDE=V2-025b/6399 | LIMITE=sem V2-026, rede ou banco
- [ ] STATUS=CANDIDATO | ROTA=P02 | TAREFA=V2-022a | ESCOPO=runtime e orquestrador offline deny-all | MODELO=SOL_ULTRA | DEPENDE=V2-018+V2-020+V2-021+V2-042a+V2-043 | LIMITE=sem adapter positivo, rede, schedule ou deploy
- [ ] STATUS=CANDIDATO | ROTA=P07 | TAREFA=V2-009b | ESCOPO=identidade e grão 8656 - Localização | MODELO=SOL_ULTRA | DEPENDE=V2-025b/8656 | RISCO=chave publicada diverge da ordenação e alto volume
- [ ] STATUS=CANDIDATO | ROTA=P08 | TAREFA=V2-009b | ESCOPO=identidade e grão 10633 - Inventário | MODELO=SOL_ULTRA | DEPENDE=V2-025b/10633 | RISCO=raiz agregada e filhos expandidos
- [ ] STATUS=CANDIDATO | ROTA=P09 | TAREFA=V2-009b | ESCOPO=identidade e grão 8636 - Contas a Pagar | MODELO=SOL_ULTRA | DEPENDE=V2-025b/8636 | RISCO=raiz ausente, parcela e duplicação financeira
- [ ] STATUS=CANDIDATO | ROTA=P10 | TAREFA=V2-009b | ESCOPO=identidade e grão 4924 - Faturas por Cliente | MODELO=SOL_ULTRA | DEPENDE=V2-025b/4924 | RISCO=source ID, título, CT-e/NFS-e, rekey e fretes
- [ ] STATUS=CANDIDATO | ROTA=P11 | TAREFA=V2-009b | ESCOPO=identidade e grão 6392 - Sinistros | MODELO=SOL_ULTRA | DEPENDE=V2-025b/6392 | RISCO=raiz, relações e valores financeiros

## Fila completa — governança e dependências externas

Essas linhas fazem parte da trilha completa, mas não substituem ação humana nem podem ser selecionadas sem o input indicado.

- [ ] STATUS=EXTERNAL_HOLD | ROTA=G01 | TAREFA=V2-041 | ESCOPO=rotação e invalidação de segredos expostos | MODELO=SOL_ULTRA+ACAO_HUMANA | DESBLOQUEIA=rede+V2-025d+release
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G02 | TAREFA=V2-016b | ESCOPO=remote, branch protection, CODEOWNERS e CI real | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=remote+autorização
- [x] STATUS=CONCLUIDO | ROTA=G03 | TAREFA=V2-017 | ESCOPO=registrar SHA do baseline de portabilidade e fechar V2-017/V2-017a | SHA=c59489cc70be9c113cdf444118dd1342c0b13903 | MODELO=TERRA_XHIGH | EVIDENCIA=STATES.md
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G04 | TAREFA=V2-015a | ESCOPO=reexecutar gates no conjunto exato e registrar SHA baseline | MODELO=TERRA_XHIGH | DEPENDE=primeiro commit autorizado
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G05 | TAREFA=V2-015d | ESCOPO=política bloqueante e baseline de vulnerabilidades | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=feed+política+owner
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G06 | TAREFA=V2-042b | ESCOPO=inputs externos de identidade, principals e governança | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=authority+provider+principals
- [ ] STATUS=CANDIDATO | ROTA=G07 | TAREFA=V2-042c | ESCOPO=adapter positivo de autorização e provas | MODELO=SOL_ULTRA | DEPENDE=V2-042b
- [ ] STATUS=CANDIDATO | ROTA=G08 | TAREFA=V2-022b | ESCOPO=dispatcher operacional ligado ao adapter positivo | MODELO=SOL_ULTRA | DEPENDE=V2-022a+V2-042b+V2-042c
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G09 | TAREFA=V2-045b | ESCOPO=retenção, archive, WORM, backup/restore e ACL produtivos | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=owners+infra
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G10 | TAREFA=V2-035a | ESCOPO=baseline produtivo e ratificação das referências governadas | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=exports+algoritmos+owners
- [ ] STATUS=EXTERNAL_HOLD | ROTA=G11 | TAREFA=V2-025d | ESCOPO=rodada cURL final das nove entidades ESL | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=V2-041+janela+teto reconfirmados

## Fila completa — implementação das verticais

A fatia `DECISAO` congela contrato de domínio, schema, reducer e invariantes. A fatia `EXECUCAO` implementa o desenho congelado e não pode reabri-lo silenciosamente.

- [ ] STATUS=CANDIDATO | ROTA=V01 | TAREFA=V2-010 | FATIA=DECISAO | ESCOPO=Coletas - domínio, presença, frescor, status e schema | MODELO=SOL_ULTRA | DEPENDE=V2-022a+V2-009a
- [ ] STATUS=CANDIDATO | ROTA=V02 | TAREFA=V2-010 | FATIA=EXECUCAO | ESCOPO=Coletas - mapper, staging, promoção, migration e testes | MODELO=TERRA_XHIGH | DEPENDE=V01 | ESCALAR=decisão de ausência, identidade ou relação
- [ ] STATUS=CANDIDATO | ROTA=V03 | TAREFA=V2-026 | FATIA=DECISAO | ESCOPO=Manifestos - raiz, filhos, frescor e reducers MAN-01/MAN-02/MAN-04/MAN-07 | MODELO=SOL_ULTRA | DEPENDE=P01+V2-022a
- [ ] STATUS=CANDIDATO | ROTA=V04 | TAREFA=V2-026 | FATIA=EXECUCAO | ESCOPO=Manifestos - mapper, staging/core, migration, limites Unicode e testes | MODELO=TERRA_XHIGH | DEPENDE=V03 | ESCALAR=conflito de reducer ou cardinalidade
- [ ] STATUS=CANDIDATO | ROTA=V05 | TAREFA=V2-027 | ESCOPO=Cotações 6906 completa em sombra | MODELO=TERRA_XHIGH | DEPENDE=P06+V2-022a | ESCALAR=tarifa ou identidade divergente
- [ ] STATUS=CANDIDATO | ROTA=V06 | TAREFA=V2-029 | FATIA=DECISAO | ESCOPO=Contas a Pagar - raiz/parcela, frescor, financeiro e reducers | MODELO=SOL_ULTRA | DEPENDE=P09+V2-022a
- [ ] STATUS=CANDIDATO | ROTA=V07 | TAREFA=V2-029 | FATIA=EXECUCAO | ESCOPO=Contas a Pagar - mapper, staging/core, migration e testes | MODELO=TERRA_XHIGH | DEPENDE=V06 | ESCALAR=moeda, valor ou cardinalidade
- [ ] STATUS=CANDIDATO | ROTA=V08 | TAREFA=V2-011 | FATIA=DECISAO | ESCOPO=Fretes - presença, frescor, performance, financeiro e sidecars | MODELO=SOL_ULTRA | DEPENDE=V2-046a+V2-010+V2-022a
- [ ] STATUS=CANDIDATO | ROTA=V09 | TAREFA=V2-011 | FATIA=EXECUCAO | ESCOPO=Fretes - mapper, stagings, promoção, migration e testes | MODELO=TERRA_XHIGH | DEPENDE=V08 | ESCALAR=precedência temporal, CT-e ou regressão
- [ ] STATUS=CANDIDATO | ROTA=V10 | TAREFA=V2-028 | ESCOPO=Localização 8656 completa em sombra | MODELO=TERRA_XHIGH | DEPENDE=P07+V2-011+V2-022a | ESCALAR=plano de alto volume ou chave divergente
- [ ] STATUS=CANDIDATO | ROTA=V11 | TAREFA=V2-030 | FATIA=DECISAO | ESCOPO=Faturas por Cliente - fiscal, título, crosswalk, frescor e filhos | MODELO=SOL_ULTRA | DEPENDE=P10+V2-011+V2-022a
- [ ] STATUS=CANDIDATO | ROTA=V12 | TAREFA=V2-030 | FATIA=EXECUCAO | ESCOPO=Faturas por Cliente - mapper, staging/core, migration e testes | MODELO=TERRA_XHIGH | DEPENDE=V11 | ESCALAR=CT-e/NFS-e, valor ou rekey
- [ ] STATUS=CANDIDATO | ROTA=V13 | TAREFA=V2-031 | FATIA=DECISAO | ESCOPO=Inventário - raiz, invoices_mapping, reducer, parsing e comprovante | MODELO=SOL_ULTRA | DEPENDE=P08+V2-011+V2-022a
- [ ] STATUS=CANDIDATO | ROTA=V14 | TAREFA=V2-031 | FATIA=EXECUCAO | ESCOPO=Inventário - mapper, staging/core, migration e testes | MODELO=TERRA_XHIGH | DEPENDE=V13 | ESCALAR=cardinalidade ou multiplicação
- [ ] STATUS=CANDIDATO | ROTA=V15 | TAREFA=V2-032 | FATIA=DECISAO | ESCOPO=Sinistros - raiz, filhos, horas, financeiro e frescor | MODELO=SOL_ULTRA | DEPENDE=P11+V2-011+V2-022a
- [ ] STATUS=CANDIDATO | ROTA=V16 | TAREFA=V2-032 | FATIA=EXECUCAO | ESCOPO=Sinistros - mapper, staging/core, migration e testes | MODELO=TERRA_XHIGH | DEPENDE=V15 | ESCALAR=valor, timezone ou cardinalidade

## Fila completa — relações entre entidades

- [ ] STATUS=CANDIDATO | ROTA=R01 | TAREFA=V2-046a | ESCOPO=Manifesto para Coleta e backlog referencial | MODELO=SOL_ULTRA | DEPENDE=V2-010+V2-026+V2-012a/COLETAS+V2-012a/MANIFESTOS+V2-047/COLETAS+V2-047/MANIFESTOS
- [ ] STATUS=CANDIDATO | ROTA=R02 | TAREFA=V2-046b | ESCOPO=Coleta para Frete e recomputação do backlog | MODELO=SOL_ULTRA | DEPENDE=V2-046a+V2-011+V2-047/FRETES

## Fila completa — dimensões derivadas restantes

A dimensão interna de Usuários já está no marco concluído; `pub.vw_dim_usuarios` permanece na seção de contratos SQL.

- [ ] STATUS=CANDIDATO | ROTA=D01 | TAREFA=V2-035b | ESCOPO=dimensão Filiais em sombra | MODELO=TERRA_XHIGH | DEPENDE=V2-011+V2-026+V2-029+V2-030 | ESCALAR=cardinalidade ou referência divergente
- [ ] STATUS=CANDIDATO | ROTA=D02 | TAREFA=V2-035b | ESCOPO=dimensão Clientes em sombra | MODELO=TERRA_XHIGH | DEPENDE=V2-010+V2-011+V2-030 | ESCALAR=grão ou deduplicação divergente
- [ ] STATUS=CANDIDATO | ROTA=D03 | TAREFA=V2-035b | ESCOPO=dimensão Veículos em sombra | MODELO=TERRA_XHIGH | DEPENDE=V2-026 | ESCALAR=placa/filial ambígua
- [ ] STATUS=CANDIDATO | ROTA=D04 | TAREFA=V2-035b | ESCOPO=dimensão Motoristas em sombra | MODELO=TERRA_XHIGH | DEPENDE=V2-026 | ESCALAR=nome/filial ou genéricos ambíguos
- [ ] STATUS=CANDIDATO | ROTA=D05 | TAREFA=V2-035b | ESCOPO=dimensão Plano de Contas em sombra | MODELO=TERRA_XHIGH | DEPENDE=V2-029 | ESCALAR=classificação financeira ambígua

## Fila completa — gates por entidade

Cada entidade percorre explicitamente `V2-012a → V2-047 → V2-012b → V2-013 → V2-050 → V2-038`. Relações aplicáveis entram antes da paridade core. Nenhuma linha de sweep pressupõe que o sweep será habilitado: o resultado pode ser `DISABLED`, `BLOCKED` ou `NOT_APPLICABLE`, desde que aceito no `STATES.md`.

- [ ] STATUS=CANDIDATO | ROTA=Q-USR-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Usuários | MODELO=TERRA_XHIGH | DEPENDE=V2-033+ORACULO_GRAPHQL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-USR-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Usuários | MODELO=SOL_ULTRA | DEPENDE=Q-USR-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-USR-03 | TAREFA=V2-012b | ESCOPO=paridade core Usuários | MODELO=SOL_ULTRA | DEPENDE=Q-USR-02
- [ ] STATUS=CANDIDATO | ROTA=Q-USR-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Usuários | MODELO=SOL_ULTRA | DEPENDE=Q-USR-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-USR-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Usuários | MODELO=TERRA_XHIGH | DEPENDE=Q-USR-03
- [ ] STATUS=CANDIDATO | ROTA=Q-USR-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Usuários | MODELO=SOL_ULTRA | DEPENDE=Q-USR-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-COL-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Coletas | MODELO=TERRA_XHIGH | DEPENDE=V2-010+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-COL-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Coletas | MODELO=SOL_ULTRA | DEPENDE=Q-COL-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-COL-03 | TAREFA=V2-012b | ESCOPO=paridade core Coletas | MODELO=SOL_ULTRA | DEPENDE=Q-COL-02+V2-046a+V2-046b
- [ ] STATUS=CANDIDATO | ROTA=Q-COL-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Coletas | MODELO=SOL_ULTRA | DEPENDE=Q-COL-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-COL-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Coletas | MODELO=TERRA_XHIGH | DEPENDE=Q-COL-03
- [ ] STATUS=CANDIDATO | ROTA=Q-COL-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Coletas | MODELO=SOL_ULTRA | DEPENDE=Q-COL-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-MAN-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Manifestos | MODELO=TERRA_XHIGH | DEPENDE=V2-026+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-MAN-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Manifestos | MODELO=SOL_ULTRA | DEPENDE=Q-MAN-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-MAN-03 | TAREFA=V2-012b | ESCOPO=paridade core Manifestos | MODELO=SOL_ULTRA | DEPENDE=Q-MAN-02+V2-046a
- [ ] STATUS=CANDIDATO | ROTA=Q-MAN-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Manifestos | MODELO=SOL_ULTRA | DEPENDE=Q-MAN-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-MAN-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Manifestos | MODELO=TERRA_XHIGH | DEPENDE=Q-MAN-03
- [ ] STATUS=CANDIDATO | ROTA=Q-MAN-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Manifestos | MODELO=SOL_ULTRA | DEPENDE=Q-MAN-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-COT-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Cotações | MODELO=TERRA_XHIGH | DEPENDE=V2-027+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-COT-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Cotações | MODELO=SOL_ULTRA | DEPENDE=Q-COT-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-COT-03 | TAREFA=V2-012b | ESCOPO=paridade core Cotações | MODELO=SOL_ULTRA | DEPENDE=Q-COT-02
- [ ] STATUS=CANDIDATO | ROTA=Q-COT-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Cotações | MODELO=SOL_ULTRA | DEPENDE=Q-COT-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-COT-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Cotações | MODELO=TERRA_XHIGH | DEPENDE=Q-COT-03
- [ ] STATUS=CANDIDATO | ROTA=Q-COT-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Cotações | MODELO=SOL_ULTRA | DEPENDE=Q-COT-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-CAP-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Contas a Pagar | MODELO=TERRA_XHIGH | DEPENDE=V2-029+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-CAP-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Contas a Pagar | MODELO=SOL_ULTRA | DEPENDE=Q-CAP-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-CAP-03 | TAREFA=V2-012b | ESCOPO=paridade core Contas a Pagar | MODELO=SOL_ULTRA | DEPENDE=Q-CAP-02
- [ ] STATUS=CANDIDATO | ROTA=Q-CAP-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Contas a Pagar | MODELO=SOL_ULTRA | DEPENDE=Q-CAP-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-CAP-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Contas a Pagar | MODELO=TERRA_XHIGH | DEPENDE=Q-CAP-03
- [ ] STATUS=CANDIDATO | ROTA=Q-CAP-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Contas a Pagar | MODELO=SOL_ULTRA | DEPENDE=Q-CAP-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-FRE-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Fretes | MODELO=TERRA_XHIGH | DEPENDE=V2-011+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-FRE-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Fretes | MODELO=SOL_ULTRA | DEPENDE=Q-FRE-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-FRE-03 | TAREFA=V2-012b | ESCOPO=paridade core Fretes | MODELO=SOL_ULTRA | DEPENDE=Q-FRE-02+V2-046b
- [ ] STATUS=CANDIDATO | ROTA=Q-FRE-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Fretes | MODELO=SOL_ULTRA | DEPENDE=Q-FRE-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-FRE-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Fretes | MODELO=TERRA_XHIGH | DEPENDE=Q-FRE-03
- [ ] STATUS=CANDIDATO | ROTA=Q-FRE-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Fretes | MODELO=SOL_ULTRA | DEPENDE=Q-FRE-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-LOC-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Localização | MODELO=TERRA_XHIGH | DEPENDE=V2-028+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-LOC-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Localização | MODELO=SOL_ULTRA | DEPENDE=Q-LOC-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-LOC-03 | TAREFA=V2-012b | ESCOPO=paridade core Localização | MODELO=SOL_ULTRA | DEPENDE=Q-LOC-02
- [ ] STATUS=CANDIDATO | ROTA=Q-LOC-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Localização | MODELO=SOL_ULTRA | DEPENDE=Q-LOC-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-LOC-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Localização | MODELO=TERRA_XHIGH | DEPENDE=Q-LOC-03
- [ ] STATUS=CANDIDATO | ROTA=Q-LOC-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Localização | MODELO=SOL_ULTRA | DEPENDE=Q-LOC-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-FAT-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Faturas por Cliente | MODELO=TERRA_XHIGH | DEPENDE=V2-030+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-FAT-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Faturas por Cliente | MODELO=SOL_ULTRA | DEPENDE=Q-FAT-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-FAT-03 | TAREFA=V2-012b | ESCOPO=paridade core Faturas por Cliente | MODELO=SOL_ULTRA | DEPENDE=Q-FAT-02
- [ ] STATUS=CANDIDATO | ROTA=Q-FAT-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Faturas por Cliente | MODELO=SOL_ULTRA | DEPENDE=Q-FAT-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-FAT-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Faturas por Cliente | MODELO=TERRA_XHIGH | DEPENDE=Q-FAT-03
- [ ] STATUS=CANDIDATO | ROTA=Q-FAT-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Faturas por Cliente | MODELO=SOL_ULTRA | DEPENDE=Q-FAT-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-INV-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Inventário | MODELO=TERRA_XHIGH | DEPENDE=V2-031+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-INV-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Inventário | MODELO=SOL_ULTRA | DEPENDE=Q-INV-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-INV-03 | TAREFA=V2-012b | ESCOPO=paridade core Inventário | MODELO=SOL_ULTRA | DEPENDE=Q-INV-02
- [ ] STATUS=CANDIDATO | ROTA=Q-INV-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Inventário | MODELO=SOL_ULTRA | DEPENDE=Q-INV-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-INV-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Inventário | MODELO=TERRA_XHIGH | DEPENDE=Q-INV-03
- [ ] STATUS=CANDIDATO | ROTA=Q-INV-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Inventário | MODELO=SOL_ULTRA | DEPENDE=Q-INV-05+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=Q-SIN-01 | TAREFA=V2-012a | ESCOPO=caracterização inicial Sinistros | MODELO=TERRA_XHIGH | DEPENDE=V2-032+ORACULO_ESL_AUTORIZADO
- [ ] STATUS=CANDIDATO | ROTA=Q-SIN-02 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Sinistros | MODELO=SOL_ULTRA | DEPENDE=Q-SIN-01+fonte_historica_autorizada
- [ ] STATUS=CANDIDATO | ROTA=Q-SIN-03 | TAREFA=V2-012b | ESCOPO=paridade core Sinistros | MODELO=SOL_ULTRA | DEPENDE=Q-SIN-02
- [ ] STATUS=CANDIDATO | ROTA=Q-SIN-04 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Sinistros | MODELO=SOL_ULTRA | DEPENDE=Q-SIN-03+prova_snapshot
- [ ] STATUS=CANDIDATO | ROTA=Q-SIN-05 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Sinistros | MODELO=TERRA_XHIGH | DEPENDE=Q-SIN-03
- [ ] STATUS=CANDIDATO | ROTA=Q-SIN-06 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Sinistros | MODELO=SOL_ULTRA | DEPENDE=Q-SIN-05+V2-022b

## Fila completa — Raster condicional

- [ ] STATUS=CONDICIONAL | ROTA=RAS-01 | TAREFA=V2-034a | ESCOPO=decidir manter ou retirar Raster | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=owner+consumidores
- [ ] STATUS=CONDICIONAL | ROTA=RAS-02 | TAREFA=V2-025c | ESCOPO=contrato Raster | MODELO=SOL_ULTRA | DEPENDE=RAS-01/manter+autorização
- [ ] STATUS=CONDICIONAL | ROTA=RAS-03 | TAREFA=V2-009c | ESCOPO=identidade viagem/paradas | MODELO=SOL_ULTRA | DEPENDE=RAS-02
- [ ] STATUS=CONDICIONAL | ROTA=RAS-04 | TAREFA=V2-034b | FATIA=DECISAO | ESCOPO=atomicidade, tempo, snapshot e cap Raster | MODELO=SOL_ULTRA | DEPENDE=RAS-03+V2-022a
- [ ] STATUS=CONDICIONAL | ROTA=RAS-05 | TAREFA=V2-034b | FATIA=EXECUCAO | ESCOPO=implementar pai/filhos Raster em sombra | MODELO=TERRA_XHIGH | DEPENDE=RAS-04
- [ ] STATUS=CONDICIONAL | ROTA=RAS-06 | TAREFA=V2-012a | ESCOPO=caracterização inicial Raster | MODELO=TERRA_XHIGH | DEPENDE=RAS-05+ORACULO_RASTER_AUTORIZADO
- [ ] STATUS=CONDICIONAL | ROTA=RAS-07 | TAREFA=V2-047 | ESCOPO=bootstrap histórico Raster | MODELO=SOL_ULTRA | DEPENDE=RAS-06
- [ ] STATUS=CONDICIONAL | ROTA=RAS-08 | TAREFA=V2-012b | ESCOPO=paridade core Raster | MODELO=SOL_ULTRA | DEPENDE=RAS-07
- [ ] STATUS=CONDICIONAL | ROTA=RAS-09 | TAREFA=V2-013 | ESCOPO=Sweep and Prune ou aplicabilidade Raster | MODELO=SOL_ULTRA | DEPENDE=RAS-08+prova_snapshot
- [ ] STATUS=CONDICIONAL | ROTA=RAS-10 | TAREFA=V2-050 | ESCOPO=memória limitada e push-down Raster | MODELO=TERRA_XHIGH | DEPENDE=RAS-08
- [ ] STATUS=CONDICIONAL | ROTA=RAS-11 | TAREFA=V2-038 | ESCOPO=E2E, replay, recovery e desempenho Raster | MODELO=SOL_ULTRA | DEPENDE=RAS-10+V2-022b

## Fila completa — cinco fatos/materializações

- [ ] STATUS=CANDIDATO | ROTA=F-MAT01 | TAREFA=V2-036 | ESCOPO=fato_gestao_vista_fretes - MAT-01 - Fretes operacionais | MODELO=SOL_ULTRA | DEPENDE=V2-012b/entradas+referências+relações_aplicáveis
- [ ] STATUS=CANDIDATO | ROTA=F-MAT02 | TAREFA=V2-036 | ESCOPO=fato_gestao_vista_coletores - MAT-02 - Coletores | MODELO=SOL_ULTRA | DEPENDE=V2-012b/entradas+referências+relações_aplicáveis
- [ ] STATUS=CANDIDATO | ROTA=F-MAT03 | TAREFA=V2-036 | ESCOPO=fato_fretes_faturamento - MAT-03 - Faturamento | MODELO=SOL_ULTRA | DEPENDE=V2-012b/entradas+referências+relações_aplicáveis
- [ ] STATUS=CANDIDATO | ROTA=F-MAT04 | TAREFA=V2-036 | ESCOPO=fato_gestao_vista_faturas - MAT-04 - Faturas | MODELO=SOL_ULTRA | DEPENDE=V2-012b/entradas+referências+relações_aplicáveis
- [ ] STATUS=CANDIDATO | ROTA=F-MAT05 | TAREFA=V2-036 | ESCOPO=fato_gestao_vista_manifestos - MAT-05 - Manifestos | MODELO=SOL_ULTRA | DEPENDE=V2-012b/entradas+referências+relações_aplicáveis

## Fila completa — 19 contratos SQL

- [ ] STATUS=CANDIDATO | ROTA=C-VW01 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_faturas_por_cliente_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW02 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_fretes_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW03 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_coletas_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW04 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_coletas_excluidas_origem | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW05 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_cotacoes_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW06 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_contas_a_pagar_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW07 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_localizacao_cargas_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW08 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_manifestos_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW09 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_fato_manifestos_dash | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW10 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_bi_monitoramento | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW11 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_inventario_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW12 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_sinistros_powerbi | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CONDICIONAL | ROTA=C-VW13 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_raster_sm_transit_time | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW14 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_dim_filiais | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW15 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_dim_clientes | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW16 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_dim_veiculos | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW17 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_dim_motoristas | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW18 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_dim_planocontas | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas
- [ ] STATUS=CANDIDATO | ROTA=C-VW19 | TAREFA=V2-037 | ESCOPO=contrato SQL vw_dim_usuarios | MODELO=SOL_ULTRA | DEPENDE=owner+manifesto_consumidor+entradas_qualificadas

## Fila completa — gates por fato e saída SQL

Cada fato ou view percorre explicitamente `V2-012c → V2-050 → V2-038`.

- [ ] STATUS=CANDIDATO | ROTA=O-MAT01-01 | TAREFA=V2-012c | ESCOPO=paridade de saída fato_gestao_vista_fretes | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-MAT01-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída fato_gestao_vista_fretes | MODELO=TERRA_XHIGH | DEPENDE=O-MAT01-01
- [ ] STATUS=CANDIDATO | ROTA=O-MAT01-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída fato_gestao_vista_fretes | MODELO=SOL_ULTRA | DEPENDE=O-MAT01-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-MAT02-01 | TAREFA=V2-012c | ESCOPO=paridade de saída fato_gestao_vista_coletores | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-MAT02-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída fato_gestao_vista_coletores | MODELO=TERRA_XHIGH | DEPENDE=O-MAT02-01
- [ ] STATUS=CANDIDATO | ROTA=O-MAT02-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída fato_gestao_vista_coletores | MODELO=SOL_ULTRA | DEPENDE=O-MAT02-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-MAT03-01 | TAREFA=V2-012c | ESCOPO=paridade de saída fato_fretes_faturamento | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-MAT03-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída fato_fretes_faturamento | MODELO=TERRA_XHIGH | DEPENDE=O-MAT03-01
- [ ] STATUS=CANDIDATO | ROTA=O-MAT03-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída fato_fretes_faturamento | MODELO=SOL_ULTRA | DEPENDE=O-MAT03-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-MAT04-01 | TAREFA=V2-012c | ESCOPO=paridade de saída fato_gestao_vista_faturas | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-MAT04-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída fato_gestao_vista_faturas | MODELO=TERRA_XHIGH | DEPENDE=O-MAT04-01
- [ ] STATUS=CANDIDATO | ROTA=O-MAT04-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída fato_gestao_vista_faturas | MODELO=SOL_ULTRA | DEPENDE=O-MAT04-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-MAT05-01 | TAREFA=V2-012c | ESCOPO=paridade de saída fato_gestao_vista_manifestos | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-MAT05-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída fato_gestao_vista_manifestos | MODELO=TERRA_XHIGH | DEPENDE=O-MAT05-01
- [ ] STATUS=CANDIDATO | ROTA=O-MAT05-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída fato_gestao_vista_manifestos | MODELO=SOL_ULTRA | DEPENDE=O-MAT05-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW01-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_faturas_por_cliente_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW01-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_faturas_por_cliente_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW01-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW01-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_faturas_por_cliente_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW01-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW02-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_fretes_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW02-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_fretes_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW02-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW02-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_fretes_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW02-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW03-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_coletas_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW03-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_coletas_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW03-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW03-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_coletas_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW03-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW04-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_coletas_excluidas_origem | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW04-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_coletas_excluidas_origem | MODELO=TERRA_XHIGH | DEPENDE=O-VW04-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW04-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_coletas_excluidas_origem | MODELO=SOL_ULTRA | DEPENDE=O-VW04-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW05-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_cotacoes_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW05-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_cotacoes_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW05-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW05-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_cotacoes_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW05-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW06-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_contas_a_pagar_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW06-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_contas_a_pagar_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW06-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW06-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_contas_a_pagar_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW06-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW07-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_localizacao_cargas_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW07-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_localizacao_cargas_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW07-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW07-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_localizacao_cargas_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW07-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW08-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_manifestos_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW08-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_manifestos_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW08-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW08-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_manifestos_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW08-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW09-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_fato_manifestos_dash | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW09-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_fato_manifestos_dash | MODELO=TERRA_XHIGH | DEPENDE=O-VW09-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW09-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_fato_manifestos_dash | MODELO=SOL_ULTRA | DEPENDE=O-VW09-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW10-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_bi_monitoramento | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW10-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_bi_monitoramento | MODELO=TERRA_XHIGH | DEPENDE=O-VW10-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW10-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_bi_monitoramento | MODELO=SOL_ULTRA | DEPENDE=O-VW10-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW11-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_inventario_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW11-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_inventario_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW11-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW11-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_inventario_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW11-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW12-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_sinistros_powerbi | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW12-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_sinistros_powerbi | MODELO=TERRA_XHIGH | DEPENDE=O-VW12-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW12-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_sinistros_powerbi | MODELO=SOL_ULTRA | DEPENDE=O-VW12-02+V2-022b
- [ ] STATUS=CONDICIONAL | ROTA=O-VW13-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_raster_sm_transit_time | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CONDICIONAL | ROTA=O-VW13-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_raster_sm_transit_time | MODELO=TERRA_XHIGH | DEPENDE=O-VW13-01
- [ ] STATUS=CONDICIONAL | ROTA=O-VW13-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_raster_sm_transit_time | MODELO=SOL_ULTRA | DEPENDE=O-VW13-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW14-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_dim_filiais | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW14-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_dim_filiais | MODELO=TERRA_XHIGH | DEPENDE=O-VW14-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW14-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_dim_filiais | MODELO=SOL_ULTRA | DEPENDE=O-VW14-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW15-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_dim_clientes | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW15-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_dim_clientes | MODELO=TERRA_XHIGH | DEPENDE=O-VW15-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW15-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_dim_clientes | MODELO=SOL_ULTRA | DEPENDE=O-VW15-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW16-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_dim_veiculos | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW16-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_dim_veiculos | MODELO=TERRA_XHIGH | DEPENDE=O-VW16-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW16-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_dim_veiculos | MODELO=SOL_ULTRA | DEPENDE=O-VW16-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW17-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_dim_motoristas | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW17-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_dim_motoristas | MODELO=TERRA_XHIGH | DEPENDE=O-VW17-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW17-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_dim_motoristas | MODELO=SOL_ULTRA | DEPENDE=O-VW17-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW18-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_dim_planocontas | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW18-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_dim_planocontas | MODELO=TERRA_XHIGH | DEPENDE=O-VW18-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW18-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_dim_planocontas | MODELO=SOL_ULTRA | DEPENDE=O-VW18-02+V2-022b
- [ ] STATUS=CANDIDATO | ROTA=O-VW19-01 | TAREFA=V2-012c | ESCOPO=paridade de saída vw_dim_usuarios | MODELO=SOL_ULTRA | DEPENDE=build+contrato_SQL+V2-012b/entradas
- [ ] STATUS=CANDIDATO | ROTA=O-VW19-02 | TAREFA=V2-050 | ESCOPO=memória e push-down da saída vw_dim_usuarios | MODELO=TERRA_XHIGH | DEPENDE=O-VW19-01
- [ ] STATUS=CANDIDATO | ROTA=O-VW19-03 | TAREFA=V2-038 | ESCOPO=E2E e desempenho da saída vw_dim_usuarios | MODELO=SOL_ULTRA | DEPENDE=O-VW19-02+V2-022b

## Fila completa — release, ensaio, cutover e encerramento

- [ ] STATUS=EXTERNAL_HOLD | ROTA=Z01 | TAREFA=V2-039a | ESCOPO=fundação operacional de release | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=V2-016b+V2-022b+V2-041+V2-042c+V2-045b
- [ ] STATUS=CANDIDATO | ROTA=Z02 | TAREFA=V2-039b | ESCOPO=release candidate da unidade database-wide | MODELO=SOL_ULTRA | DEPENDE=Z01+V2-038/todas_responsabilidades
- [ ] STATUS=CANDIDATO | ROTA=Z03 | TAREFA=V2-015c | ESCOPO=gate final de release | MODELO=SOL_ULTRA | DEPENDE=Z02+V2-015d
- [ ] STATUS=CANDIDATO | ROTA=Z04 | TAREFA=V2-048b | ESCOPO=ensaio database-wide de troca e recuperação | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=V2-037+V2-039+V2-047/todas_responsabilidades
- [ ] STATUS=EXTERNAL_HOLD | ROTA=Z05 | TAREFA=V2-014 | ESCOPO=gate produtivo database-wide e ponto de não retorno | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=DoD+Z03+Z04+autorizações_nominais
- [ ] STATUS=EXTERNAL_HOLD | ROTA=Z06 | TAREFA=V2-040 | ESCOPO=fechar paridade e desativar legado | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=V2-014/todas_responsabilidades

## Promoção automática do próximo chat

- A linha `STATUS=AGORA` coerente com o `STATES.md` é executada diretamente.
- Uma linha `STATUS=CANDIDATO` copiada pelo usuário é pedido de repriorização. O Codex valida dependências antes de promovê-la.
- `EXTERNAL_HOLD` e `CONDICIONAL` nunca viram `AGORA` por conveniência.
- Ao fechar um chat, promover exatamente uma linha elegível. Se nenhuma estiver elegível, manter o estado sem fabricar progresso e registrar o bloqueio concreto.
- Depois do Bloco 30, o próximo número novo disponível é 31.

## Fechamento obrigatório do chat

1. Validar a entrega proporcionalmente ao risco.
2. Atualizar o `STATES.md` primeiro e esta trilha depois.
3. Registrar comandos/resultados reais, arquivos alterados, migrations/objetos afetados e limites da evidência.
4. Não alegar rede, banco, CI, publicação, paridade, deploy ou cutover sem execução comprovada.
5. Preservar `V2-041` e os demais holds até evidência externa nova.
6. Atualizar o painel rápido, a linha concluída e o próximo `STATUS=AGORA`.
7. Executar o validator da trilha e o scanner offline antes do handoff.

## Regra contra divergência

Em qualquer divergência, o `STATES.md` vence. O chat deve corrigir esta trilha antes de executar a linha. Uma linha marcada `[x]` aqui sem checkbox/evidência correspondente no estado é inválida; um escopo aceito no estado e ainda aberto aqui também é drift documental.
