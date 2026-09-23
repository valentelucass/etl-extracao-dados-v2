# 0180 — P03: regressão observada; contraprova MC corrigida em execução

19/09/2026. Continuação de0179. Campanha07 preservada:30unit/6IT PASS, rollback.
Schema104 permanece instalado; nenhuma migration ou fonte de produto mudou.

p03-regression-sql-01 terminou OBSERVED/exit1/rollbackConfirmed=true em7m40s.
66IT:65PASS,1erro,zero falhas de asserção ou skips. Passaram Cotações6, replay1,
isolamento2, integração relacional12, falhas de sequência4 e40casos da matriz
relacional anterior. A nova contraprova MC parou no construtor RelationalBinding:
REL_LAB_BINDING_INVALID, antes de bind/resolve. Seus quatro evidenceId não tinham
o prefixo synthetic- obrigatório. O erro e seus XMLs continuam preservados.

Correção causal exclusivamente no teste: nomes técnicos synthetic-set-old,
synthetic-set-other, synthetic-set-new e synthetic-set-conflict. Nenhum ID de
domínio, relação, cardinalidade, frescor, fixture da campanha ou expectativa mudou.
Regra de validação não foi relaxada.

Nova admissão p03-relational-counterproof-02 em execução, somente
RelationalLaboratoryMatrixIT, reserva própria600s/heap512MiB/SQL≤60s, dados
sintéticos e rollback, após reconciliação do terminal01. Sem saldo anterior.
Os outros25IT de01 serão reutilizados mediante igualdade dos bytes de produto
e consumidores; nenhuma campanha aprovada será repetida sem causa.

Rodada: target/macrobloco-campanhas-integrais-20260915-01/.
Consultar result.json, process.json, XMLs e before/after antes de repetir efeitos.
Próximas ações:
1. Conferir terminal/41IT/rollback da contraprova02.
2. Vincular bytes finais às provas, diff e gates aplicáveis, preservando falhas.
3. Sincronizar STATES/trilha/checkpoint/RETOMADA e entregar apenas o recorte.

39/45 e67/115 preservados. P03 inteiro e paisV2 abertos; P04–P08 não iniciados.
Recuperação por rollback das provas; DDL persistente só por migration nova revisada.
