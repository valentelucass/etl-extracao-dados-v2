# Checkpoint 0541 — piso ESL implementado; qualificação pendente de memória

23/10/2026. O usuário autorizou prosseguir e confirmou que pausou a V1. Continuação da pendência de configuração identificada em [0540](0540-verificacao-regras-esl-intervalo-e-franquia.md). Esta unidade altera código local e prepara sua qualificação; não é uma extração executada.

## Alteração local

- `RuntimeConfigurationFactory` passa a recusar intervalos efetivos menores que 2000 ms nas configurações habilitadas de Data Export e GraphQL. O valor é verificado depois da precedência das variáveis de ambiente. Valores válidos são preservados, sem ajuste silencioso; o teto existente de 60000 ms permanece.
- A mensagem de recusa não inclui valores sensíveis nem a causa de parsing. A política genérica `EslResiliencePolicy` e seus cenários sintéticos com intervalo zero não foram alterados.
- Foram acrescentadas sete invocações JUnit: três valores inválidos (0, 1 e 1999), três válidos (2000, 2001 e 60000) e a precedência de ambiente válido sobre arquivo inválido. Cada caso cobre Data Export e GraphQL; as duas primeiras famílias cobrem arquivo e ambiente. São testes escritos, ainda não executados.
- Duas fixtures antes consideradas válidas foram atualizadas para 2000 ms: a configuração comum de `RuntimeConfigurationFactoryTest` e o bundle sintético de `MainDevWorkspaceProcessTest`. Nenhum gate foi removido.

## V1 e recursos

O usuário confirmou a pausa em três mensagens. O PID conhecido 74124 e seus filhos não foram encontrados; os comandos dos processos Java observados não correspondiam aos caminhos V1/V2. O arquivo de estado da V1 conserva a marca antiga `RUNNING`, sem comprovar processo vivo. Nenhum processo V1 foi iniciado ou alterado por esta unidade.

Às 16:11:34 BRT o executor vigente recusou a qualificação **antes de iniciar o programa**:

```text
BATCH_MEMORY_ADMISSION: RAM livre 3348 MiB; comprometimento 85%. A tarefa nao foi iniciada.
```

Origem da regra: `C:/Users/suporte/Documents/projetos/.runtime/memory-control/README.md` e `Executar-Batch.ps1`: piso de 4096 MiB disponíveis, comprometimento de até 85%, limite de 2048 MiB por árvore e um batch protegido por vez. Não houve exceção, mudança do limite nem encerramento de processos de outros projetos. Foi solicitado ao usuário liberar memória; nenhuma resposta havia chegado ao registrar este checkpoint.

## Evidências e validação

Diretório próprio, sem sobrescrever qualificações anteriores:

`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/qualificacao-intervalo-esl-20261023-01`

- `admission-refusal.json`: recusa real do executor, horário e `childStarted=false`.
- `static-review.json`: hashes e verificações locais, sem tratá-los como execução Java.
- `validate.py` e `run-protected.ps1`: qualificação preparada para snapshot isolado, Maven offline, regressão causal com a implementação anterior, testes selecionados, estilo e atualização AST. **Esse roteiro não foi executado**, pois a admissão foi recusada. Não há `result.json`, classes qualificadas ou JAR novo desta unidade.

`git diff --check` passou. Inspeção confirmou os dois pontos de aplicação do piso, as duas fixtures e a preservação dos hashes de policy/governor/executor HTTP e do POM. Formatter, Checkstyle, compilação, JUnit e `graphify update .` permanecem pendentes; indexação também deve passar pelo executor protegido. Não incorporar estes testes às contagens aprovadas dos checkpoints anteriores.

## Próximo passo e limites

Com recursos admitidos, executar a qualificação preparada e registrar o resultado real, inclusive falhas; só publicar eventual formatação após verificar que os arquivos canônicos continuam iguais aos inputs copiados. Não repetir uma recusa idêntica sem mudança dos recursos. Depois, avaliar a próxima unidade operacional com bytes e evidências atuais.

HTTP ESL, SQL e leitura/materialização de credenciais: zero nesta unidade. A pausa da V1 reduz a concorrência local observada, mas não comprova ausência de outros consumidores nem saldo da franquia. Limite contratado, período, consumo/saldo e escopo ainda precisam ser esclarecidos; os budgets locais não representam essa franquia. O piso na configuração não cria coordenação entre processos nem altera sozinho a política genérica de retry de 429. Fretes6389/422 continua sem causa confirmada. STOP429, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, C78 rollback-only e as restrições SQL16.0.1000.6 permanecem. Extração integral, persistência/retenção, paridade, cobertura e G05 não foram concluídos.
