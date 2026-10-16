# Checkpoint0498 — Recuperação local testada; CLI e opt-in em qualificação

Mesmo HERMES_ce7fca106978/paridade01, UTC2026-10-12T23:04:46.867463+00:00. EM_EXECUCAO; nenhum aceite agregado antecipado. Anterior0497 SHA256 8c75d94389f2fbec7fdcf87034a940e053db3c936b16b1c3ee356845869b1188 preservado.

Banco recebeu contraprova real baseline RED e qualificou15 casos dirigidos de ledger em quality01..03. Qualificação final03 READ_ONLY_PUBLIC_API_AND_IDEMPOTENT_TERMINAL_RECONCILIATION_PASS; fonte ledger a448bd728dcdb2f2c137c6c9adfb17bd52ec0946d44921104051439c328892b9. Resultados anteriores e falhas de harness Windows preservados; não são sinal verde SQL/fonte. Handoff/coorte FINAL ainda pendentes. Root pediu regressões pertinentes de start/journal antigo por comportamento alterado, sem repetir suites fora do escopo.

Runtime implementa reconcile-start explícito e status de pendência sem mutação no mesmo bloco. A primeira rodada parser/processos exit1/28906ms fica preservada; sucessor em correção/qualificação, sem PASS declarado. Root reviu default status para não esconder pendingStartHold por um registro COMPLETED anterior e leu código/testes reais. Nenhum DevLocalEngine é usado como prova de fonte integrada.

Defeito de entrega encontrado: testes novos referenciavam bank.start.crash.agent/asm/legacy.classes externos sem gate e quebrariam mvn test comum com -javaagent:null. Root transmitiu causa via test-repro-review14; Banco reconheceu e implementa opt-in explícito apenas nos casos dependentes, verificando fixture completa e mantendo casos independentes executáveis por padrão. Runtime recebeu a mesma regra. Não tornar skip em PASS de fronteira real, nem aceitar fixture incompleta. Contrato final/contagens ainda pendentes; não autorizado alterar POM/produção por suposição.

ADR0063 e runbook dev-local-start-recovery criados como decisões em qualificação. Regra DEV-FS-002: intenção própria imutável antes de diretórios, reconciliação explícita do próximo journal slot sob OSlock, sem adoção de material estrangeiro, purge ou retry de negócio. Open/status não alteram evidência. Modelo de morte de processo em gravação completa; não queda de energia/writer não cooperativo/recuperação workspace-init.

O relatório de paridade e os três documentos de estado ainda apontam execução corrente; nenhum checkbox de P01–P33/captura/DQ/promover/pub/completude foi alterado. Prévio recibo ce7f e inventário1448 estão congelados. Fonte real/target/catálogo/proteção writers continuam necessários para próxima operação integrada; quota sem prova mantém STOP429 somente no dependente. ZERO SQL/TDS/HTTP/provider/PM2/DDL/serviço/ACL/trust/produção/V1. JAMAIS alterar versão/instalação SQL16.0.1000.6. Main/C78/E3/BF/defaults e histórico permanecem intactos.

Próximas ações:
1. Consumir FINAL Banco/Runtime, rever delta/coorte e opt-in padrão/fixture válida; corrigir falhas demonstradas neste bloco, preservando REDs.
2. Qualificar CLI/ledger reais e fechamento, Regras revisa START01..07; concluir ADR/relatório/runbook com resultados observados.
3. Atualizar somente delta AST final, executar verificações aplicáveis e recibo ce7f consolidado; encerrar este único bloco e aguardar decisão sem callback Hermes.
