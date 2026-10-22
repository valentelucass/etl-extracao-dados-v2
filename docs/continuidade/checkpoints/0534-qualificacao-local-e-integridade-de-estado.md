# Checkpoint 0534 — Qualificação local e integridade de estado

Data UTC: 2026-10-17T00:22:12.8680404Z. Pedido USER_20261016_CONTINUAR_V2; instrução atual: “conclua entao, um a um até encerrar”. Escopo: encerrar sequencialmente as frentes locais elegíveis enquanto a ESL aguarda o gestor. Anterior: docs/continuidade/checkpoints/0533-preparo-atual-medicao-e-fronteiras-locais.md, SHA256 4e64093ab1adfcdb06f8e2aa10d80332d32676fe6927f44546fa9728529e748e.

Zero autoridade física nova. ESL em standby: nenhuma INFO/probe/captura/reserva/quota renovada pelo tempo. C78 operacional rollback-only; E3 não é herdado. MainHTTP anterior UNKNOWN, MISSING_NOT_RECONSTRUCTED, V1 parado pelo usuário, SQL Server 16.0.1000.6 e proibições permanentes preservados. Root único docs/CI/autoridade; Banco único SQL/TDS. Jobs pesados somente no executor vigente, serial, 2 GiB por processo/árvore, piso4096MiB/comprometimento85%, prazos finitos. Documentação, desenho, modelos e testes locais não aprovam efeitos SQL/Source, retenção real, paridade integral ou cutover.
## Resultado da primeira frente

Encerrado o recorte local elegível de robustez/qualificação; **o gate global de cobertura não está fechado**. Foram acrescentadas duas classes com20 casos distintos:12 de integridade da entrada temporal e8 de serialização/recuperação de estado do Inventário. Quatro testes existentes passaram a limitar somente suas JVMs filhas a `-Xms16m -Xmx128m`. Não houve delta em produção:749 fontes main,673 test,1422 Java no total. POM,53 exclusões/limiares e43 arquivos RAW imutáveis permanecem iguais. Nenhum pin histórico foi reescrito para acomodar resultados novos.

Os casos temporais verificam arquivo/política/binding, limite exato/excedido, JSON duplicado/trailing, blackout e vínculos de source/tenant/fuso antes da preparação operacional. Os de Inventário usam o mapper real e verificam precisão de1ns, arrays ordenados com valores duplicados, replay, stale/update e recusa de estado anterior inconsistente, preservando os inputs. São contratos de comportamento local, sem banco, ledger operacional, worker ou Source.

## Execuções e composição, sem soma de sobreposição

- Focal14:36/36PASS, contendo os12 temporais novos; drift0. As falhas anteriores11/13 eram fixtures de fuso inválidas e continuam preservadas/rejeitadas.
- Rodada12: OOM no Checkstyle com Maven256, antes de compilar/testar. Rodada15:3620 casos,3587PASS,7falhas de inicialização de filhos,5erros por JAR ausente e21skipped; Failsafe não executado. Seu pico observado de2304753664B excedeu o teto configurado, portanto não há alegação de cumprimento do teto nessa ocorrência.
- Rodada17: package sem testes primeiro, Maven512; test em novo Maven256, Surefire512. Compilação estrita dos749main+673test, Spotless1422 e Checkstyle passaram.3628 casos:3605PASS,2FAIL,0ERROR,21SKIP; os12 negativos da15 foram resolvidos. Os dois FAIL eram do novo State8: `JsonNode.equals` distingue tipos numéricos internos antes/depois da leitura, embora os bytes JSON persistidos sejam iguais. A ocorrência17 continua FAIL, e seu exec não foi aceito.
- Correção delimitada18: somente duas comparações de árvores viraram igualdade dos bytes serializados das rows. Todas as demais asserções e os8 casos foram preservados. SHA da fonte atual `c79507fa3540b38d672b359da0c277da6528a77409bfe06211089d380bab8705`. Compilação estrita e focal18:8/8PASS,0falha/skip/abort. Os7 testes `QualificationPackageIntegrityIT` sobre o JAR17 passaram. Estilo da única fonte alterada depois da17 passou novamente.
- Aceite **composto**, não uma rodada limpa nem verify irrestrito: excluir TODOS os8 State17 e substituir pelos8 State18. Resultado unitário distinto:3628 casos,3607 aprovados e21skipped; integração18:7/7. Não adicionar focal14, os6 State17 que passaram nem as invocações Own-JAR05 a esses totais. Os21 skips e os cinco perfis físicos desativados não são aceites substituídos por esse número.

