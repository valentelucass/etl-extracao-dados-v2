# Prova real de recuperação de timeout — Fretes

Em 04/11/2026, a extração de Fretes de 19/10/2026 concluiu mesmo recebendo dois timeouts da ESL.

- As páginas 3 e 7 receberam HTTP422 com o erro `Tempo limite excedido`, em 2,518 s e 2,274 s.
- O extrator esperou 10 s e repetiu a mesma requisição. As duas páginas foram recuperadas.
- A execução terminou com 946 linhas, 850 IDs, 19 requisições físicas e página final vazia confirmada. Duração: 2 min 34 s.
- A auditoria independente reabriu os arquivos originais, confirmou os dois erros exatos e verificou cada registro entre RAW,JSONL e os apontamentos de origem. Repetições foram preservadas.

A correção é uma recuperação limitada: uma repetição por página, duas por execução, dentro do limite total. Mantém o intervalo mínimo de 3 s após cada resposta. Erros 429 e outros erros seguem interrompendo a execução.

## Conferência do conteúdo

O Excel oficial 1764306, gerado às 21:15 para o mesmo dia e sem outros filtros, contém 946 linhas e 114 colunas. O conteúdo completo foi comparado:

- 112 colunas coincidem integralmente.
- 2 colunas ainda diferem: Custos/Manifesto e Custos/CustoTotal, nos mesmos 10 fretes. A maior diferença observada é 8,87.
- 840 grupos têm a tupla completa de 114 valores igual; 10 têm diferenças. Nenhuma coluna ficou pendente de comparação.
- 48 dos 50 totais numéricos obrigatórios coincidem.

A recuperação do timeout está comprovada nesta execução real. A aprovação de Fretes em 100% e nas três semanas permanece pendente das diferenças de custos.

## Arquivos da prova

Captura: `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\outputs\fretes-day19-real0691-01-actual01\capture.complete\manifest.json`.

Auditoria independente: `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\work\fretes-complete-recovery-audit0691-run02\diagnosis.json`.

Comparação por coluna: `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\work\fretes-current-daily1140691-run01\audit\final-result.json`.

Reconciliação de fechamento: `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\outputs\root-acceptance-fretes-complete-recovery0692-01.json`.

## Limite observado na segunda tentativa

Uma nova execução às 21:44 recebeu o mesmo erro422 duas vezes seguidas na primeira página. Os corpos completos confirmam que a ESL respondeu em4,749s e3,285s; nosso prazo30s não se esgotou. O extrator esperou10s e parou após a repetição permitida. Não houve erro429, aumento de velocidade nem perda de arquivos.

Isso comprova recuperação de falhas passageiras na primeira execução e parada correta diante de falha persistente na segunda. Ainda não comprova que toda execução concluirá. A causa interna da ESL permanece desconhecida; não atribuir a falha apenas à paginação ou aos processos locais sem evidência.

Auditoria da segunda tentativa: `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\work\fretes-partial-retry-audit0693-run01\actual-audit.json`. Original59416/0,Node83544/0,3corpos completos e8arquivos verificados. Fechamento reconciliado: `outputs\root-acceptance-fretes-retry-partial0693-01.json`.
## Nova conferência às 21:58

O Excel oficial1764339, gerado novamente para19/10, foi comparado integralmente com a mesma captura completa:112colunas iguais; apenas Custos/Manifesto e Custo Total diferem, agora em11IDs. Resultado protegido: work/fretes-current-daily1140693-run01/audit/final-result.json. 839tuplas114iguais/11diferentes;48/50totais numéricos iguais. Ainda não é aprovação100% nem3semanas. A próxima captura não começou por recusa do limite de memória do computador antes da criação do processo; nenhuma requisição nova àESL.

## Atualização 0697: falha repetida e conferência dos custos novos

A captura 0694 recebeu duas respostas 422 consecutivas na página 4 e encerrou no limite vigente, preservando 198 ocorrências/180 IDs. Todos os seis corpos completos e fechamentos foram auditados. A recuperação anterior resolveu falhas isoladas; essa nova sequência demonstra que a cobertura ainda precisava ser ampliada.

Nas 198 ocorrências novas, os campos Custos/Manifesto e Custo Total coincidiram com o Excel das 21:58, com multiplicidades preservadas; seis IDs mudaram em relação à API anterior. Evidência: work/fretes-partial-content0696-run02/diagnosis.json. Ainda não existe aprovação completa do dia ou das três semanas.

A bateria de regras PowerShell V2 passou em 13 invocações sintéticas e quatro verificações reais da cadeia, sob Original 21668/0. Java/Node e testes locais de transporte, lentidão e recursos continuam pendentes. O candidato não está adotado e não há prova de solução universal do timeout. Detalhes no checkpoint 0697.