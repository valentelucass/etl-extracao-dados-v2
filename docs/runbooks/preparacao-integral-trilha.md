# Preparação integral da trilha — P01–P33

Estado: PREPARACAO_OFFLINE_NAO_AUTORIZA_EXECUCAO. Pedido de 19/09/2026:
preparar o terreno de toda a trilha, antes de continuar a construção.
Não é adoção de campanha física, rotação, fonte, publicação, DDL, COMMIT ou corte.

O [mapa verificável](../catalogos/preparacao-trilha/plano.json) cobre os 33 P e os
48 IDs atualmente abertos no STATES: escopo, dependências obrigatórias e
condicionais, inputs, artefatos reutilizáveis, preparação e critério de saída.
Não cria um segundo backlog nem altera os critérios canônicos. STATES continua
autoritativo; a tabela da [trilha](../../TRILHA_CONCLUSAO_POR_MODELO.md) conserva
prioridades e recomendações de modelo. Campo `prepare` é instrução, não prova
de que a etapa foi implementada. Campo `exit` exige evidência futura.

## 1. O que travou os últimos blocos

Foram confrontados o prompt original adotado da campanha, `authorization.json`,
o prompt P04 fornecido pelo usuário, o LEDGER e checkpoint0184. As fontes privadas
permanecem nos respectivos diretórios; esta preparação não as altera.

| Fonte | O que efetivamente estabelece | O que não estabelece |
| --- | --- | --- |
| `target/preparacao-macrobloco-campanhas-integrais-20260915-01/PROMPT-MACROBLOCO-CAMPANHAS-INTEGRAIS.md`, §4 | Sequência1800s, etapa240s, campanha3600s, heap512MiB, query60s, watchdog verify5400s; registrar teto/saldo antes do efeito | Data final explícita ou quantidade global numérica de campanhas |
| `target/macrobloco-campanhas-integrais-20260915-01/authorization.json` | Escopo local A–N adotado e os tetos acima | Validade temporal explícita e total cumulativo de tentativas |
| Prompt P04 fornecido pelo usuário, seções4 e Limites | Conferir saldo/vigência, nova reserva, não renovar por inferência; parar em P04, sem P05–P08 | Um valor novo de saldo, prazo ou autorização para P05 |
| `p04-supervisor-preview-20260919-01/LEDGER.md` e sql-01..06 | Tentativas históricas e limites por tentativa; resultados OBSERVED, exit1, rollback agregado confirmado no checkpoint0184 | Uma reserva futura ou prova de execução dos workers recusados |

Conclusão: **não há evidência de expiração ou de esgotamento de um total global
numérico definido**. Há falta de explicitação/reconciliação do que o prompt P04
mandou conferir. Ausência de campo não equivale a saldo zero, expiração nem saldo
infinito. Somar seis tetos de3600s não mede o tempo gasto nem define um orçamento
cumulativo autorizado. Não atribuir a um gate técnico o que foi uma decisão
administrativa anterior ao controlador.

O bloqueio físico histórico permanece registrado, sem reexecutar nesta rodada.
Na próxima execução, conferir a instrução efetivamente adotada: aplicar os limites
que ela define; se uma condição indispensável continuar ambígua, resolver uma vez
com o pacote delimitado abaixo. Não exigir uma renovação se a autorização vigente
já cobrir exatamente a ação. Não usar esta retificação para criar permissão.

Criar documentação, inventário ou executar verificações offline desta preparação
não depende de saldo SQL. A proibição histórica genérica de criar diretório ou
processo não se aplica a essas ações solicitadas agora.

## 2. Próximo conjunto local já delimitado

O maior conjunto coeso imediato é **fechar as lacunas de admissão de P04 →
qualificar I/J → medir P05**. P06 é revisão técnica posterior; P07→P08 compartilham
regressão final/pacote, após os achados. Não misturar aprovação operacional ou
fonte real nesse conjunto. Preparar as filas externas pode ocorrer antes.

