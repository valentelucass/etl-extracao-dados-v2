# 0746 — Manifestos: código testado e comparação offline real concluída

## Resultado e limite do fechamento

O código de Manifestos6399 foi aplicado ao repositório e executado pelo Main.
O build final passou 299 testes em 26 classes, com zero falhas, erros ou skips.
São 79 casos adicionais ao conjunto fechado de Coletas/Fretes, incluindo as
contraprovas para o leitor91, planejamento, adapter, integridade, privacidade
do resultado e regressão das rotas compartilhadas.

O Main comparou integralmente a captura retida de 17/10/2026 com o único Excel
novo da ESL: 315 ocorrências de cada lado, 91 colunas e 17 chaves de agrupamento.
As projeções completas Java/Python coincidem nos dois lados: valores por coluna,
tuplas completas, associações por chave, multiplicidades e os 50 totais. A
referência independente usa inteiros/racionais, sem float ou epsilon.

O resultado de conteúdo continua divergente: 90/91 colunas coincidem e os
50 totais coincidem. Há 2 ocorrências de 1 manifesto com comprovante mobile
“Não” na captura histórica e “Sim” no Excel atual. Os primeiros 90 campos
coincidem conjuntamente com as repetições preservadas. Nenhum identificador ou
valor operacional foi publicado no resumo. Não foi demonstrada a causa da
mudança, a identidade temporal dos snapshots, o filtro de serviço por uma
coluna nativa ou a qualificação das regras candidatas de apresentação.

Este fechamento cobre código e diagnóstico offline. **Não** cobre aceite de
conteúdo/entidade, 3/3 de Manifestos, coleta nova, adoção operacional, produção,
SQL, consumo real do dashboard ou corte. Esses flags continuam falsos.

## Alterações concretas

- `ManifestosIndependentRequest`: perfil `MANIFESTOS_6399_NATIVE91_V1`, contrato
  GET_WITH_QUERY, período/zona e os 18 filtros explícitos; plano pelo Main.
- `BoundedExportWorkbook.readManifestosRows`: 91 coordenadas e presenças
  completas, incluindo colunas64–91, títulos repetidos e posição visual60
  duplicada. A rota de31colunas de Coletas continua separada; não aplica o
  predicado de data da coluna36 de Fretes.
- `ManifestosContentAdapter` e catálogo fixado por hash: Chegada=`closed_at`
  na coluna12; Fechamento=`finished_at` na13. Arrays mantêm ordem e repetições.
  Vazio/NULL/comprovante mobile têm regras visuais restritas aos itens
  declarados; RAW, presença e tipos não são sobrescritos.
- `ManifestosOfflineDiagnostic` e `independent-export diagnose-manifestos`:
  diagnóstico integral em arquivo, stdout apenas com recibo, pins antes/depois,
  destinos novos e recusa sem mensagem de parser ou dados privados. Saída3
  sinaliza divergência; saída0 sob apresentação candidata também não concede
  aceite ou produção.
- `Prepare-ManifestosOfflineUnit.ps1`: escopo concreto com80Java,15controles,
  95inputs e nomes exatos dos299JUnit. Mantém orçamento e fechamento físico.

Documentação de uso e limites:
`docs/validacao/manifestos-native91-offline.md`.

## Falha encontrada no caso real e correção

O candidato que passou298testes recusou o Excel real por
`MANIFESTOS_CANONICAL_WHOLE_SECOND_ENCODING_REQUIRED`. Um probe protegido
revelou somente esse código de recusa. A exigência era mais forte que o eixo
visual declarado no catálogo. A correção preserva os lexemas/estilos originais,
compara a data/hora exibida em segundos por aritmética racional e registra
separadamente as codificações diferentes do segundo inteiro canônico.
O caso real contém978dessas codificações. Não foram apagadas ou declaradas
iguais no eixo original. A contraprova adicional demonstra que alterar o
segundo exibido continua produzindo diferença. O build final de299testes inclui
essa correção.

