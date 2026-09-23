# Checkpoint 0032 — B59 verify completo e preparação SQL

Data: 2026-09-09. Anterior: 0031-bloco59-composicao-recuperacao-local.md,
SHA-256 dd037ed8c1dfefa97354187bb9d153036142ab17979149ae6b6dc81814097620.
Objetivo mantém A–F integral; EM_EXECUCAO no fechamento F. Autoridade/limites do
prompt adotado inalterados. Zero API real, SQL físico, .env/credencial, instalação,
agenda, deploy, commit/push ou budget. Inventário inicial 1.532 arquivos preservado.

Resultados novos (logs/exit/surefire em target/bloco59-local/<run>):
- red-null-receipt-01: falha de compilação do teste, preservada, não é RED funcional.
- red-null-receipt-02: 38 testes/1 falha/0 erros/0 skips, exit1. Recibo JDBC com
  updated_rows NULL era aceito como zero; correção requiredCount inclui wasNull.
- users-directed-02: 113 testes, zero falhas/erros/skips, exit0. Inclui recuperação
  tipada, heartbeat, cancelamento via controle operacional e troca de scope.
- verify-01: Maven spotless:apply verify, --offline, Java17, heap512MiB, build novo
  build-verify-01, sem clean. Exit0, 1.377 testes/0 falhas/0 erros/5 skips, 196 suites.
  Formatter, Checkstyle, arquitetura e todos os gates JaCoCo passaram.
- java-result.json: 808 sourceBindings e 211 artefatos com hash. Skips: três testes
  de symlink sem suporte neste Windows; medição V2-050 opt-in; comando fonte B57
  opt-in ausente. Não se adicionou skip nem se enfraqueceu expectativa/gate.
- Test-Bloco59SqlPreparation.ps1 -SelfTest: PASS, seis contraprovas estáticas.
  SQL não compilado/executado. Preparação concreta em database/preparation/bloco59/
  usuarios-runtime.sql; V001–V023/baseline permanecem intactos.

Decisões refinadas: partição explícita do handler; modo/janela GraphQL fechados;
permits vivos de Usuários não podem repetir apply incerto sem readback durável.
Selo durável após DQ, contagens de recibo não aceitam NULL. V007 determina os tempos
SQL: applied_at técnico pode preceder published_at, nunca exigir igualdade inventada.
Preparação confere recibo tipado, aplicações/history e autorização APPLIED.
ADR0040 e runbook B59 criados; matriz deve receber a evidência final de F.

Todos os processos Maven desta unidade terminaram; conferir verify-01/exit.json
antes de qualquer repetição. Nenhum efeito físico desconhecido. Não repetir suíte
sem mudança de Java/teste/configuração ou falha/dúvida concreta.

Próximas ações:
1. Revisar diff próprio de Java e validar JAR somente diagnóstico/recusa; completar
   matriz de critério/teste/evidência sem confundir SQL preparado com aplicado.
2. Criar sucessão exata B59 e snapshots dos arquivos alterados; adaptar somente
   o validator sucessor mais recente; rodar cadeia privada, guards, scanner e UTF-8.
3. Sincronizar STATES/trilha/RETOMADA e checkpoint final; gerar diff contra initial,
   conferir recuperação/reverse --check e emitir recibo após o último delta.
Aceite máximo INTEGRACAO_LOCAL_USUARIOS_TESTADA; nenhum pai/checkbox adicional.
