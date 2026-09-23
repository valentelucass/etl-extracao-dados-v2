# 0166 — sequências integrais: admissão e executor em prova

## Identificação e autorização

EM_EXECUCAO,15/09/2026. Pedido integral do usuário no anexo adotado; cópia
do mesmo escopo em target/preparacao-macrobloco-campanhas-integrais-20260915-01/
PROMPT-MACROBLOCO-CAMPANHAS-INTEGRAIS.md. Uma entrada e entrega final somente
depois de todas as frentes locais elegíveis. Sem perguntas/subagentes/continue.

Predecessor0165: docs/continuidade/checkpoints/0165-states-execucao-local-concluida.md,
SHA256 c55a15c9918094dd76ab1cae9edbff910c4b618b206b4dcac03e8b146f1d7d88.
Base atual0165/schema102; manter cinco correções,V099–V102,39/45 e67/115.

Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW,Windows integrado,duas travas,
sintéticos rollback-only. Sem fonte real,segredo/.env,V1 executada,DDL já
instalado,COMMIT de domínio,grants,índice Git,commit/push,serviços ou produção.
Novos tetos:1800s/seq,240s/etapa,3600s/campanha,heap512MiB,query60s.
Verify5400s. Não renovar campanhas antigas/runtime-recovery-local-integration.

## Trabalho e evidência

Rodada:target/macrobloco-campanhas-integrais-20260915-01.
baseline-verification.json:PASS;3505 snapshots byte-idênticos em before/,
seis membros da preparação/41pins,24pins do selo/3103membros ZIP,
sete contraprovas de sucessão. Índice Git preservado. Schema102 por recibos
anteriores; nenhuma consulta SQL nesta unidade até a escrita deste checkpoint.

Implementação inicial: LocalArtifactSequence e entrada Main/companheira;
contrato fechado de2–8etapas/pins/modos/revisões, estado SQL compartilhado,
oráculos por etapa. Ação SEQUENCE em ligação ao supervisor/worker;
índice opcional v2, mantendo tetos e v1. ADR0051 e contrato em
docs/catalogos/campanhas-integrais. Nenhuma entrega/qualificação alegada.

Teste dirigido ativo:sequence-contract-01. Conferir process.json/result.json
antes de repetir. Camada Java offline; snapshot anterior às edições posteriores
do supervisor,limitado600s,sem SQL. Testes novos de admissão e fixture A/B;
LocalArtifactSequenceIT ainda não executado. Não atribuir PASS antecipado.

## Próximas ações

1. Reconciliar sequence-contract-01,corrigir falhas e executar prova física
   dirigida dos três estágios A/B, com reserva,preflight,rollback e recibos.
2. Completar agenda/revisões/recomposição/previews/falhas/recuperação e
   contraprovas na sequência; preservar kernels e contratos anteriores.
3. Qualificar quatro escalas,gates completos,pacote/JAR/supervisor,diffs/overlay,
   matriz45/A–N,sucessão,scanner e selo/readback antes da entrega final.

Sem efeito SQL desconhecido nesta fotografia. G01–G08 continuam por parcela
externa; não bloqueiam implementação independente. Resultados posteriores
devem ser consultados em WORKLOG e recibos, preservando esta fotografia.
