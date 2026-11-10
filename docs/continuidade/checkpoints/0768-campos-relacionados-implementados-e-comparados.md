# 0768 — Campos relacionados implementados e comparados

Ler [resultado completo](../../catalogos/novas-entidades-0761/comparacao-e-propagacao-0768.md) e [runbook](../../runbooks/atualizacao-campos-relacionados-0768.md).

- Prova real finita: 02–09/11/2026, duas entidades, 36.730 linhas/738.799 valores/16 partições HTTP→modelo→SQL exatos, 396 HTTP na execução final.
- Consolidação: 39 campos; Ocorrências: 20 campos. As 36 ocorrências da regressão anterior foram recapturadas por `RELATED_UPDATED` e confirmadas com os 20 campos nos novos Excel.
- Conteúdo de todas as 36.509 Ocorrências confirmado integralmente em pelo menos um novo Excel. Igualdade com o arquivo posterior: false; snapshot transacional comum não exposto pelo ESL. 0 linhas novas/versões próprias posteriores reencontradas no filtro de atualização. Não confundir reconciliação temporal com igualdade simultânea.
- 161 testes específicos, compilação/estilo/package, reprodução dos 883 registros de Localização e grafo de cinco arquivos aprovados. Runtime privado `propagacao0768/runtime-manifest-v6.json`, árvore Java `8ad7f2a2a846bcb0c420a2ffb14db6296fa522e4`.
- Fotografias anteriores 36.688/737.959 relidas integralmente e preservadas; visão atual correta; dados/acessos/catálogo 11/508/SQL 16.0.1000.6 iguais; zero RUNNING.
- Não executar carga inicial de 90 dias nesta conversa. Nenhuma tarefa permanente, supervisão contínua ou dashboard foi ativado. Checkpoint global da carga inicial inalterado. Operação futura pelo terminal, observada pelo usuário, com espera 60 minutos após concluir.
- Capturas, Excel, JSONL, scripts de prova, JAR e credenciais permanecem no armazenamento privado/arquivos ignorados. Não versionar esses arquivos nem apagar falhas históricas.
