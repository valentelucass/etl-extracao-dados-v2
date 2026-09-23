# Estabilização P01 → P02 → recorte P03

Estado: P01 reconciliado; P02 diagnosticado; recorteP03 BLOQUEADO_POR_INPUT
(autorização específica para evolução SQL). A estabilização não foi concluída.
Escopo adotado em19/09/2026: diagnóstico e correção delimitada de A/B e referências,
sem DDL/migrations, P04–P08, produção, fonte real ou alteração do índice.
39/45 e67/115 preservados. Este relatório não sela a campanha A–N.

## Estado reconciliado

Rodada: `target/macrobloco-campanhas-integrais-20260915-01/`.
Inventário inicial desta estabilização e cópias: `stabilization-20260919/`.

- Base0165:3505 hashes de `before/` conferidos; validator de sucessão executado
  nessa cópia histórica passou, inclusive7contraprovas. Manifestos anteriores intactos.
- Árvore no início:3540 arquivos; delta frente à base:25modificados e35novos.
  As oito exclusões Git são anteriores à campanha; nenhuma restauração ou indexação.
- Todas as dez tentativas anteriores têm resultado OBSERVED e nenhum processo
  próprio ativo.04/05:exit1, rollback confirmado, bytes de agregados antes/depois
  idênticos. O texto de0168 sobre04 em andamento é fotografia histórica.
- `inputs.json` identifica a revisão de entrada; `build/` inclui formatter.
  A05 testou seis etapas. As sete atuais e demais mudanças posteriores não
  recebem aprovação pelo resultado anterior.
- Sucessão necessária às provas: base histórica validada, inventário/delta
  explícitos, snapshots e recibos novos. O validator antigo contra o worktree
  continua FAIL por `STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`.
  Fechamento de sucessão/selagem da entrega permanece emP08; nenhum hash antigo
  foi trocado para esconder drift.

## Causa da campanha — SEQ-02/SEQ-05, relações MC

`IntegralArtifactFixtures.Spec.generation` muda a permutação Manifesto→Coleta.
O componente de origem MC identifica a coleta apontada; trocar a relação também
muda esse componente. A revisão superior do suplemento não aposenta o componente
anterior, porque V034 calcula `DENSE_RANK` por relação/origem/componente.

Assim, dois componentes antigos e dois novos continuam como revisões atuais de
grupos distintos. A regra `ONE_TO_ONE` encontra origens concorrentes para cada
destino e recusa os quatro vínculos. `collectionReferences` então encontra zero
vínculos ativos para duas raízes e lança `QUAL_LINEAGE_COLLECTION_BINDING_COUNT`.
`compareRecomposition` é chamado em todas as etapas: seu nome na stack não prova
que a falha ocorreu na etapa final de recomposição.

Prova06 A/B: `completed=4 activeMC=0 latestMC=4 conflictingMC=4 olderComponents=2`.
Ambos param no backfill tardio (quinta etapa). As quatro anteriores passaram pelo
comparador; a mudança de componente é o discriminante. A falha original continua
sendo erro da IT positiva, com diagnóstico agregado anexado como suppressed.

Classificação: falta de sucessão do conjunto relacional no produto SQL, exposta
pela fixture que muda relações. O guard de cardinalidade e o oráculo não são a
correção. A view de componentes também conserva observações históricas; uma
correção precisa definir conjunto efetivo e conservar histórico, sem inferir
exclusão por janela incremental nem remover vínculos arbitrariamente em Java.

## Causa da referência — SEQ-02/SEQ-05, Cotações

A fixture congelada05 já declara bootstrap→reference→recompose. O executor
interrompe a cadeia quando a comparação falha; por isso pode retornar dois
resultados. A asserção de quantidade anterior escondia essa divergência.

O teste agora verifica os comparadores antes de exigir3resultados, mantendo a
exigência original. Acrescenta33previews por etapa e a ordem exata da cadeia.
Dois testes unitários A/B preservam páginas/revisão de fonte e a separação das
revisões de suplemento. Não houve redução de expectativa ou troca de oráculo.

