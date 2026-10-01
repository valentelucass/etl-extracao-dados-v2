# Checkpoint 0312 — P29 HTTP/URI v102 offline

## Identificação e objetivo

- 28/09/2026, 21:23 UTC. Anterior [0311](0311-instancia-e-banco-shadow-local-criados.md), SHA-256 `1F3262157211DFC9EF75D09ED2CD4988C39ADF93150751601B8FAE244799AE12`.
- Objetivo autorizado: executar uma unidade offline P29 do mapa P01–P33, com contraprovas causais, correção de defeitos e revisão factual do bloqueio P07/P08. `STATES.md` mantém a autoridade de aceites; nenhum checkbox P01–P33 foi promovido.
- Limite efetivo: não alterar pin 12.8.1→12.8.2; não executar JDBC, DLL, migrations, IT, SQL, fonte, produção, remoto ou cutover. Só teste Maven `test` selecionado, compilação sem teste e SAST no espelho offline. A árvore suja preexistente, locks e recibos anteriores foram preservados.

## Alteração e decisão

- Regra `HTTP-JSON-MEDIA-TYPE-01`: `DataExportHttpExecutor` classificava `application/jsonp` como JSON porque usava prefixo. A classificação diagnóstica agora separa parâmetros e aceita somente media type completo com caixa ASCII indiferente. `DataExportPageFetch.jsonContentTypeDeclared` e template-info são os contratos afetados; não se alterou o parsing do body. Owner de negócio não identificado; Segurança/release devem revisar.
- O XML v96 tinha nove `IMPROPER_UNICODE` nas três classes de configuração HTTP. `HttpEndpointUnicodeBoundaryTest` demonstra, na camada de `URI` e construtores, recusa de host Unicode parecido com localhost e de esquema Unicode, mantendo `HTTP://LOCALHOST` ASCII. A prova não dispõe nominalmente os alertas nem cobre os demais 37 Unicode.
- Revisão da proposta 12.8.2: UAC/instância/banco local deixaram de ser inputs faltantes após 0311. O banco continua vazio; `AGENTS.md`, ambos os perfis shadow, lock e pacote candidato ainda fixam 12.8.1, suspenso. TCP/NP estão off; falta transporte JDBC exclusivamente loopback, contrato de auth Flyway, schema V001–V104, gates atuais do pacote/SCA e decisão explícita do usuário para o pin. O lock normal 12.8.2 não substitui os artefatos shadow.

## Execução e evidência

| Passo | Camada | Esperado | Observado | Recibo |
| --- | --- | --- | --- | --- |
| Contraprova `application/jsonp` | HTTP sintético/Java17 | diagnóstico falso | Primeira chamada parou no formatter; após ajuste de formato, RED 1 teste/uma falha: observado `true`. | `target/shadow-local-rebuild-20260928-01/p29-json-media-red2.log` |
| Correção e regressão final | Java17 offline sem IT | sufixos falsos; variantes válidas verdadeiras | Foco 1/1 e seleção de seis classes 49/49, zero falha/erro/skip. | `p29-json-media-v102-focus2.log`, `p29-http-boundary-v102-regression.log` no mesmo diretório |
| URI Unicode | Java17 offline | três configurações recusam host parecido com localhost | 3/3 PASS, com caso ASCII misto positivo. | `p29-uri-boundary2.log` |
| SAST transitório/final | espelhos offline | não ocultar alerta novo | v101: 273 brutos/88 SECURITY, alerta Unicode novo no parser; v102 após regex ASCII: 272/87/46, mesmo multiconjunto tipo+classe v96, zero erro de scan. | `p29-sast-v101-*`, `p29-sast-v102-*`; XML v102 SHA-256 `02439F26ACAFEFE2E84ACCA42021DC7EB2374B06A6D7CBD0BD9BA140DC613D4E` |
| Grafo | AST local | refletir código novo | `graphify update .` retornou sem reescrever `graph.json`; chamada incremental explícita `_rebuild_code` atualizou 37.272 nós/88.392 arestas. | `graphify-out/graph.json` |
| Segredos e trilha | scan offline/documentos | nenhum segredo; consistência | 4.044 candidatos/4.043 textos/um binário, zero achados; trilha 33/48/9, UTF-8 e diff PASS. | `Invoke-OfflineSecretScan.ps1`, `Test-TrilhaPreparation.ps1` |

A fotografia v102 compilou 656 fontes com Checkstyle zero; nenhum SAST foi aceito ou suprimido nominalmente. Não houve build `verify` integral porque ele executaria ITs proibidas nesta unidade. Rollback da alteração de código, se necessário, é restaurar somente o classificador anterior após nova revisão; o RED demonstrou que isso reabre o falso diagnóstico. Não se reutiliza resultado físico de 22/09 para o banco atual.

## Retomada imediata

1. Aguardar a decisão explícita do usuário sobre o pin shadow 12.8.2; até lá, não iniciar migrations/JDBC/IT nem alterar os artefatos 12.8.1.
2. Após decisão, qualificar o delta delimitado da proposta em novos attempts offline e resolver os gates de auth Flyway/loopback antes da prova física P07/P08.
3. Em rota offline independente, continuar a triagem causal dos achados P29 remanescentes, sem converter prova técnica em aceite de Segurança.