As qualificações isoladas Own-JAR05 permanecem separadas:3 métodos/4 invocações, sem agente e sem Source/provider/SQL. A prova nova comparou todos1364 arquivos de classes/recursos main05 e17:1319classes+45recursos, mesmos conjuntos e bytes. A produção atual749 permanece idêntica àquela base; isso não transforma preparo/deployment05 em autorização de execução.

## Cobertura e recursos

Somente exec14 qualificado (união03+05+09+10+14) mais os casos bem-sucedidos18 e IT18 compõem o relatório. Os exec04/11/13/15/17 falhos não foram importados;12 não executou testes. A identidade byte a byte das classes05/17 sustenta os IDs de classe usados na união. Provas Own-JAR sem agente não recebem crédito JaCoCo.

| Pacote | Linhas | Branches | Estado |
|---|---:|---:|---|
| bootstrap |8023/10071 =79,66438288%|3768/5517 =68,29798804%|Linhas abaixo de80%; faltam34 linhas para8057|
| persistência/controle |2776/3390 =81,88790560%|1444/2087 =69,19022520%|Critérios locais satisfeitos|
| qualificação |1778/2095 =84,86873508%|962/1499 =64,17611741%|Critérios locais satisfeitos|

Check18 exit1 exclusivamente pela linha de bootstrap; focal, IT e drift passaram. Não baixar limiar, ampliar exclusões, fabricar admissão, remover guardas, usar reflexão para atingir privados ou abrir positivo Source/provider durante o standby. Os caminhos Own-JAR recusam overlays/agentes antes da abertura; provisioning/dispatch mantêm seus requisitos reais. A revisão do remanescente está arquivada em `phase1-close-boundaries17.md`; não se promete que todos os34 pontos dependam da ESL nem que o gate esteja impossível de fechar.

Executor mantido serial,2GiB por processo/árvore:17 pico1261215744B,18 pico1450647552B, ambos sem timeout e drift0;18 durou304207ms. A15 excedente é preservada, sem reclassificação retroativa. Não houve alteração de pagefile/limite global, serviço ou processo alheio.

## Evidências e continuidade

Coorte final, prova de invariantes, cobertura, reports/resultados17/18, receipts, estilo e atualização AST integral estão arquivados com tamanho/SHA no índice desta etapa. Atualização AST usa o caminho nativo de código completo, sem cluster/LLM; HTML/relatório e documentação semântica históricos permanecem identificados como históricos. A conclusão AST factual é a do `completion.json` anexado, não uma contagem copiada do passado.

Permanecem abertos o gate de linhas bootstrap, auditoria Dependency-Check/G05, oráculos/paridade de fonte e os aceites físicos. A próxima frente elegível é a auditoria de dependências. A etapa local não altera V2-012a ORACLE_REQUIRED, MainHTTP histórico UNKNOWN, C54 consumido/fechado, C78 rollback-only, proposta0533 não adotada operacionalmente ou a espera pela ESL.

Conclusão AST0534:44113 nós/115616 arestas, coorte Java1422, seis arquivos revistos com nós/ast_hash verificados; três outputs adotados sob lock/backup. O incremento documental deste checkpoint é posterior ao corte AST; a documentação semântica não foi reextraída.