| Parcela | Reusar / verificar primeiro | Prova ainda exigida e parada |
| --- | --- | --- |
| Admissão P03 | Recibos P03 e snapshots de `inputs.json` das tentativas, catálogo B–H e implementação atual | Comparar os consumidores e dependências afetados, não apenas11 hashes de testes. Divergência requer análise de impacto, não repetição automática de tudo |
| Temporal P04 | `QualificationPackageFixture`, `QualificationPlanner`, `QualificationContractTest`; 7 testes offline no0184 | Revalidar só após delta; revisar vínculo com a fixture efetiva e fronteira exata do deadline. Prazo lógico259200 não aumenta watchdog físico |
| Supervisor I | `QualificationSequenceSupervisorIT` (5 casos), `QualificationResumeAdmissionIT`, `QualificationJournalTest`, `QualificationCancellationIT`, `QualificationConcurrencyIT` | Selecionar casos pelo critério; STARTED/worker/recibo interno, owner/inputs adulterados, cancelamento, evidência parcial, retry reconciliado e isolamento. Cinco testes por nome não fecham automaticamente todo I |
| Preview J | Asserções de33 previews/etapa em `QualificationSequenceSupervisorIT`, testes sweep e contrato da sequência | Completude por raiz, savepoint/rollback, replay/observação posterior e recusa de apply integral; SQL04 vazia só no contrato admitido |
| Quatro escalas K | `SequenceScaleIT`: raízes2/4/8/16, sete etapas, filhos/suplementos e relatórios `target/sequence-scale/roots-*` da tentativa | Medições/planos efetivos das quatro massas dentro dos tetos; falha em uma escala não é quatro escalas aceitas. Sem SLO/platô produtivo |
| Revisão/gate L | Matriz A–K, diff próprio, POM e identidades dos testes anteriores | Revisão de suficiência e regressão apropriada da revisão final; não alegar revisão humana |
| Pacote M/N | Scripts existentes QualificationPackage/Succession/Laboratory e scanner offline | Executar bytes do JAR extraído A/B, comandos pertinentes, overlay em cópia, scanner integral e selo/readback; não reescrever hashes históricos |

### Preparação econômica da execução

Usar o controlador da campanha `target/macrobloco-campanhas-integrais-20260915-01/Invoke-Build.ps1`,
não o controlador histórico de outra rodada com nome semelhante.
Ele foi lido, não executado nesta preparação.

No controlador atual, `PackageDirected` admite seleção de unitários;
`Physical` admite `Tests` e `UnitTests`; **`PackagePhysical` executa toda a suíte
unitária mesmo se `UnitTests` for informado**. Não repetir esse custo a cada
correção dirigida. Primeiro diagnosticar/validar offline, depois selecionar a
prova física mínima coberta e, no fechamento, executar o gate completo obrigatório.
Reuso de artefato requer os mesmos bytes/dependências; não prometer reuso só pelo nome.

Preflight físico posterior: conferir alvo/schema104 sem DDL/reaplicar V103/V104,
autoridade/reserva, ausência de resultado desconhecido e identidade dos processos
próprios; JDK17 apenas no processo, heap512MiB e duas travas Maven. Agregados iguais
não provam, sozinhos, rollback interno ou execução de filho que nem iniciou.

### Decisão única proposta se ainda faltar autoridade aplicável

**PROPOSTA_NAO_ADOTADA.** Não é uma reserva nem um ledger de execução. Este pacote
elimina campos em branco para uma eventual aprovação explícita do próximo conjunto:

