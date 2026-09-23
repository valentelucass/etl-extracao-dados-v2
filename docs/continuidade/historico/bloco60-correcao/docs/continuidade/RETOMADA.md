# Retomada — B60 físico interrompido e compensado

Objetivo: executar o pacote aprovado e consolidar somente os resultados reais.
Checkpoint: [0041-bloco60-campanha-interrompida-compensada.md](checkpoints/0041-bloco60-campanha-interrompida-compensada.md).
SHA-256: 91a389f1452acc924f06a0222162539893dcb9d8fe6b0b8291d04296c8e1a78d

Pacote aprovado: a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e.
Autorização explícita preservada em target/execucao-b60-aprovada-20260909-2334/APPROVAL.txt.
Alvo executado: localhost/ETL_SISTEMA_V2_SHADOW. V024 instalada, preservação
até instalação conferida. USUARIOS_RUN retornou20/UNCONFIGURED; zero HTTP,
decisões/consumos/publicações. Um caso falhou; 73 não executados.
Compensação passou: SERVICE v19/replay=force=0, quatro scopes e duas policies
revogados, dois grants retirados. V024/bindings/históricos preservados.
Ledger: target/bloco60-local/physical/a3d28adeb17775bc/ledger.jsonl.
Um OPEN/CLOSE,19reservas,17sqlcmd,1JVM,zero UNKNOWN/escrow; NOT_QUALIFIED.
Processos próprios terminados; loopback fechado. Não reabrir campanha/saldo.

Catálogo: docs/catalogos/bloco60-fisico/README.md.
Consolidação/diff/recibo: target/execucao-b60-aprovada-20260909-2334/.
O PASS do recibo final confirma a consolidação; campaignPassed permanece false.
67/115=58,26%,48pendentes,191rotas,zero AGORA; nenhum aceite agregado novo.
Pacote/JARs/migrations/manifestos históricos permanecem imutáveis.

Próximas ações:
1. Conferir recibo final e Test-Bloco60PhysicalClosure com evidência privada.
2. Reproduzir/corrigir offline a incompatibilidade da entrada `$Fault.class`
   com o verificador real numa revisão futura; log físico só expõe UNCONFIGURED.
3. Para novos efeitos, preparar pacote contra V024/SERVICE v19 e obter aprovação
   específica; aprovação anterior não renova OPEN, validade nem orçamento.