A primeira reprodução Python posterior divergiu no protocolo de digest:
hexadecimal minúsculo no carrier versus maiúsculo no Java publicado. Somente a
referência foi corrigida; a terceira versão reproduziu integralmente o Java.
As duas execuções anteriores não foram reescritas ou aceitas por esforço.

## Evidências atuais

Executor principal real `01a1b0da-5f23-7512-8ec2-92ab8fdac018`; chat auxiliar
autorizado `01a1b0ed-71e6-7171-8cfc-af67512642d3`, encerrado/idle após entregar
código. Somente o principal executou Original/Java/Maven/Python/grafo.
Proprietário histórico do recurso `01a17ee4` continua distinguido do executor.

Base dos caminhos abaixo:
`C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade`.

- Build final: `work/independent-export-manifestos0745-test02/root-executed-binding.json`,
  SHA256 `D223F3AE2D39C5224525C4BD7B43FEC248F8B09E458B460773820A77EB94BD61`.
  Original31192/0,200690ms,pico1035739136B,limite1073741824B,sem timeout.
- Par final: `work/manifestos0745-realpair03/comparison-summary.json`.
  Main retorna3 com diagnóstico íntegro; referência Python retorna0; fechamento
  Original/estado comprovado e `allCompleteProjectionAxesJavaPythonEqual=true`.
  Os arquivos integrais e totais privados permanecem nessa pasta.
- Catálogo final: SHA256
  `9066588513197A48D0F669AC9E8CB15A549877C0C364097641FF65BD7C0A6810`.
- AST: `work/manifestos0745-graph01/graph-executed.json`, SHA256
  `CF23688C1B0A8DF7B475EC85199C9D9A2D3F72126529D7CE59F3A2B536EF1CBB`.
  Escopo95inputs/93arquivos extraídos,52215nós/125590arestas, evidências alheias
  preservadas; sem extração semântica/LLM. Original23176/0,193202ms,284794880B.
  Endpoints estão declarados; resolução de todas as referências não é afirmada.
- Resumo público: `outputs/manifestos-conferencia-codigo.json`,11487B,SHA256
  `3508FB4C936FBBBAA60965E8837D6B1AC711672F788F2C2CFA45EB3074473DAF`.
- UI: arquivo1769668 gerado06/11/2026às10:04:21-03,315linhas; período17/10.
  ESL continua aberta, sem modal, período preservado. Screenshot sem células de
  negócio em `outputs/esl-manifestos-periodo.jpg`.

Todos os14Originals próprios fecharam com estado restaurado e slot vazio,
incluindo2com falha preservada (recusa inicial e diferencial de representação).
Houve2recusas de preparo de build e1de metadados do grafo antes de Original0;
os sucessores corrigiram os pins/formato sem aumentar limite ou esconder falha.
Os candidatos Pythonv1/v2 e scripts executados continuam versionados em work.

## Continuidade

Coletas/Fretes seguem fechados na camada0744. Não repetir seus131mecanismos ou
seis pares sem causa nova. Seus testes compartilhados participaram do299final.
Manifestos ainda precisa de coletor/integração e par API/UI fresco, com contrato,
autoridade, admissão, orçamento e reservas atuais de Source1 antes de qualquer
despacho. Os harnesses desta unidade só autorizam Source0; não relaxar essa
separação nem atribuir automaticamente a diferença mobile à passagem do tempo.

Canônico781028B/SHA256
`FB923CC52CBB6740386AB1D62D9ADD4663C04675E6FCE645EEB08B36EC6DA7E6` e
AUX28/SHA256 `49A25567E3A2636E4D81E1DFCBC32B2BE5B9D97F34BC803DF0C0B60B003FA25D`
preservados. Sem alteração de ACL, contas, serviços, SQL, DDL ou deploy. V1
permanece pausado e intocado. Data Export para as entidades; GraphQL só Usuários.
Source/HTTP/SQL0 referem-se aos jobs offline do ETL; a aquisição independente
de1exportação UI ocorreu no navegador autenticado e está registrada à parte.