| Campo | Proposta concreta |
| --- | --- |
| Escopo | Admitir e concluir P04/I–J; somente após aceite, P05/K. Preparação/correção local dirigida incluída; P06–P08 não incluídos nos efeitos físicos desta proposta |
| Alvo | Exclusivamente `localhost/ETL_SISTEMA_V2_SHADOW`, schema104 já existente, autenticação Windows |
| Efeito | Dados sintéticos, duas travas Maven, transação rollback-only/commit bloqueado; nenhum DDL, fonte, credencial, job, deploy, produção, commit/push ou índice |
| Quantidade | Até2 tentativas físicas P04 e1 campanha P05 contendo as quatro escalas; máximo3 no conjunto. Segunda P04 só após falha conhecida, causa delimitada e correção validada |
| Tetos | Cada tentativa/campanha até3600s; sequência1800s; etapa240s; SQL60s; heap512MiB. Total reservado máximo10800s para as3 tentativas, sem aumento dos tetos de massa/página/arquivo |
| Vigência proposta | 48 horas a partir do registro UTC da adoção explícita; registrar início/fim absoluto antes de qualquer efeito. Não depende da duração do chat e não se renova na retomada |
| Contabilidade | Ledger novo vinculado à adoção e aos seis resultados históricos. Reservar3600s antes de cada tentativa; reserva consumida não volta ao saldo por falha/término antecipado. Nunca inventar gasto histórico |
| Recuperação/parada | Serial; resultado desconhecido bloqueia repetição até reconciliação. Não matar processo sem owner comprovado. Parar por teto, fim da vigência, dependência ou risco; não criar quarta tentativa |
| Evidência | Comando sanitizado, revisão/input hashes, PID/start, resultado, camada, recibos internos e rollback. P05 não executa se I/J não aceitos ou se as quatro escalas não couberem no teto |

Esses números são **uma proposta de limite conservador**, não estimativa de duração
nem garantia de conclusão. Não autorizar toda a trilha de uma vez: release, fonte,
COMMIT/restore, corte e retirada são efeitos distintos com alvo ainda nominalmente
pendente. Se a autoridade já vigente for suficiente, citar sua origem e não exigir
esta proposta como condição adicional artificial.

## 3. Uma coleta de entradas para toda a trilha

Solicitar somente evidência sanitizada. Nunca pedir token, senha, payload real,
arquivo `.env`, cursor ou dados de negócio no chat/Git. Modelos sintéticos existentes
servem como formato, nunca como atestado real. Pessoas/times não conhecidos ficam
como papéis. Não enviar pedidos a terceiros sem ordem do usuário.

| Input | Campos e evidência que precisam chegar juntos | Libera / recebimento |
| --- | --- | --- |
| G01 | Classes/consumidores abrangidos, owner/data, invalidação anterior, saúde do writer legado, referência privada do secret store e autorização da fonte | P09, depois uso autenticado correspondente. Reusar catálogo `evidencia-rotacao-v2-041` e seu validator; não rotacionar nesta preparação |
| G02 | Provider, remote/branch, conjunto aprovado para publicar, owners/aprovadores, checks e proteções, autoridade de commit/push/CI | P10, independente de G01. SHA remoto e execução real só são preenchidos depois do efeito aprovado |
| G03 por entidade | Canal/template, release/contrato, chave/grão/tenant, cardinalidade raiz/filho, filtros, fuso/janela, paginação/terminal/completude, oráculo independente, teto/validade e autorização | P13–P20 da entidade. CAP8636, FAT4924, INV10633 e SIN6392 exigem provas próprias; USER snapshot e Raster separado, sem9901 por inferência |
| G04 por referência/saída | Owner, release/baseline/vigência/proveniência, calendário/filial/frota, grão/precedência fiscal e fonte da série NFS-e; manifesto nominal18 saídas externas + SQL10 interno; política por33 responsabilidades de ausência | P14/P15/P21–P26. Só entra como dependência onde consumido; não consultar dashboards nem inventar cadastros |
| G05 por efeito | Host/banco isolado, TLS/principals existentes ou criação especificamente aprovada, quotas/storage, retenção, backup/restore, RTO/RPO, janela, orçamento e recuperação | P12 e provas materiais posteriores. COMMIT/crash/restore exigem escopo próprio; local rollback-only não os autoriza |
| FEED / V2-015d | Feed/NVD permitido, modo de acesso fora do Git, janela/tetos, responsável por baseline/achados/exceções e retenção de relatórios | P11 e RC posterior; não bloquear sonda ESL independente por falta desse feed |
| G06 | RC/inputs qualificados da unidade DATABASE_WIDE, alvo isolado, rota/Tcut, executores, janela/tetos, fences e duas recuperações, aceite do ensaio | P31 somente após P30. Não é cutover nem autorização produtiva |
| G07 | Ensaio aceito, DoD completo, aprovação nominal/datada, backup/rota/abort, responsáveis, sequência writer antigo→novo e recuperação V2 pós-PNR | P32; aprovação individualizada próxima do corte, não antecipada com lacunas |
| G08 | Cortes/observação/retenção comprovados, inventário de destinos/owners e consumidores, itens exatos de retirada, retenção/recuperação e autorização | P33; não retirar adaptador/job/credencial apenas porque existe substituição sintética |

