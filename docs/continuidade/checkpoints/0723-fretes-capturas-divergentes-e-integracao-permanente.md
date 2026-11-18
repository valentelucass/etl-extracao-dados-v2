# 0723 — Fretes: três capturas da semana26 ainda divergentes; código permanente em conclusão

Coletas permanece em3/3. Fretes permanece em2/3: semanas19–25/10 e12–18/10 aceitas integralmente nas114 colunas, tuplas, IDs, multiplicidades e50 totais. Nenhum aceite de26/10–01/11 foi emitido.

Comparações reais fechadas e preservadas, sem alteração dos valores para forçar paridade:

| ParAPI/UI | Solicitação | Linhas/IDs por lado | Diferenças |
|---|---|---|---|
|1767319/1767323|16:18/16:18|5221/4589|35Status e113Atualização;2ocorrências de1ID|
|1767581/1767582|17:39/17:40|5224/4592|111Comprovante;1ocorrência de1ID|
|1767618/1767619|17:57/17:57|5224/4592|51Previsão e53Agendado;1ocorrência de1ID|

Nos três pares, todos50 totais são iguais, sem IDs exclusivos ou diferenças de multiplicidade. Campos antes divergentes bateram em capturas posteriores, mas isso não autoriza apagar as divergências nem afirmar causalidade individual. Uma auditoria de evolução entre os seis arquivos foi solicitada ao helper, sem nova chamada ESL.

A correlação do primeiro par reproduziu os114 agregados e passou12 casos sintéticos sobOriginal67068/0,30439ms,223887360B. O único ID divergente temStatus eupdated_at diferentes; a bolsa de atualizaçãoAPI é estritamente anterior àUI. A janela16:18–17:16 é apenas contexto de observações; não prova limites/ordem dos snapshots nem a causa individual. Diagnóstico emwork/xlsx-update-order0722-week26-run02/diagnosis.json.

GETV6 e DOWNLOADV7 foram qualificados com fixtures e OFFLINE. DOWNLOADV7 tinha um erro local de variável foreach($bound) que sobrescrevia worker-bound durante reconciliação. O reparoV8 isolado renomeia o binder, preservando Source/qualificaçãoV7, recibos históricos, prefixo e CAS. Fontes04/05/06 foram fechadas com o mesmo reparo, sem repetir download ou omitir efeitos.

Controle fechadoAUX22:work/fretes-aux-download-reconcile-next-root0719-week26-06/aux-current-control.json,44242B/SHA38CE6AF90C7D9EE92FD00D9EE482424377628466A0F9C1368543E299B9FD8455. Canônico767882B/SHAFA5CD98591A46B4DC66B5EA1B8A558C40A3ADC738C4B7E9D14E396764EB7AF77,ACL B4E214EB2D92C1A0FBFF90AB2BDF72A3CCCF367AC492C02D32DB5B44603008A0,slot vazio. Main19 completos/14 parciais históricos preservados, sem adoçãoMain. SeleçãoRoot atual0722-02 tem seis JVMs após censo fechado13632/0; observação nova antes de cada Source continua obrigatória, sem atualizações automáticas.

Chat de implementação01a1ad23-10eb-77b1-9440-ce1b8f250376 trabalha no fluxo permanente, adaptadorColetas, transporte próprio limitado e comparação auditável. Unidade antiga25pins/57testes NÃO foi executada. Freeze foi liberado para aplicar a versão final e preparar unidade final concreta. HarnessV5 foi revisto estaticamente sem residual(cursor23): versõesJava17/Maven3.9.6 dentroOriginal, POMcopiado com build novo, inventário completo fonte/classes, nomes exatos de casos JUnit, settings inertes. Nenhuma execuçãoMaven/FORMAT/TEST ocorreu; não afirmar compilação ou sucesso do produto. Root é único executor pesado.

Próximos passos: auditar a evolução dos campos entre os três pares; concluir/aplicar código final; formatar e testar sobOriginal; replayJava integral dos pares contra o modeloPython qualificado e131 casosColetas; comprovar chamada operacional e contrato do dashboard. Main/dashboard/entidade permanecem pendentes; V1 permanece pausado; entidadesDataExport e GraphQL somente usuários. SemSQLprodução/deploy/serviços/ACL/identidades alterados.