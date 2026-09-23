# Checkpoint 0276 — qualificação técnica de sombra e validador Manifestos

- Data: 22/09/2026 (America/Sao_Paulo).
- Anterior: `0275-cotacoes-6906-continuacao-0309.md`, SHA-256 `b5c32baa03afb4a28449517e1494b349d6b8b3f1b8df4c24db871b6ac13fb83e`.
- Objetivo do usuário: executar de uma vez a alternativa técnica de sombra para voltar às conclusões de código do `STATES.md`.
- Estado: correção de validador comprovada; qualificação global de build ainda aberta no gate JaCoCo; P17–P29 sem novo aceite.

## Autorização e limites

O pedido vigente autoriza a validação local de sombra e correção de defeito
comprovado. O `AGENTS.md` limita SQL ao banco
`localhost/ETL_SISTEMA_V2_SHADOW`, autenticado por Windows, e a IT JDBC ao
perfil opt-in e conexão rollback-only. O `master` confirmou o alvo antes das
consultas e da IT. Não houve consulta ESL/Raster/GraphQL, sonda 4924, banco
produtivo, DDL/DML produtivo, deploy, agenda, credencial ou cutover.

## Alterações e decisão

- `database/validation/042_validate_manifestos_shadow_vertical.sql`:
  a condição `PREPARE` aceita a assinatura da procedure reducer V022 ou a
  assinatura V012 anterior; exige kernel e travas V022 quando aplicável.
- `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md` e `BLOCOS_ETAPA_2.md`:
  resultado por camada e gate global aberto, sem marcar caixas B17–B29.
- Working tree tinha 1.150 entradas preexistentes; nenhuma foi descartada.
  Logs foram criados em `target/shadow-technical-delivery-20260922-01/`.
- Exercícios 041/043/045/047 não foram usados porque seu reset histórico
  exige schema vazio, incompatível com o shadow instalado.

## Execução e evidência

| Passo | Camada | Observado | Evidência local |
| --- | --- | --- | --- |
| Maven inicial | offline | Enforcer parou no JDK 25 herdado | `maven-verify.log` |
| Maven JDK 17 | offline | 5 erros por heap acima de 512 MiB no teste de escala | `maven-verify-jdk17.log` |
| Maven JDK 17 / 512 MiB | offline | 2.263 testes, 0 falhas, 0 erros, 5 ignorados; `verify` falhou no gate JaCoCo por pacote | `maven-verify-jdk17-heap512.log` |
| Cinco verticais | PowerShell | 5/5 passaram | `Test-*ShadowVertical.ps1.log` |
| Schema e governança | PowerShell | manifesto, gate progressivo e 20 autotestes do scanner passaram | logs correspondentes |
| Scanner integral | offline | varredura final: 3.966 candidatos, 3.957 textos, zero findings | `Invoke-OfflineSecretScan.final.log` |
| SQL read-only | shadow local | 038/040/044/046, 055/057/061/062 passaram; 042 falhou antes da correção e passou após ela | `*.sql.log` e `042_validate_manifestos_shadow_vertical.corrected.log` |
| JDBC sintético | shadow local | 1/1 IT passou com opt-in; contagens `execution_audit/page_audit` 0/0 antes e depois | `shadow-audit-local-it-quoted.log`, `shadow-audit-counts-{before,after}.log` |

Primeira invocação da IT teve erro de parse Maven/PowerShell antes dos testes;
o log foi preservado. Nenhum efeito físico ficou sem reconciliação. A
correção foi validada contra a procedure V022 instalada; não constitui
contrato oficial do fornecedor nem paridade de dados reais.

## Retomada imediata

1. Engenharia V2: definir e autorizar o conjunto de ITs locais que completa
   o gate JaCoCo, mantendo os limites de `AGENTS.md`, e obter `mvn verify`
   verde sem reduzir limiares.
2. Fornecedor/owner de Cotações: entregar release 6906 com identidade e
   semântica de paginação/terminal; owner de tarifas: referência aprovada.
3. Negócio/responsável de dados: fornecer janela fechada e oráculo
   independente para P17; a sonda financeira 4924 só reabre com condição
   externa nova registrada antes do efeito.

Condição de parada: falta de escopo para ITs adicionais ou de input externo
real; não converter testes sintéticos em aceite P17–P29. Conclusão global
exige gate de build verde e os critérios reais de cada entidade.
