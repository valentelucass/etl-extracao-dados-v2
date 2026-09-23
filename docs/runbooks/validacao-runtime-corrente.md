# Validação corrente do runtime — B61

Entrada offline corrente, a partir da raiz do V2:

```powershell
pwsh -NoProfile -File scripts/validation/Test-RuntimeLocal.ps1
```

O runner executa uma lista fechada de seis verificações, conserva cada log/exit
em diretório novo `target/runtime-local/<uuid>` e só passa se todas passarem.
O teste de controladores usa processos próprios e loopback sintético. Nenhum
item executa SQL, autenticação Windows operacional, JAR operacional ou fornecedor.
Java é validado separadamente pelo `verify` offline em cópia isolada com Java 17.

| Entrada/componente | Papel corrente e limite da evidência |
| --- | --- |
| `Test-RuntimeQualification.ps1` | Valida o AST de SQL059 e um pacote sintético explícito: requests da mesma ocorrência, PIDs distintos, sessões simultâneas na mesma amostra, readbacks equivalentes e recuperação confirmada. Recusa arquivo acima de 1 MiB. |
| `Test-RuntimeQualificationGuards.ps1` | Exercita a entrada anterior sobre mutações reais dos arquivos e o controlador real `Bloco60ConcurrentPair`; inclui saída precoce, teto temporal, NULL, amostras separadas, readback ausente/divergente e falhas na observação/cleanup/barreira/filhos. |
| `Bloco60Assertions.psm1` | Asserções compartilhadas de efeitos, telemetria e preservação. Comparação distingue NULL de string vazia; readback incompleto não equivale a campo presente. |
| `Test-Bloco60Assertions`, `Test-Bloco60ConcurrentPair`, `Test-Bloco60Controllers`, `Test-Bloco60SqlContract -SelfTest` | Regressões existentes mantidas como consumidores do runner; fins distintos permanecem separados. |
| `database/validation/059_observe_bloco60_concurrent_waiters.sql` | Observador SQL adotado: até 28 polls de 250 ms, igualdade de recursos por INTERSECT incluindo NULL. B61 não altera nem executa esse SQL. |
| `database/validation/060_exercise_bloco60_adversarial_rollback.sql` | Exercício adversarial SQL corrente. Execução depende de autorização física futura; não faz parte do runner offline. |
| SQL057/058, `Invoke-Bloco60*`, pacotes, ledgers e receipts B60 | Reprodução histórica. Não são atalhos para nova execução. Orçamento físico B60 encerrado: 204 SQL/83 JVM/75 HTTP. |
| `Test-Bloco61Local -IncludePrivateEvidence`, `Test-Bloco61Preparation`, `Test-Bloco60RenewedClosure` | Integridade da revisão atual e fotografias históricas, via sucessão exata; não repetem campanha física. |

`RuntimeObserverContract.cs` usa ScriptDom já instalado, cujo hash sai no resultado.
Ele avalia os predicados efetivos do AST de SQL059 com variáveis/recursos sintéticos:
condição de continuidade até o limite, parada ao encontrar dois waiters, atraso,
igualdade de tuplas incluindo NULL e divergência em cada coluna do recurso.
A substituição por igualdade escalar SQL é recusada pelo caso NULL/NULL. Isso é
uma avaliação offline de um subconjunto fechado do AST, não execução do motor SQL
nem prova de concorrência física. Sintaxe não suportada falha explicitamente.

A fixture corrente é `src/test/resources/runtime-qualification/concurrent.synthetic.json`;
o par só surge na 28ª amostra, com dois waiters em cadeia. Não contém evidência
do fornecedor. O validador não permite promover seu resultado a prova física.

A revisão histórica está em `target/b60-conclusao-20260910/concurrency-review.json`:
16 amostras simultâneas, exits 0/0 e readbacks iguais sustentaram o aceite físico
independente. O controlador do pacote b776e40f… permanece `NOT_QUALIFIED`.
`observerCodeReexecutedWithTwoJvms=false` continua verdadeiro como limitação.
Não regravar esse controlador nem reemitir o recibo B60 para acomodar código atual.

Na próxima campanha física, se nominalmente autorizada, congelar nova revisão,
requests, bindings e budgets; usar SQL059/060 correntes, reservar antes do efeito,
observar duas JVMs oficiais e registrar recuperação. Esta instrução não concede
autorização nem recupera saldo B60.
