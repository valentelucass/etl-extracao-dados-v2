# B61 — consolidação local e preparação da paridade

10/09/2026. **LOCAL_CONSOLIDATION_COMPLETE_PARITY_INPUTS_PREPARED**.
Pedido adotado: [prompt B61](../../runbooks/prompt-bloco-61-consolidacao-local-e-paridade.md).
O escopo local A–D foi tratado; não houve paridade de fonte real, campanha física,
SQL operacional, UAC, fornecedor, produção, bootstrap, deploy ou cutover.

| Frente | Entrega e evidência |
| --- | --- |
| A | Mapper entrega texto de status já interpretado; domínio não usa Jackson. Limite de 100 observações físicas, recusa antes do 101º next. Sem ligação do redutor ao runtime. [ADR0042](../../adr/0042-manifestos-fronteira-json-e-coorte-limitada.md), testes de limite/preguiça/nulo/conflito/ordem/replay e regra geral de arquitetura com contraexemplos reais. |
| B | Entrada [Test-RuntimeLocal](../../../scripts/validation/Test-RuntimeLocal.ps1), seis verificações e 35 contraprovas na revisão final. Comparadores compartilhados distinguem NULL/vazio/ausente; cleanup falho conserva registro da barreira e de ambos os filhos. SQL059 tem predicados avaliados via AST offline; SQL060 permanece adversarial corrente, 057/058 históricos. [Índice operacional](../../runbooks/validacao-runtime-corrente.md). |
| C | [Matriz acionável](../../runbooks/bloco61-matriz-paridade.md) com seis entidades, inputs, limites a ratificar, oráculos e critérios V2-012a/b/c/V2-047. Reusa Q-FND-01/02 e B58. Q-USR-01 preferencial pelas dependências locais atendidas; Q-COL-01 independente; nenhuma AGORA, Q-MAN-01 em hold. |
| D | Build isolado, logs falhos/corrigidos preservados, diff contra inventário, relatório, checkpoints e sucessão exata de 12 arquivos anteriores. Resultados detalhados dos gates ficam no diretório privado abaixo. |

Diretório da rodada: `target/b61-local-20260910-144800/`. `inventory.json` registra
HEAD, estado inicial e hashes dos 2.285 arquivos públicos; `before/` conserva seus
bytes. `build-path.log` comprova `build/target` sob esse diretório privado. O target
canônico nunca foi limpo. O recibo B60 e 4.286 artefatos foram verificados antes
das edições; a sucessão atual preserva os bytes anteriores e os verifica novamente.

Validação Java 17.0.20.1 / Maven 3.9.14:

| Execução | Resultado e limite |
| --- | --- |
| RED A, testes novos contra fontes anteriores | 26 testes, três falhas esperadas: JSON no domínio, 101 entradas aceitas, leitura além do limite preguiçoso. `a-red.log` e `a-red-reports/`. |
| GREEN dirigido A | 43 testes, zero falhas/erros/skips; `a-green.log` e `a-green-reports/`. |
| Primeiro verify | 1403 testes, zero falhas, cinco erros porque faltava teto de heap exigido pelos testes de medição; cinco skips. `verify-01.log` e `verify-01-reports/` preservados. |
| Verify corrigido completo | **1403 testes, zero falhas, zero erros, quatro skips**, BUILD SUCCESS. Enforcer, Spotless, Checkstyle, arquitetura e todos os limites JaCoCo passaram. `verify-02.log`, `java-verification.json` e `build/target/surefire-reports/`. |
| Cobertura do código atual exercitado nesta rodada | **91,53% linhas (13959/15250), 76,41% branches (5455/7139)**. Relatório JaCoCo no build isolado; não é medição operacional por entidade. |

Comando final, dentro da cópia isolada, com `JAVA_HOME` do JDK17 e
`_JAVA_OPTIONS=-Xmx512m` definidos somente no processo:

```text
mvn.cmd --offline --batch-mode --no-transfer-progress verify -Dv2.measurement.receipt=true
```

