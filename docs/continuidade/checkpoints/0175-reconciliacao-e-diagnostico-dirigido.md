# 0175 — Estado reconciliado e diagnóstico dirigido admitido

## Identificação e objetivo
19/09/2026. P01 reconciliado para provas dirigidas; P02/P03 EM_EXECUCAO.
Anterior:0174-uma-entrada-uma-entrega-por-macrobloco.md, SHA256 be324bdcb931c8bc49688954df050416ed28ff66e37594bac233fce03385c8b7.
Pedido atual: estabilização P01→P02→recorte P03; não P04–P08.

## Autorização e limites
Pedido19/09 e controlador da campanha15/09: SQL sintético localhost/ETL_SISTEMA_V2_SHADOW, Windows, duas travas, commit bloqueado/rollback, semDDL.3600s por tentativa,1800s sequência,240s etapa,512MiB,query60s. Não há renovação de reservas antigas; resultados04/05 conhecidos. Controlador usa reserva por tentativa, sem expiração/calendário ou saldo global registrado; nenhuma autorização externa inferida.

## Alterações e decisões
Inventário3540 e cópias antes em target/macrobloco-campanhas-integrais-20260915-01/stabilization-20260919/. Delta da base:25modificados/35adicionados/zero remoções adicionais; oito exclusões Git já preexistiam. Índice preservado. Nenhum manifest histórico regravado.

## Execução e evidência
historical.json: validador de sucessão contra before0165 PASS com7contraprovas;3505hashes baseline conferidos. reconciliation.json:04/05 OBSERVED/exit1/rollback e arquivos agregados idênticos, processos próprios ausentes.05 contém3etapas de referência, mas executor retorna2 por gate interrompido; expected3/actual2 mascara a divergência anterior. A/B05 usa6etapas; atual7, não qualificada. collectionReferences falha por quantidade inferior às raízes; hipótese pendente: troca de origin_component impede supersessão V034.
Sucessão da prova: histórico validado no snapshot preservado, delta explícito e novos inputs por tentativa. Isso não fecha sucessão de entrega P08; validator histórico no worktree continua falhando legitimamente.

## Próximas ações
1. Reservar06 e executar diagnóstico A/B, referência, recomposição e unit dirigidos; resultado esperado discrimina comparação vs cardinalidade, não transforma falha em PASS.
2. Corrigir apenas causas dentro do escopo; se exigir migration/DDL, preservar falha e concluir frente independente.
3. Sincronizar estados/trilha e checkpoint final. Nenhum pai V2/P03 inteiro concluído.