# Checkpoint0496 — paridade V1: retomada durável selecionada

## Identificação e objetivo

- UTC: 2026-10-12T22:30:22.796230+00:00; mesmo HERMES_ce7fca106978, orientação adicional de Lucas, EM_EXECUCAO.
- Anterior: 0495-auditoria-existente-investigada-quota-sem-acesso-operacional.md; SHA256 7797a9a08ec9fe04b5d2aff754636545e45db9b5b71b835d8a72df4e8c505beb.
- Autoridade: arquivo etl-diretriz-paridade-v1.txt, cache/scratch Hermes; checksum em paridade01/root/intent01.json. Construção não fica inteira parada por quota: comparar V1 real e executar UM bloco interno de maior impacto, com mudanças e testes reais. Não reiniciar busca de quota nem declarar mocks como entrega integrada.
- Recibo anterior ce7f foi efetivamente gravado/retornado; cópia exata preservada em root/receipt-before-parity01.json. A instrução adicional autoriza incorporar os novos resultados ao MESMO recibo ao concluir. Não apagar o fechamento histórico0495, nem fingir que o resultado anterior incluía esta fase.

## Autorização e limites

- Quota6908 continua STOP429/dependência externa documentada; somente a operação dependente permanece bloqueada. Não trocar credencial/escopo, sondar fonte, enfraquecer TLS, DDL/grants/deploy/produção ou alterar V1.
- JAMAIS alterar versão/instalação SQL Server16.0.1000.6, contas, identidades, ACL, permissões, serviço/listeners/trust. C78/final02/E3/BF/defaults preservados. Nenhuma nova autoridade SQL/TDS/HTTP/provider/PM2 por esta unidade.
- V1 real: script-etl-automacao, leitura de código/documentação, sem executar suas operações. V2: checkout atual,1448arquivos src/pom/checkstyle congelados em root/code-initial02.json, documentos *.before e git-initial01.bin.
- Root único editor docs/CI/grafo/integrador. Banco owner somente DevLocalLedger.java e novo DevLocalLedgerStartRecoveryTest.java/helper de processo no package devlocal. Runtime qualifica processo/componente depois dos bytes finais; Regras aceita critérios; Fontes conclui comparação, sem segunda implementação. Despachos efetivos em root/dispatch01.json e dispatch04.json; código do emissor04 teve SyntaxError antes de qualquer efeito, preservado; sucessor05 parseado e despachado, sem replay externo.

## Seleção e evidência atual

| Achado | Prova atual | Decisão |
| --- | --- | --- |
| Interrupção do start local | Banco handoff01: V1 RecoveryUseCase/SqlServerRecoveryReplayGate comparados ao V2 DevLocalLedger201–232/426–469. Código V2 cria runs/execution e filhos antes do journal START; load só reconstrói journal; directory semSTART recusa ORPHAN_RUN_CONFLICT e recover/readback RUN_NOT_FOUND | Prioridade por integridade e retomada. Primeiro reproduzir causalmente, depois intenção durável própria/reconciliação, preservando recusas de objetos estrangeiros |
| C78 composição pendente | Connector atual qualificado não é sessão operacional/capture; E3 runner permanece explícito, não trocar pin ou herdar confiança | Registrar lacuna independente; nenhum auth/catálogo/SQL realizado nesta fase |
| Exportação temporal | Root observou operacionalRequests sempre array, inclusive uma janela, enquanto run request exige objeto. APIs/tests atuais tratam array como saída de planejamento | Lacuna de ligação/ergonomia menor a revisar, não segundo bloco. Não alterar comportamento por hipótese sem preservar contrato atual |
| Agendamento | ADR0007 one-shot externo é decisão explícita; V1 daemon não deve ser copiado automaticamente | Paridade funcional de janelas/retomada/controle, não mudança de arquitetura por aparência |

- Implementação selecionada: BANK_PARITY_DURABLE_START_ORPHAN. Intenção de START ligada a workspace/execution/input sob lock existente; reconciliar somente material próprio validado. Não simplesmente inverter diretório/journal criando outra janela; não adotar orphan estrangeiro, apagar histórico, marcar sucesso ou criar retry de negócio.
- Aceite esperado: falha original demonstrada; JVM própria interrompida na fronteira de start, processo encerrado/lock liberado, nova JVM/mesmo ledger real reabre e produz estado honesto recuperável. Segundo recovery idempotente, sem START duplo; mismatch/tamper/foreign/link/case/concurrent writer recusados; compatibilidade do journal/limites finitos. Sem alegar power-loss ou SQL durability sem prova.
- Ambiente dev já disponível: filesystem/OS locks/JDK17 e componente DevLocalLedger reais. DevLocalEngine é fluxo sintético; seus resultados não comprovam origem, SQL, ETL integrado ou paridade completa. Prova real aqui é do componente de controle/persistência local; SQL/fonte continuam com gates próprios.
- Fonte/SQL/TLS/captura/provider/PM2/DDL/versão/permissões/V1/produção nesta unidade:0. Nenhum teste ou aceite de implementação fechado por análise. Comparações dos outros builders e contraprova/implementação Banco ainda pendentes de consumo.

## Retomada imediata

1. Consumir matrizes V1→V2 reais dos quatro builders e integrar até três lacunas por prioridade; não reabrir busca de quota. Checkpoint é índice, não qualificação.
2. Banco reproduz falha, implementa e testa somente paths atribuídos. Root revê diff e invariantes; Runtime valida bytes finais/processos/componente semmocks, Regras confronta causalmente o resultado. Não aguardar/pollar em terminal depois de handoff.
3. Atualizar regra/ADR/estado, grafo após mudança de código, verificações adequadas e recibo ce7f consolidado. Concluir este único bloco e aguardar decisão; nenhuma sequência indefinida/callback Hermes.

- Bloqueio externo mantido: acesso operacional de auditoria/quote6908 e seis vínculos, somente operações dependentes. Fonte real será necessária para extração, paridade semântica/completude e validação ponta a ponta. Este reparo não libera esses gates.