Os quatro skips são três testes de symlink indisponível no Windows sem elevação
e `CotacoesSourceCommandTest` sem a propriedade opt-in `bloco57.cotacoes.command`.
Os warnings de Surefire são esses skips; a mensagem de `_JAVA_OPTIONS` é mantida.
O opt-in de receipt V2-050 gera somente medições sintéticas. Nenhum perfil físico
foi habilitado. O primeiro Spotless recusou Java25 herdado de JAVA_HOME; os logs
`red-format.log`/`red-format-java17.log` registram falha e correção da seleção.

Validação PowerShell e documental:

- `b-runtime-local-02.log` aponta para `target/runtime-local/5452efaa59134d63bd24edc93d32459a/`:
  seis checks passaram; a suíte final de guards tem 35 recusas deliberadas.
- O teste do controlador anterior falhou por `B61_RECOVERY_LOST_READBACK_STOP`;
  `b-controller-red.log` preserva a prova. As tentativas iniciais dos guards e
  seus arquivos mutantes também foram mantidas; corrigidos path absoluto e coleção
  vazia no próprio validador antes do GREEN.
- Dez validadores de contratos/identidade/decisões/verticais/Q-FND-01/02/bootstrap
  passaram; `contract-checks.json` registra todos os exits.
- O primeiro gate global de integridade recusou B60R_FILE_HASH: o validador do
  pacote corretivo comparava o helper atual com o hash histórico. Seu adaptador
  agora consulta a sucessão exata e valida o snapshot contra o mesmo hash antigo;
  nenhum manifest/receipt B60 mudou. `final-checks-01.json` e `final-global-gates.log`
  preservam o RED; a rodada corrigida consta de `final-checks.json`/logs `final2-*`.
- Gates finais de sucessão/continuidade/trilha, scanner, UTF-8 e diff são registrados
  em `final-checks.json`, `delivery-checks.json` e respectivos logs. O scanner inicial
  passou em 2.308 candidatos sem finding; o scanner final inclui a documentação de
  fechamento. Não é auditoria de vulnerabilidades nem aceite de segurança externo.

Preservação: [manifesto atual](manifesto.json) referencia o manifesto B61-preparação
imutável, snapshots exatos dos 12 deltas e hashes dos 2.273 arquivos preservados.
`Test-Bloco61Preparation` recebeu somente adaptação de sucessão: continua validando
a fotografia proposta, e delega a revisão corrente ao novo validador exato.
Test-Bloco60CorrectivePackage também lê esses snapshots para os dois pacotes
históricos; suas verificações e hashes antigos continuam exigidos. Não
há exceção genérica para docs/Java, regravação de receipt ou alteração de V001–V024.
Os arquivos novos de snapshots têm seu conteúdo integral verificável; nenhum
trabalho preexistente foi descartado. Não houve commit ou alteração do index.

Diff: `review.patch` contém os deltas ativos e arquivos novos funcionais/documentais;
`diff-completo.patch` acrescenta snapshots e manifesto de integridade.
`changes.json` enumera cada caminho, hash antes/depois e classificação. O patch
é contra o inventário inicial desta sessão, não contra HEAD do worktree já sujo.

Limites e recuperação: B60 permanece aceito apenas fisicamente no laboratório
local. O controlador b776e40f… continua NOT_QUALIFIED; a revisão independente de
16 amostras, exits 0/0 e readbacks sustenta seu aceite anterior. O observador059
inteiro não foi repetido com duas JVMs. AST/fixtures não provam semântica completa
do motor SQL, identidade externa, paridade, completude ou produção. O orçamento
B60 está encerrado em 204 SQL/83 JVM físicas/75 HTTP; B61 não criou reservas.

Rollback revisável: restaurar em conjunto somente os deltas B61 usando `before/`
ou os snapshots públicos, se solicitado; preservar logs e a cadeia documental.
Nenhum rollback de banco é necessário, pois nenhum banco foi acessado. Roadmap
inalterado: **67/115, 48 pendentes, 191 rotas abertas, zero AGORA**. Próxima decisão
externa requer os inputs nominais da matriz, sem fechar V2-012/V2-038/V2-050 ou pais.
