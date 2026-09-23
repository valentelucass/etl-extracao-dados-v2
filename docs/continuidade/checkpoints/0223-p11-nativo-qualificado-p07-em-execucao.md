# 0223 — Nativo qualificado; P07 em execucao

P11_NATIVE_QUALIFIED_P07_RUNNING. 2026-09-21T23:29:30.090Z
Anterior: docs/continuidade/checkpoints/0222-p11-regressao-local-e-matriz-conferidas.md; SHA-256 058400174be7453843b16877b54051fb204b0827ee1b4a5fd6557425e481f54b.
Objetivo: concluir as pendencias P10/P11/P12/P14/P15/P21 e A/B locais.

Instrucao efetiva: "conclua esses"; depois, "procure e vc mesmo tem a capacidade de saber com base nas documentacoes e testes".
A primeira foi interpretada como adocao das qualificacoes locais A/B nomeadas
no resultado anterior. A segunda determina buscar provas existentes. Nao
constitui ratificacao nominal de Seguranca/Negocio nem publicacao/cutover.
Limites escolhidos pelo executor, registrados antes dos efeitos: serial,
36.000 segundos reservaveis, prazo ate2026-09-22T23:12:37.470Z,4 GETs publicos
Central de ate30s/10MiB, sem retry/redirect. Autoridade/ledger em target/p11-fisico-20260921-01/.
Alvo exato ONLINE confirmado; Windows Authentication, sinteticos, rollback,
sem DDL, commit de dominio, fonte de negocio ou alteracao produtiva.

Prova A:1 IT passou, zero erro/falha/skip; DLL12.8.2 conferida. Agregados
antes/depois iguais. Recibo: target/p11-fisico-20260921-01/native-summary.json, SHA-256 204a91350254d72082d07ecdea47ff8327e453536a3a730fa10d6fee15a32b27.
Lock atual e seis POMs novos correspondem aos pins corrigidos; bytes antigos
preservados em before/ e POMs/licencas historicos. Fonte Java/POM/SQL intactos.
Native01 falhou antes de SQL por PATHEXT; dois diagnosticos offline falharam;
PATHEXT/ComSpec/MAVEN_SKIP_RC corrigidos no executor privado e check03 PASS.
Nenhum efeito desconhecido dessas falhas. Tentativas antigas preservadas.

P07 corrente: p11-p07-verify-01, perfil VerifyPhysical, teto Maven7200s,
wrapper7500s, snapshot imutavel p11-patched-source-01. Processo proprio e
reserva em target/p11-fisico-20260921-01/p07-verify-01/. Nao repetir sem resultado/readback.
Busca delimitada vinculou23 documentos/recibos aos16 requisitos externos,
sem aceite nominal suficiente. Evidencia: target/p11-fisico-20260921-01/investigacao-inputs.json.
39/45 e67/115 intactos; nenhum checkbox novo, nenhum aceite humano inferido.

Proximas acoes:
1. Observar P07, conferir casos/cobertura/readback e resolver falha local se houver.
2. Qualificar pacote atual reproduzivel, sequencias A/B e recusas em reservas proprias.
3. STATES → trilha/matriz → sucessao/validadores → checkpoint/hash → RETOMADA.
Parar cadeia dependente em timeout, drift, processo proprio vazado ou resultado desconhecido; reconciliar antes de repetir.
