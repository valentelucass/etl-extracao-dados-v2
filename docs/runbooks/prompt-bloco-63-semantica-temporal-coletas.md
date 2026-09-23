# B63 — semântica temporal de Coletas e regressões de qualificação

**PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO**, preparado em 10/09/2026.
O usuário pediu um próximo bloco delimitado para iniciar em outro chat. Preparar
este documento não executa B63. Sua adoção explícita autoriza concluir as frentes
locais A–D abaixo, sem confirmação repetida para cada edição ou teste coberto.

## Objetivo, seletor e leitura inicial

Trabalhe em `C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2`.
Conclua a manutenção local motivada por COL-TIME-01: rastrear a semântica temporal
de Coletas do legado até o V2, corrigir defeitos demonstrados, exercitar o código
corrente e preparar a prova representativa ainda necessária a Q-COL-01/V2-012a.

O STATES mantém zero rotas AGORA e nenhum próximo bloco oficial elegível para
qualificação externa. Esta proposta é uma manutenção local específica derivada
da evidência nova B62, conforme regras2/3 do seletor: dependência externa bloqueia
somente o subgate que a utiliza. Não reabrir V2-041, escolher outro EXTERNAL_HOLD,
criar checkbox para aumentar progresso ou adotar o programa global de48 itens.
O rótulo B63 identifica esta proposta; não é aceite nem reserva de campanha.

Leia, nesta ordem:

1. `AGENTS.md`, `STATES.md` — topo atual, tarefas pendentes, direções técnicas e
   seletor — e `../CONTEXTO_GLOBAL.md`.
2. `docs/continuidade/RETOMADA.md`, `docs/runbooks/continuidade-agentes.md`,
   `docs/continuidade/checkpoints/0058-preparacao-bloco63-coletas-temporal.md`
   e seu predecessor `0057-bloco62-replay-real-coletas.md`.
3. Este prompt, `docs/catalogos/bloco63-preparacao/README.md` e `manifesto.json`;
   depois `docs/catalogos/bloco62-real-replay/RELATORIO.md`/`source-summary.json`
   e `docs/runbooks/bloco61-matriz-paridade.md`.
4. ADR0023, catálogo Coletas V2-010, ADR0043, contrato6908 corrente, componentes
   e testes citados nas frentes, somente na profundidade necessária.

Antes de editar arquivos ancorados, execute
`scripts/validation/Test-Bloco63Preparation.ps1 -IncludePrivateEvidence -SelfTest`
e confira a cadeia B62/B61. Se a árvore já evoluiu, siga a sucessão efetiva e
preserve as revisões históricas; não force os arquivos a voltar a este snapshot.

## Evidência de partida

B62 passou sete linhas reais/quatro raízes pelo parser/gate/mapper/staging em
memória. Duas páginas GraphQL trouxeram40 nós; as sete linhas encontraram pares,
sem diferenças de ID de origem e campos comuns. O relatório usou o rótulo
"ID canônico" para essa comparação entre canais: isso não significa que tenha
provado o surrogate canônico do registry SQL nem equivalência universal de tags.

status_updated_at está ausente no contrato6908 corrente; a seleção GraphQL possui
statusUpdatedAt e houve sete diferenças. Nenhuma fonte terminou sua travessia.
COL-SHAPE-01 está comprovado apenas nessa amostra; COL-TIME-01 e a qualificação
representativa continuam abertos. O frescor preenchido pelo mapper não prova
equivalência temporal com GraphQL. Corpos reais não foram retidos para novo replay.

Baseline de testes histórica:1448/0/0/4,Java17,Maven3.9.14,heap512MiB; nove checks
finais passaram. Recibo B62 em `target/b62-real-replay-20260910-171610/receipt.json`,
SHA-256 `ac05ccdab6b5e95e28124e1f5604f3fdd5105025785f8023cad01810fb9db74a`.
Não alegar que essa suíte foi executada novamente por preparar/adotar B63.
Roadmap de partida67/115,48 pendentes,191 rotas,zero AGORA; não é meta numérica.

## Escopo local e limites

