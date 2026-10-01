# Checkpoint 0290 — P10/G02, dois gates e cobertura de bootstrap aberta — 25/09/2026

## Identificação, autoridade e limites

- Anterior: [0289](0289-p10-g02-ci-diagnostico-parcial.md), SHA-256 `f341abab1d4e6e134b9258c3fe6a6bac52adfefb6575900839589a6d0f3cd54d`.
- Objetivo: corrigir os checks falhos do HEAD remoto exato e provar dois gates de cobertura, sem push, merge ou deploy. Estado `EM_EXECUCAO`; G02/owner não aceitos.
- HEAD local/remoto consultado: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. O último estado remoto observado continua `verify` e `secret-scan` failure, `dependency-audit` skipped; nenhuma alegação sobre novo SHA.
- Alvo dos efeitos: diff local do POM, testes e catálogo; espelhos descartáveis e logs privados sob `target/ci-p10-20260925-01/`. Recuperação: reverter só o diff desta unidade após revisão, sem tocar índice Git real, artefatos históricos ou mudanças preexistentes. Sem endpoint, banco, credencial ou operação produtiva.

## Decisões e evidência

| Critério | Camada | Observado |
| --- | --- | --- |
| Revisão dos testes fracos | Testes focais | Teste isolado de `NONE` removido; cleanup real de statements, pins exatos de oráculo, mutações reais de manifesto/POM; `QualifiedPackagePolicyTest`, matriz temporal, wire e política passaram |
| Escopo misto | Manifesto e teste fail-closed | 117 fontes por path/hash, 116 no gate unitário e `QualificationLineageEvidence` SQL no shadow; fonte nova/alterada, rota adulterada e exclusão adicional reprovam |
| Qualificação pura após package | Failsafe offline | Um IT de correspondência lock/SBOM/recursos/schema, mutante de origin reprovado; teste focal posterior provou admissão bloqueada, retomada idêntica e ausência de processo filho |
| Maven v9 | Espelho privado | `verify` exit 1; 2.299 Surefire, zero falhas/erros, cinco skips históricos; JaCoCo aberto |
| Maven v10 | Espelho privado | `verify` exit 1; 2.300 Surefire + um Failsafe offline, zero falhas/erros, cinco skips históricos; somente `bootstrap` viola JaCoCo Ubuntu 80/60 |
| Gate shadow estrutural v10 | JaCoCo sobre exec unitário | Falha exigente nos três pacotes JDBC e nas classes SQL exatas de Coletas e Qualificação; **não** houve execução física do IT shadow |
| Scanner v10 | Espelho limpo e índice Git só nele | Gitleaks 8.29.1 zero achados; scanner PASS, 3.991 candidatos, 3.990 textos, um binário, zero achado/não inspecionado |
| Graphify | AST local | `graphify update .` terminou em access violation; fallback `--no-cluster` concluiu 794 arquivos e atualizou o grafo |

O lock histórico não foi alterado. Cinco POMs de terceiros atuais têm LF e os pins históricos conferem com CRLF; a fixture offline re-sela apenas seus próprios bytes. `QualificationTemporalMatrix` ficou no gate Ubuntu porque possui carregador puro testado. A primeira exclusão em `qualificacao` tem prova direta de SQL e está no gate físico; a leitura JaCoCo isolada acusa somente `bootstrap` (0,33 linhas e 0,32 ramos contra 0,80/0,60). O teste focal de supervisor e as últimas mutações de política foram executados **depois** do espelho v10, portanto ainda requerem `verify` integral novo. O perfil SQL físico não foi executado: `V2_SHADOW_JDBC_URL` não estava no processo; não é PASS.

## Retomada imediata

1. Classificar por classe os caminhos de `bootstrap`, separar os métodos puros dos que exigem SQL shadow e adicionar testes causais; não excluir o pacote nem classes mistas por proxy textual.
2. Rodar `verify` integral em espelho novo com o teste focal final, corrigir JaCoCo mantendo 80/60 e `-Xmx512m`; validar o gate shadow na camada possível sem chamá-lo de IT físico.
3. Repetir Gitleaks e scanner com índice Git e contagem positiva no candidato final; sincronizar `STATES.md` antes de checkpoint/trilha/validadores.

Dependência externa para aceite G02: novo SHA remoto com três check-runs verificáveis e owner nominal. Nenhum processo de teste ficou ativo neste checkpoint. O trabalho local de cobertura de `bootstrap` é executável e permanece aberto.
