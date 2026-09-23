# Correção dos gates globais após B60

Manutenção autorizada após a auditoria dos 67 itens concluídos. Não cria bloco
funcional nem checkbox. Os critérios e os aceites históricos permanecem no STATES.

| Achado | Correção | Prova de regressão |
|---|---|---|
| A01 / V2-015b e V2-015e | Lista explícita do gate passa a incluir V024, já existente no baseline. | `repositoryMigrationAllowlistMatchesEveryVersionedFileAndBaseline` compara lista, arquivos e baseline. |
| A02 / fundação V2-047 | Rebind de SCHEMA-FOUNDATION e do catálogo de cutover revisado; demais 14 fontes intactas. | `bootstrapBindsEveryCanonicalSourceToItsCurrentBytes` verifica os 16 bindings. |
| A03 / V2-048a | Catálogo regenerado; validator compara quantidade com o schema previamente validado, em vez de repetir o número antigo. | `cutoverTracksTheCurrentSchemaVersionHashAndPermissionCount` compara versão, SHA e triplets. |

Os três testes falharam antes da correção: nove testes focados, três falhas, zero
erros/skips em `target/correcao-gates-pos-b60/red-02`. A tentativa red-01 parou no
formatter e foi preservada como falha de preparação, não como RED funcional.

A fundação já era versão 5 com 41 triplets. Não houve alteração de schema,
migration, grant, role, política SQL, regra de domínio ou runtime. O gerador
conservou responsabilidades, DAG e fences; somente manifesto/checksum mudaram.
O bootstrap conserva 17 linhas, seis entidades elegíveis somente ao planejamento,
34 casos sintéticos e todos os bloqueios de execução/oráculos externos.

`Test-GlobalGateMaintenance.ps1` confere exatamente 14 deltas, os snapshots,
os arquivos preservados e as adições declaradas no manifesto desta manutenção.
Seu modo privado verifica o recibo B60 original e os 2.094 artefatos pela revisão
correta. Não existe exclusão genérica por pasta, extensão ou data. Os snapshots
em `docs/continuidade/historico/gates-pos-b60/` mantêm os bytes anteriores;
os manifests e receipts históricos B55–B60 não são regravados.

O validator B60 usa essa sucessão para distinguir evidência histórica de arquivo
atual. No pacote físico preparado, muda exclusivamente o hash desse validator.
Todos os outros campos, arquivos, limites, validade, requests e ausência de
aprovação permanecem iguais. O pacote anterior está preservado por snapshot;
eventual aprovação futura deve identificar o novo hash. Esta manutenção não
executa nem autoriza SQL físico, fonte, credencial, instalação ou renovação de budget.

Evidência nova em `target/correcao-gates-pos-b60/`: build isolado, logs de gates,
guards, inventário, diff da rodada e recibo. O encerramento depende de
`final/receipt.json` com resultado aprovado e hashes íntegros; este catálogo
não substitui evidência executada nem conclui o aceite físico pendente do B60.