Autorizados na adoção: leitura dos repositórios V1/V2 e documentação já existente;
edições revisáveis no V2 necessárias a Coletas; fixtures sintéticas, testes Java17/
PowerShell, loopback sintético da suíte, build offline isolado, ADR e continuidade.
SQL legado e migrations atuais podem ser lidos como código para caracterização.
Não abrir o projeto de dashboards. O V1 é somente fonte de leitura neste bloco.

Não executar API, ler .env/credenciais, chamar fornecedor, SQL/JDBC físico,
migration, JAR operacional, UAC, grants, Windows auth operacional, agendamento,
bootstrap, backfill, sweep, publicação, deploy, cutover, commit ou push. Não
alterar a configuração do ambiente nem iniciar nova campanha B60/B62. JVM de
teste offline e loopback sintético não constituem execução operacional.

Todas as rodadas B62 estão encerradas, inclusive a última5/5. Preservar ledgers,
requests, probes executadas, ColetasSourceReplay B62 e seus recibos. Não fabricar
corpos reais a partir de totais sanitizados. A ausência de artefato externo não
impede A/B nem a preparação específica C; ela impede apenas alegar prova externa.
Preservar migrations aplicadas, baselines, contratos/releases e decisões antigos.
Se surgir necessidade de mudança SQL/integração externa, entregar a proposta
revisável com alvo, motivo, dependências e recuperação; não executá-la neste bloco.

## A — rastrear e decidir a semântica temporal local

Inspecione no V2 `ColetaDataExportRecordMapper`, `ColetaFreshnessOrigin`, tipos de
staging, gate/release6908 corrente, gateway JDBC e a expressão de dedupe/promoção
da migration V010, além das regressões existentes. No V1, leia seu AGENTS e
`ColetaMapper`, `ColetaRepository`, DTO/entidade e consumidores temporais no ETL.

Produza um mapa curto por campo: source path/seleção, presença, tipo/parse,
timezone, instante ou data civil, origem do frescor, precedência de dedupe,
comparação na promoção e consumidor. Inclua status_updated_at/statusUpdatedAt,
updated_at, finish_date, service_date e request_date. Distinga timestamp de
evento, última alteração da entidade, data de negócio e tempo técnico de captura.

Investigue explicitamente: V1 usa +00:00 no fallback SQL de datas civis, enquanto
V2 possui zona de fonte no mapper; V2 tenta formatos locais com atZone. Determine
o comportamento em gap/overlap, valor presente inválido, empate e mesma data com
status diferente. São pontos a caracterizar, não ordens para copiar o V1 ou mudar
a regra sem fundamento. Confronte a decisão aprovada antes de editar o código.

Não renomear updated_at/finish_date para status_updated_at, atribuir hora de
extração ao frescor, usar ordem de chegada/hash como desempate ou preencher campo
ausente para obter igualdade. Separe falha de implementação, diferença intencional
do contrato e lacuna de fonte. Cada conclusão deve apontar origem e teste.

Aceite A: mapa temporal e decisão técnica rastreáveis, com alternativas e limites.
Uma equivalência sem prova continua pendente. A necessidade de complemento
GraphQL pode ser especificada com campos, identidade/escopo, proveniência e plano
de remoção; esta frente não aprova nem liga um novo sidecar operacional.

## B — correções e regressões sobre o código corrente

Implemente a menor correção necessária para cada defeito demonstrado em A,
preservando a semântica aprovada. Não criar abstração sem consumidor, nova
fundação de caracterização ou mudança automática do release para aceitar campos
não observados. Mudança semântica sem evidência suficiente permanece proposta.
Se o comportamento atual estiver correto, prove isso com regressões significativas
e registre por que a alteração funcional é desnecessária.

Reutilize os componentes produtivos e testes existentes:
`ColetaDataExportRecordMapperTest`, `DataExportColetasContractTest`, caracterização
Coletas de B58 e perfis Q-FND. O envelope histórico de B58 não deve mascarar o
ROOT_ARRAY corrente: adapte a ligação test-only do novo escopo sem reescrever a
fixture antiga nem o replay executado B62. SQL lido ou JDBC simulado continua
classificado como prova local, nunca como dedupe/transação física comprovada.

Cubra pelo menos os comportamentos aplicáveis:

