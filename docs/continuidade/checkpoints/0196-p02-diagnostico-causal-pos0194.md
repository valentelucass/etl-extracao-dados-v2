# Checkpoint 0196 — P02, diagnóstico causal offline pós-0194

## Identificação e objetivo

- Data:2026-09-20T20:19:23Z. Macrobloco P02, diagnóstico/documentação concluídos.
- Anterior: `0195-p04-i-j-fechamento-nao-qualificado.md`, SHA-256
  `40718373aa68f3db9b956a3d1475a5958ce55fcfc4d8560b0ba31edd16657f19`.
- Pedido efetivo: diagnosticar P04/I–J somente offline/documental, reconciliar
  P04-P05-POS0194-01, separar asserção/controlador/limite e parar sem Physical.
- Estado: **IMPLEMENTADO_NAO_QUALIFICADO** para I/J; P02 não concede aceite.

## Autorização e limites

Somente leitura de artefatos existentes, contraprovas offline documentais,
compilação/formatter estáticos e sincronização documental. Guard e fontes Java
não foram alterados. Fora: P03–P08/P05, SQL/JDBC, DDL, migration/Flyway, fonte,
rede, credenciais, V1, produção, deploy, commit/push e qualquer reserva Physical.
A única reserva histórica permanece consumida. Não há orçamento novo, campanha
ativa ou aprovação solicitada. Autoridade física futura precisa ser nova e finita.

## Alterações e decisões

Inventário anterior: `target/p02-diagnostico-pos0194/baseline.json`,23614 arquivos;
status Git inicial no mesmo diretório. Mudanças preexistentes conservadas.
Ordem: STATES, TRILHA_CONCLUSAO_POR_MODELO, matriz-a-n, relatório sanitizado,
este checkpoint e, após leitura/hash, RETOMADA. Arquivos novos de evidência
ficam exclusivamente no diretório P02; não reescrevem a campanha histórica.

1. Retificação temporal demonstrada:2405.848s é classe com cinco testes,
   soma2405.837s, máximo758.817s. Três cenários possuem @Timeout1800 individual;
   dois de admissão não têm anotação. Contrato SEQ-01 continua1800/240s;
   recibos sucesso/falha/cancelamento328.0730455/254.5823196/317.3075448s,
   maior etapa43.750s. A inferência histórica de violação é retificada, não
   apagada. Não foi demonstrado defeito de enforcement por esse total.
2. Asserção histórica estava errada: recibo tardio33/33/33/33/0, estados
   PASS_LOCAL×4/BLOCKED_DEPENDENCY,19 saídas por etapa, falha direta preservada.
   Correção test-only já existente está coerente com SEQ-06 e o runtime.
3. Guard busca sourceRoot, mas Maven usa build. Bloco de leitura reproduz
   cinco ausências na origem; build dá zero ausências/uma suíte falha/exit127.
   Menor correção futura é apenas a raiz da busca. Não aplicada em P02.

Abordagens rejeitadas: tratar a classe como uma sequência; ampliar o limite;
converter XML histórico falho em PASS; executar IT como suposta contraprova
offline; procurar XML fora da tentativa; regenerar manifest para esconder drift.

## Execução e evidência

| Critério | Camada/comando | Observado | Evidência em target/p02-diagnostico-pos0194 |
| --- | --- | --- | --- |
| XML/recibos/limites/guard | reconcile.ps1, somente arquivos | PASS;20 ITs históricos/1 falha; três recibos vinculados | evidence.json, reconcile.log |
| Preview | Predicado sobre JSON e mutações em memória | Antigo recusado; corrigido aceito;4 adulterações recusadas | evidence.json |
| Correção Java | javac17 --release17 -proc:none; dois arquivos test-only | exit0; nenhum método executado | javac.log, javac-result.json |
| Formatter | Maven offline/JDK17 spotless:check, teto240s | exit0;1162 arquivos limpos;20.205s | spotless.log, spotless-result.json |
| Preparação | Test-TrilhaPreparation -SelfTest | PASS;1 positivo/24 negativos | preparation.log |
| Scanner | Invoke-OfflineSecretScan | FAIL conhecido: oito MISSING_CANDIDATE | scanner.log |
| Sucessão | Get-StatesExecutionSuccession, somente arquivos | FAIL histórico de RETOMADA preservado | succession.log |

Verificações finais/diff/preservação ficam em preservation.json e closure.json;
consultar os resultados finais, não inferir PASS da intenção de executar.
Não foi executado helper Java de asserção, suíte Java integral, Checkstyle,
build verify ou integração. Compilar dois arquivos e validar formato não
substitui essas provas. A incompatibilidade anterior JVM25 foi contornada com
JDK17 no processo filho, sem mudar dependências, PATH global ou ambiente persistente.

Readbacks existentes iguais e recibos rollback=true; logs UTF-8 abaixo16MiB.
Journals terminam em TERMINAL. Zero processo próprio ao fechamento é evidência
histórica do ledger/worklog, não nova inspeção de processo/SQL; P02 não lançou
processo físico. Sem efeitos desconhecidos ou nova reserva. Nenhum aceite
fechado:39/45,67/115, oito ausências e falha de sucessão mantidos.

## Retomada — até três ações

1. Em escopo posterior de correção local, alterar somente a raiz do guard e
   provar aceitação/recusas com XMLs isolados; nunca alterar o XML histórico.
2. Provar o helper Java isoladamente sob JDK17 sobre JSON/recibo sanitizado,
   sem setup/execute/resume/JDBC; depois preflight da revisão exata.
3. Somente com autoridade física nova e finita, reservar outra campanha P04
   com limites originais e qualificar integralmente I/J. P05 segue bloqueado
   até esse aceite e autorização aplicável; nenhum saldo histórico é transferível.

Bloqueio externo para prova física: autorização quantitativa futura do usuário,
com alvo, impacto, limites e recuperação; não necessária para concluir P02.
Condição de parada atingida: diagnóstico causal e plano mínimo verificáveis,
sem modificar guard, conceder I/J, reservar Physical ou executar P05.
Relatório: `docs/catalogos/campanhas-integrais/P02-DIAGNOSTICO-POS0194.md`.
