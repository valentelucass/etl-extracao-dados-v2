# Checkpoint 0289 — P10/G02, diagnóstico de CI e gates parciais — 25/09/2026

## Identificação e limite

- Anterior: `0288-gitignore-publicacao.md`, SHA-256 `9e83a0ab7afbbdde4c72529e2b7c3a1a9235dce50bfafd3ff683811486952467`.
- Objetivo: corrigir os dois checks falhos do HEAD remoto exato e provar a política de dois gates de cobertura, sem publicação.
- Autoridade: `STATES.md` seção inicial P10/G02 e instruções desta rodada. Estado: `EM_EXECUCAO`; nenhum aceite G02/owner.
- HEAD local/remoto: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. Sem push, merge, deploy, SQL ou credencial exposta.
- Logs e ferramentas privadas em `target/ci-p10-20260925-01/`; não versionar esse diretório. Recuperação: corrigir somente o diff novo, preservar manifests/ledgers históricos e mudanças preexistentes.

## Resultado observado

| Critério | Camada | Resultado |
| --- | --- | --- |
| Checks remotos | API read-only limitada | `dependency-audit` skipped, `secret-scan` failure, `verify` failure; log oficial de secret-scan HTTP 403, parada |
| Gitleaks | Espelho exato e candidato | 336 achados sintéticos no HEAD; seis exceções exatas e seis contraprovas mantêm detecção; candidato Gitleaks zero |
| Scanner offline | Espelho candidato com índice Git e `core.longpaths=true` só local | PASS: 3.976 candidatos, 3.975 textos, um binário aprovado, zero achado/não inspecionado |
| Maven candidato v3 | JDK17/pwsh portáteis privados | 2.274 testes, zero falhas/erros, cinco skips históricos; `verify` sai 1 em JaCoCo |
| Gate Ubuntu parcial | `jacoco:check@check-package-coverage` no exec v3 | Falha em `bootstrap`, `qualificacao`, `persistencia.coletas`, `fonte.dataexport`; apenas três pacotes JDBC excluídos |
| Gate físico estrutural | `-Pshadow-local-integration jacoco:check@check-shadow-package-coverage` no exec unitário | Falha nos três pacotes JDBC, 80/60 exigente; não é execução do IT shadow |

Os cinco pins anteriores eram CRLF histórico; os blobs/worktree/JAR atuais são LF idênticos. O recurso temporal v2 usa hashes LF; v1 preservado. O cap de 512 MiB permanece e os cinco testes antes afetados passaram. A falta de `pwsh` local era específica do Windows; workflow Ubuntu usa symlink Java. Testes causais adicionados para bindings analíticos, expansão, telemetria, oráculos e métricas; os três últimos foram testados focalmente após o `verify` v3. O recurso de cobertura JaCoCo permanece aberto.

## Retomada imediata

1. Fechar manifesto fail-closed por pacote/classe, distinguir exatamente classes SQL físicas de métodos puros em `bootstrap`/`qualificacao`/`persistencia.coletas` e ligar as classes físicas ao gate shadow.
2. Cobrir os caminhos puros restantes sem reduzir 80/60 nem limiares especiais; repetir `verify` completo em espelho com todos os novos arquivos.
3. Repetir scanner indexado e validações de política no candidato final; sincronizar primeiro `STATES.md`, depois trilha/validadores/Graphify, sem alegar CI remoto verde.

Bloqueio externo do aceite G02: novo SHA remoto com check-runs verdes e owner nominal ainda não existem. O perfil shadow físico não foi executado porque `V2_SHADOW_JDBC_URL` não estava configurado no processo; isso não transforma o trabalho local de cobertura em bloqueio. Nenhum processo de teste permanece ativo neste checkpoint.
