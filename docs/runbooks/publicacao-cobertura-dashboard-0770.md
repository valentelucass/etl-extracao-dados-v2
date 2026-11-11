# Publicação operacional de recebimento para o dashboard — 0770

O pacote `database/production/publication/001_dashboard_receiving_overview.sql` fornece `pub.v2_dashboard_receiving_overview`. A consulta usa o catálogo de relatórios e o último lote completo de cada entidade/data em `ctl.production_ingestion_coverage`. Confere a origem, a entidade, o dia e o estado gravado antes de contar. Não lê documentos ou outras colunas de negócio para o dashboard. Se mais de uma origem estiver presente, os totais ficam indisponíveis até definir o escopo; não se somam origens.

**Comparação antes da instalação.** O V1 usa `log_extracoes` como diário de execuções, e Saúde do ETL usa os respectivos contadores de processamento. O teste não os substitui por somas do diário V2. Em leitura na base existente, o teste comparou o resultado com uma contagem independente da view de registros atuais: 11 entidades, 89.703 linhas e 103 combinações de entidade e data. Também reproduziu duas capturas de 100 registros, dia confirmado vazio, dia ausente, ponteiro para execução inválida e duas origens. Nenhuma execução de extração ou carga de 90 dias foi iniciada.

Em Windows PowerShell 5.1, a partir da raiz:

```powershell
.\scripts\validation\Test-DashboardReceivingOverview.ps1 -Mode Reference -ReceiptPath C:\caminho\referencia.json
.\scripts\validation\Test-DashboardReceivingOverview.ps1 -Mode Candidate -ReceiptPath C:\caminho\candidato.json
.\scripts\operations\Install-DashboardReceivingOverview.ps1
```

Esses comandos são de teste: a definição candidata é executada como consulta e os cenários adversos usam variáveis locais ao comando. O instalador sem argumentos verifica o alvo e a compilação, sem gravar. O caminho explícito `-Apply` instala só a nova view no V2 e registra o arquivo no diário de instalação, em transação. Não cria identidades ou permissões; depende do proprietário comum dos schemas e da leitura já autorizada em `pub`. Uma divergência entre o diário e a definição instalada é recusada.

O dashboard consome a view por um novo synonym próprio, via Flyway V070, sem redirecionar os 26 nomes existentes. Não aplicar V070 manualmente ao banco do ETL. O uso está descrito no repositório `dashboards-etl`, em `docs/operacao/cobertura-etl-v2-0770.md`. Não há publicação analítica, corte V1 ou nova tela de negócio nesta etapa.