As oito famílias G e FEED são **pacotes de entrada**, não nove aprovações globais.
Receber a parcela de Cotações pode liberar sua vertical sem esperar Inventário;
fatos usam o DAG exato da trilha. Ausência/sweep e saídas analíticas são ramos
separados. Relação MC não bloqueia a ingestão base de Fretes, mas bloqueia a
paridade relacional que a utiliza. Não converter isso em uma cadeia linear de33 Ps.

## 4. Regra prática para próximos macroblocos

1. Ler o topo vigente do STATES/RETOMADA e os critérios da fatia, não tomar um
   prefácio histórico como próxima ação. Rodar a verificação offline abaixo.
2. Confrontar inputs e autoridade **antes de entregar um prompt de execução**.
   Gate conhecido sem mudança não gera outro bloco que só repete preflight.
   Usar pacote de entrada já preparado; trabalhar nas parcelas independentes.
3. Agrupar por entrega: P04→P05 local; depois revisãoP06; P07→P08; uma vertical
   P16→P18; relações/coreP19→P20; uma saídaP24→P27. Os intervalos são candidatos,
   não permissão para saltar dependências ou mudar o modelo compulsoriamente.
4. Reaproveitar prova só com revisão, consumidor e camada compatíveis. Executar
   gates finais requeridos; não repetir suíte/scanner sem delta só para preencher
   um chat. Correção nova exige revalidar a prova afetada.
5. Guardar tentativas falhas e resultados desconhecidos; sincronizar STATES →
   trilha/mapa → verificações → checkpoint lido → RETOMADA. Sem novos checkboxes
   de implementação para esta preparação.

```powershell
pwsh -NoProfile -File scripts/validation/Test-TrilhaPreparation.ps1
pwsh -NoProfile -File scripts/validation/Test-TrilhaPreparation.ps1 -SelfTest
```

O verificador lê somente os arquivos locais declarados, valida33 etapas,
dependências/condições, referências e cobertura exata dos IDs abertos. Não abre
rede/SQL/JDBC, não chama Maven, não lê credenciais, não reserva campanha e não
seleciona automaticamente uma ação física. PASS significa **mapa coerente**,
não runtime aceito, autoridade vigente, CI, scanner integral ou P08 concluído.
Não substitui `Test-Gpt56ChatTrail.ps1` (trilha histórica diferente), nem seus
gates de sucessão. As falhas históricas permanecem pendentes de P08.

## 5. Progresso sem inflação

Fotografia preservada: construção39/45 =86,7%; aceites67/115 =58,3%.
Esta preparação acrescenta **0 unidade de construção e0 aceite**. P04/P05 podem
fechar capacidade/prova local sem mudar esses numeradores. Não prometer percentual
para um macrobloco sem mapear o critério canônico que ele realmente concluirá.

A matriz45 mantém seis linhas `countedAfter=false`: V2-041,016,017,048,014,040.
Isso não significa seis implementações inteiras ausentes: V2-017 já tem checkbox
canônico aceito no STATES, enquanto a métrica de construção herdada não o conta.
São métricas distintas; reclassificar exigiria reconciliação explícita da base,
não mais código nem checkbox duplicado. Cada unidade dessa base vale2,22 pontos
percentuais e cada checkbox vale0,87 ponto, mas pais/subfatias não devem ser somados
como entregas independentes. Preparação33/33 é cobertura do mapa, não100% do projeto.
