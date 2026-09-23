# Checkpoint0060 — B63 regressões locais e pacote de prova

10/09/2026. Predecessor0059 SHA-256 baaf91e766482e54fb5ce8f5211aa23f11db74fd4b1a12310af034163c61c028.
Escopo A–D adotado; A documentada, B testada no caminho corrente, C preparada;
D em execução. Nada autoriza SQL/fonte/API/.env/credenciais/UAC/produção.

COL-TIME-02 corrigido no mapper: horário local exige um único offset válido.
Harness B58 usa forRelease e preserva o envelope histórico; ROOT_ARRAY corrente
é exercitado sem fabricar status_updated_at. Dois REDs preservados:27 testes,
6 falhas de gap/overlap;19 testes,10 falhas da ligação antiga. GREEN:259 testes,
0 falhas/erros,1 skip, incluindo Q-FND/B58/current/replay sintético e JDBC mock.
Novos casos JDBC de nanos/captura independente foram acrescentados depois do
GREEN e serão validados no verify completo atualmente em execução.

C: PROVA-REPRESENTATIVA.md e inputs-prova.json preenchidos com paths/versões,
seleções conhecidas,10 casos e limites existentes. Budget/owner/scope/oráculo/
Segurança/janela ratificada continuam ausentes, sem novos valores fabricados.
SQL: proposta para precisão/convergência; migrations e reducers preservados.

Evidência: target/b63-temporal-local-20260910/logs e snapshots de cada fase.
Inventário2365/before; oito deltas permitidos com snapshots públicos exatos.
Sucessão do validador em preparação; não presumir gates finais verdes ainda.
COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 abertos; nenhum checkbox/aceite novo.
Sem efeito desconhecido. Processo próprio: verify-complete em build isolado.
Recuperação: reverter apenas deltas pelo before, sem apagar falhas ou evidências.

Próximas ações:
1. Conferir verify completo e corrigir eventuais falhas locais.
2. Sincronizar STATES/trilha/checkpoint final e selar sucessão contra inventário.
3. Executar validadores/scanner; entregar diff, relatório e recibo.
