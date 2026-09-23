# Retomada — B60: provas rechecadas, janela final pendente

Objetivo: concluir B60; 72/74 casos originais e um cancelamento extra comprovados.
Busca independente dos 3.388 artefatos e 73 conjuntos de assertivas concluída.
Faltam CONCURRENT_A/B com observação física e saídas individuais, validação
adversarial e agregado SQL. Recuperação anterior SERVICE31/scopes14 comprovada.
Nenhum efeito físico novo nesta revisão. Janela anterior encerrada.

[Checkpoint 0047](checkpoints/0047-bloco60-busca-de-provas-e-pacote-final.md),
SHA-256 086a7ebea77d50d068ee3c2454890615454533071fce0cb7140f1e2b8d79f2cb.

Pacote concreto: target/b60-provas-finais-20260910/package/package.json,
SHA-256 26ba79e319a5c652b968ed617fd890b760c3649f42cfcdf268edfef4bb0cba57. Requer aprovação de uma única janela de 60 minutos;
não interpretar pedido de busca como renovação automática. Até três JVMs,
20 SQL ordinários e seis de recuperação restantes, dois HTTP. Teto cumulativo
original mantido; já gastos 160 SQL, 77 JVMs, 71 HTTP e dois de oito escrows.
README e controlador no diretório do pacote; UAC normal, localhost/SHADOW.
Preflight SERVICE31/scopes14/V024; recuperar SERVICE33/scopes16 se ativado.

Testes desta revisão: 17 checks, quatro cenários de coordenação, C# e sintaxe
de três PowerShell/18 SQL. Maven 1397/0/0/4 é histórico, Java sem alteração.
Auditoria: target/b60-provas-finais-20260910/evidence-audit.json. Diff e recibo: target/b60-provas-finais-20260910/final/.
STATES é a autoridade. 67/115, 48 pendentes, 191 rotas e zero AGORA.
Users transitório SHADOW_UPSERT_ONLY; sem fonte real, snapshot ou cutover.