V011 e V085 só atualizam tarifa/snapshot com frescor de fonte maior;
V086 exige a release do snapshot igual à seleção vigente. Páginas idênticas
mantêm frescor; a captura com nova referência conserva o snapshot antigo
e retira Cotações de SQL05. Prova06 A/B: `SQL-05 expectedRows=2 observedRows=0`,
uma diferença de cardinalidade. A cadeia interrompe corretamente antes da
recomposição. A referência antiga não pode ser aceita como resultado novo.

Classificação: capacidade de revisão de referência desacoplada do frescor da
fonte ausente no caminho SQL. A fixture e seu oráculo mantêm os valores novos
independentes, e os testes confirmam páginas de fonte preservadas. O recibo05
estava correto sobre a quantidade retornada; faltava expor o gate reprovado.
O defeito do teste era a ordem do diagnóstico, corrigida e exercitada em06.

## Alterações e provas selecionadas

- `IntegralCampaignIT`: nome de sete etapas e diagnóstico agregado na falha,
  preservando todas as expectativas de aprovação.
- `SequenceBindingDiagnostics`: consulta set-based limitada ao run próprio,
  timeout10s, somente contagens; não consulta nem registra payload/identidade.
- `SequenceReferenceIT`: ordem das asserções e validação adicional de etapas/previews.
- `LocalArtifactSequenceTest`: dois casos A/B de preservação das fontes/revisões.
- Nenhuma alteração funcional Java/SQL aplicada para contornar os defeitos.
  Formatter do controlador é contabilizado separadamente no diff da rodada.

Tentativa06:27unit passaram, zero falhas/erros/skips. IntegralCampaignIT:2erros;
SequenceReferenceIT:2falhas; SequenceRecompositionIT:2PASS, zero skips.
Recibo OBSERVED/exit1/rollbackConfirmed=true, sem timeout, excesso de log ou
falhaUTF-8. Compilação, Enforcer, Spotless e Checkstyle passaram. Não houve
correção funcional aprovada nem teste convertido de falha em sucesso.
SequenceRecompositionIT foi selecionada porque a
fixture compartilhada atual é posterior à prova03; SequenceFailureIT03 foi
preservada, pois não houve mudança no executor/dependência Coletas→Fretes.
Os dois casos de recomposição mantêm33recibos de captura e comparam os cinco
fatos/19saídas; funcionam como controle independente sem troca de relações ou
referência. Campanhas07etapas e referência03etapas continuam não aprovadas.
Sem regressão integral, escalas, supervisor ou pacote: pertencem aP04–P08.

## Limites e condição de desbloqueio

Controlador existente: reserva própria por tentativa,3600s, sequência1800s,
etapa240s, heap512MiB/query60s; duas travas Maven, Windows, alvo local exato,
commit bloqueado e rollback. Nenhuma reserva antiga renovada.

A correção relacional e a revisão de tarifa requerem evolução do SQL e suas provas. O pedido atual
proíbe DDL/migrations; não usar escrita direta ou redefinição transitória de
procedure para contornar a restrição. Preparar correção aditiva, baseline e
contraprovas somente sob escopo que inclua explicitamente essa evolução.
Não fecharP03, paisV2, paridade real ou prontidão produtiva por este diagnóstico.

Próximo macrobloco proposto: concluir este recorteP03 com sucessão relacional e
revisão tarifária, migrations aditivas/baseline e contraprovas, somente após
autorização explícita para prepará-las, qualificá-las e aplicá-las ao alvo local.
Sugestão: GPT-6 Astra / High, pela decisão de conjunto efetivo versus histórico e
pela separação de referência/frescor sem quebrar replay. P04 continua dependente
das provas positivas faltantes; não é liberado por este relatório.

As contraprovas devem conservar IDs/cardinalidade, histórico, fonte/referência/
suplemento separados e ordem Coletas→Fretes: conflito contemporâneo deve continuar
recusado, distinguindo-o de supersessão legítima; referência nova deve alterar a
tarifa com fonte idêntica, enquanto replay da mesma referência mantém no-op.
Não basta substituir `>` por `>=` no frescor, nem escolher MAX de identidade ou
reduzir expectativas. Reexecutar A/B sete etapas e referência três etapas após
qualificar o delta, dentro de novas admissões permitidas, sem renovar reservas.