- ABSENT/NULL/valor vazio/valor inválido preservados e classificados separadamente;
- instante com Z/offset, data civil, virada de dia e zona explícita do contrato;
- gap/overlap histórico em America/Sao_Paulo e nenhum default do host;
- precedência única, atualização posterior/anterior, empate e perda de precisão;
- mesma chave/data com status divergente, preservação de terminalidade e conflito;
- distinção done/coletada e finished/finalizada, cancelamento e status desconhecido;
- páginas expandidas, repetição em páginas, per por IDs e JSON preservado;
- ausência de timestamp de status nunca classificada como paridade temporal;
- captura limitada/sem terminal nunca classificada como snapshot ou sucesso total.

Use casos autorais com expectativas independentes, inclusive contraexemplo que
falhe no comportamento defeituoso. Não derive o esperado da saída do mapper.
Não alterar a policy de empate/reducer para fazer uma fixture passar. Comparação
de massa, relações e dedupe continuam set-based no SQL; qualquer harness em
memória é somente test-only e limitado.

Aceite B: correções justificadas quando necessárias, regressões positivas e
negativas executadas no caminho corrente e camada da prova explicitada. Não
exigir mudança artificial em produção se a lacuna demonstrada for exclusivamente
da fonte; nesse caso, a classificação/teste deve impedir um aceite falso.

## C — pacote específico para a futura prova representativa de Coletas

Atualize a preparação apenas no delta COL-TIME-01/Q-COL-01, reaproveitando a matriz
B61. Entregue casos a observar, campos mínimos por canal, diferenças esperadas e
prova discriminante, critérios de seleção de janela, bindings e limites técnicos
do harness. Preencha o que já é descobrível no repositório; não pedir ao usuário
caminhos/IDs/campos que já possam ser localizados tecnicamente.

Inclua mudanças de status ao longo do tempo, pendente/concluída/cancelada, nulos,
datas nas bordas, empate, ordem inversa e expansão. Especifique como diferenciar
mudança da fonte entre leituras de divergência do mapper. Uma janela pequena ou
paginação terminal não comprovam por si sós representatividade/completude.

Não definir ou consumir novo orçamento de chamadas. Se necessário, preparar
consultas estáticas candidatas como texto, nunca executá-las. A futura rodada
precisa de autorização, janela/teto, oráculo, source_instance/tenant_scope,
responsável por expectativas e Segurança aplicáveis conforme V2-012a/V2-041.
Os limites locais existentes são64KiB por entrada,1.000 linhas,profundidade16,
256 paths e4.096 nós; não aumentar limites para acomodar a prova desejada.

Aceite C: pacote acionável e mínimo, com campos já conhecidos preenchidos e
somente lacunas reais identificadas. Preparação não fecha COL-TIME-01 na fonte,
Q-COL-01 ou V2-012a. Usuários, Fretes, demais identidades, fatos e corte permanecem
fora do escopo funcional deste bloco.

## D — integração, verificação e continuidade

Antes de editar, inventarie o worktree e preserve seus arquivos preexistentes
num diretório privado próprio. Evolua somente os arquivos necessários e mantenha
a sucessão exata de docs/validadores vinculados por hash. Preserve o manifesto
B63-preparação como fotografia desta proposta; não reescrever hashes históricos.

Execute testes dirigidos, depois verify offline completo em Java17/build isolado,
sem clean no target canônico e sem perfis físicos. Use formatação, lint,
arquitetura e cobertura exigidos pelo projeto. Rode validadores atuais/históricos,
continuidade/trilha e scanner pertinentes. Preserve falhas intermediárias e
registre números realmente executados, skips e limitações.

Atualize STATES, trilha, RETOMADA, checkpoint e sucessão. Entregue mapa/decisão A,
código/regressões B, pacote C, diff da rodada, relatório, recibo e até três próximas
ações. A–D são critérios de manutenção, não novos checkboxes do roadmap. Resultado
local completo não equivale a qualificação representativa, SQL físico ou aceite
de negócio. O resultado externo COL-TIME-01 continua pendente onde faltar prova.

Conclua todas as frentes locais adotadas. Não encerre apenas com um plano,
inventário ou explicação de bloqueio externo se houver correção/teste independente
necessário. Pare somente o efeito fora do escopo ou de resultado desconhecido,
reconcilie antes de repetir e registre a proposta concreta para o passo posterior.
