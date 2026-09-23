# Checkpoint 0048 — B60 renovado, recuperado e correção preparada

Data UTC: 2026-09-10T16:01:19.8447597+00:00. Anterior: checkpoint 0047,
SHA-256 086a7ebea77d50d068ee3c2454890615454533071fce0cb7140f1e2b8d79f2cb.
Objetivo: concluir B60 e as frentes A–F de target/bloco60-local/PROMPT-ADOTADO.txt.
Estado: TESTADO_NA_CAMADA; etapa física restante BLOQUEADO_POR_INPUT.

Instrução efetiva: “pode renovar automaticamente para concluir isso logo”.
Renovação de validade aplicada; não renovar saldo de tentativas. Aprovação
para três JVMs além do teto 80 e reenvio do UAC cancelado foi perguntada após
preparar e congelar o pacote concreto abaixo. Nenhum novo pedido de validade.
Somente localhost/ETL_SISTEMA_V2_SHADOW, identidades existentes e fonte sintética.

Inventário inicial: target/b60-conclusao-20260910/initial-inventory.json, 2264 arquivos,
SHA-256 16910c09c5e571616241acdb433a3aa0400627971f4a56a1da9204458a1fbb75. Cópias integrais em before/.
Antes de agir, 3538 artefatos do recibo anterior ad82571a… foram conferidos;
initial-verification.json contém o resultado. Preservar todos os recibos,
ledgers, pacotes, testes falhos e checkpoints anteriores.

## Execução observada

| Passo | Camada | Resultado | Prova privada |
| --- | --- | --- | --- |
| Pacote 26ba79e… | Windows elevado + SQL/JAR físico | 16 SQL, três JVMs, dois HTTP | target/b60-provas-finais-20260910/physical/26ba79e319a5c652/ |
| Preparo selado | SQL + probe real | Exit86; PROMOTED, selo1, DQ PASSED, publicação0 | CONCURRENT_SEALED.readback.json e results.json |
| Par oficial | Duas JVMs Windows | Exits -1; observador falhou; cada consumo1, publicação0 | CONCURRENT_A/B.java.log e readbacks |
| Recuperação | SQL físico + auditoria offline | SERVICE33/scopes16, DQv8 revogada, grants retirados, V024/históricos preservados | PROFILE_RESTORED e RECOVERED_HISTORICAL_ROWS |
| SQL complementar | UAC | Cancelado antes de iniciar; zero SQL/JVM | target/b60-conclusao-20260910/sql-uac-cancelled.json |
| Revisão independente | Arquivos + processos próprios | 12 cadeias íntegras, 176 SQL/80 JVM/73 HTTP; zero desconhecidos/processos/listener | target/b60-conclusao-20260910/physical-verification.json |

Janela 15:36:58.1500693–16:36:58.1500693 UTC, campanha fechada 15:37:36.2181931.
Orçamento 240 SQL/80 JVM/400 HTTP, dois de oito escrows; sem restituição.
Os readbacks do novo par não provam concorrência nem publicação. A publicação
do par mais antigo continua preservada e tampouco recupera seus exits perdidos.
Continuam 72/74 casos originais e o cancelamento extra, sem aceite agregado.

## Correção revisável

target/b60-conclusao-20260910/package/package.json, SHA-256 b776e40f6f1bd206cf215050901497cbac66d87fc4b06594b576684f8b83b61b, 89 arquivos congelados.
O observador antigo recusava após 12 intervalos de 250 ms. O novo usa 28,
dentro dos oito segundos da barreira, e compara todas as colunas do recurso
de lock, em vez de exigir bloqueio direto pela sessão da barreira. Salva
cada sondagem e exige dois PIDs próprios simultâneos na mesma amostra.
Sem amostras antigas, atraso de inicialização e bloqueio indireto permanecem
hipóteses; o encerramento antecipado de ambas as JVMs é fato observado.
Mantidos timeouts contratuais, JAR oficial, critério de pelo menos um exit0,
uma publicação e um selo nos dois readbacks, com compensação após falha.

Proposta: três JVMs adicionais, teto cumulativo83, sem reset do80 consumido;
20 SQL ordinários + seis de recuperação, dois HTTP; tetos240/400 mantidos.
Preflight33/16/V024; ativação34/17/DQv9; compensação35/18. Policies antigas,
partições e dados preservados. Nova partição contida e IDs próprios congelados.
17 checks de orçamento/pacote, quatro cenários de coordenação, C# e sintaxe
3 PowerShell/18 SQL passaram. Não são prova física do observador novo.

STATES, trilha e RETOMADA sucedem exatamente quatro arquivos via snapshots;
nenhuma exceção genérica de hash. Java/migrations inalterados. Sem checkbox,
fonte real, snapshot completo, release ou cutover. 67/115, 191 rotas, zero AGORA.
Diff, matriz A–F, gates e recibo independentes em target/b60-conclusao-20260910/final/.

## Retomada — até três ações

1. Conferir resposta pendente sobre três JVMs adicionais e reenvio do UAC.
   Origem: limite80 esgotado, seção4 do prompt e cancelamento do Windows.
2. Se autorizado, conferir hashes/estado e executar somente o pacote congelado
   via UAC normal, registrando cada nova reserva; compensar e fazer readbacks.
3. Conferir concorrência, adversarial, agregado e recuperação; emitir sucessão
   e recibo novos, marcando somente critérios realmente comprovados.

Concluir apenas com todos os critérios A–F comprovados no escopo local; nunca
contabilizar preparação ou renovação como teste aprovado. Não reenviar UAC
cancelado por outro caminho, não criar conta ou executar além do saldo aprovado.