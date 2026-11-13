# Atualização e histórico das onze entidades — 0766

## Regras aplicadas

A primeira importação começa em uma data configurável e abrange pelo menos 90 dias civis, incluindo o dia atual. Esse período é um limite mínimo de entrada; não é retenção móvel e não apaga dados antigos. A âncora persistida só pode recuar. Se o banco já tiver datas anteriores, elas entram na reconferência. A descoberta dessa cobertura usa a procedure de planejamento existente, sem novas permissões.

Cada lote recebe até oito datas iniciais por entidade, começando pelas mais recentes. O progresso é salvo depois de cada gravação confirmada. Concluir um lote não significa concluir os 90 dias: o recibo separa `passed`, `initialComplete` e `initialRemainingDays`. A virada do dia adiciona a nova data ao trabalho; reiniciar o processo não reinicia a carga.

Depois da execução terminar, a próxima aguarda **60 minutos completos**, independentemente da duração da extração. Exemplo: início 10h, término 10h20, próximo início a partir de 11h20. O controle é persistido e respeitado após reinício. Há bloqueios contra execução simultânea no supervisor, na fonte e no banco.

As gravações confirmadas ficam disponíveis na visão de recebimento conforme cada data termina. A atualização do dashboard depende do seu consumidor; este trabalho não publica nem altera o dashboard.

## Filtros observados e exercitados na fonte

| Entidade | Template | Atributos | Filtro de atualização | Precisão |
| --- | ---: | ---: | --- | --- |
| Coletas | 6908 | 31 | `search[scopes][by_updated_at]` | Data e hora |
| Fretes | 6389 | 114 | `search[scopes][by_updated_at]` | Data e hora |
| Manifestos | 6399 | 91 | `search[scopes][by_updated_at]` | Data e hora |
| Cotações | 6906 | 37 | `search[quotes][updated_at]` | Data civil |
| Localização de Cargas | 8656 | 26 | `search[scopes][by_updated_at]` | Data e hora |
| Contas a Pagar | 8636 | 28 | Indisponível | Reconferência histórica |
| Faturas por Cliente | 4924 | 52 | `search[scopes][by_updated_at]` | Data e hora |
| Inventário | 10633 | 26 | `search[check_in_orders][updated_at]` | Data e hora |
| Sinistros | 6392 | 44 | Indisponível | Reconferência histórica |
| Consolidação Detalhada | 11067 | 39 | `search[consolidations][updated_at]` | Data civil |
| Ocorrências | 12072 | 20 | `search[invoice_occurrences][updated_at]` | Data e hora |

O catálogo versionado `receiving-catalog-0766-v1.json` conserva os 508 nomes de atributos e registra os filtros, tipos, ordem e datas obrigatórias observados em 10/11/2026. Os contratos anteriores permanecem disponíveis aos acionadores anteriores. Inventário possui filtro do tipo data e hora, mesmo que o rótulo não explicite a hora.

As requisições mantêm também o filtro de data do negócio desde a âncora histórica fixa. Coletas e Consolidação exigem esse filtro; a consulta de Consolidação somente por atualização foi recusada com HTTP 422. A correção adiciona o escopo obrigatório, sem repetir cegamente a consulta inválida. Foram exercitados nove controles com atualização recente e nove controles futuros vazios, todos com o escopo de negócio. Os resultados recentes passaram pelos mappers reais: 35 ocorrências e 1.127 valores, sem rejeições.

O fuso é `America/Sao_Paulo`. Filtros com hora usam janelas de uma hora e sobreposição de dois minutos, até 24 janelas por execução para recuperar atraso. Cotações e Consolidação usam dias inteiros, com sobreposição do dia anterior, pois a fonte não oferece precisão de hora. O ponto confirmado nunca ultrapassa o corte da execução, dois minutos antes do início. O trabalho pendente conserva sua janela até terminar.

Manifestos não devolve `service_date` entre seus 91 atributos. A descoberta consulta cada data de serviço conhecida, com filtro de atualização, e assim associa as alterações à data da requisição. Não substitui essa data por `created_at`. Essa varredura usa janela de até 24 horas e cursor persistido de até 120 datas por execução; enquanto houver pendência o ponto de atualização não avança.

## Atualização sem perda de registros

O banco atual representa capturas completas por entidade e data do negócio. Portanto, o resultado parcial do filtro de atualização serve para descobrir quais datas precisam ser recarregadas. Ele **não substitui sozinho** uma data inteira no SQL.

Para cada data afetada, o Java busca novamente o relatório completo daquela data, exige paginação terminal, valida todos os campos e grava pela operação atômica existente. A releitura compara todas as ocorrências, ordinais, conteúdo e hashes. Só então o cursor dessa data avança. O marcador da janela de atualização só avança depois de concluir a descoberta e todas as datas afetadas. Falha ou falta de confirmação mantém o trabalho pendente.

Datas repetidas na fila de trabalho são reunidas; linhas físicas do relatório nunca são deduplicadas. Não se inventa uma chave de negócio a partir de números de documento. Cancelamentos e ausências refletem a captura completa mais recente na visão `stg.production_export_latest`; capturas anteriores permanecem guardadas. Nenhum filtro adicional de situação ou exclusão física foi introduzido.

Contas a Pagar e Sinistros percorrem **todo o histórico já carregado**, em lotes de até oito datas por entidade. Depois da última data, a varredura recomeça na âncora. Não há garantia de reencontrar toda alteração antiga no primeiro ciclo de 60 minutos: o tempo depende do tamanho do histórico e dos lotes.

As outras nove entidades também reconferem uma data histórica completa por execução. Essa proteção cobre, de forma eventual, exclusões, mudanças da data do negócio e alterações em campos relacionados que eventualmente não movimentem o filtro de atualização do relatório. A fonte não comprovou uma revisão transacional comum entre API e exportação Excel.

## Componentes e operação

- `ProductionRefreshMain`: entrada explícita do novo fluxo; configuração e credenciais somente pela entrada padrão.
- `ProductionRefreshCycle`: importação inicial, descoberta de alterações, filas duráveis, reconferência e gravação validada.
- `ProductionRefreshPolicy` e `RefreshCheckpoint`: limites, datas, cadência e progresso com integridade SHA e gravação atômica.
- `ReportUpdateContract` e `ReportMetadataCatalog`: filtros observados e validação da revisão atual dos metadados.
- `AllEntitiesIngestionStore`: reutiliza as procedures de recebimento e planejamento já instaladas.

A configuração pública de exemplo fica em `config/production-refresh.example.json`. Ela não contém segredos. `historyDays` aceita 90 a 3660; `historyStart` pode explicitar uma data inicial com pelo menos 90 dias inclusivos. `initialBatchDays`, `reconciliationBatchDays` e `changedDaysPerBatch` aceitam 1 a 8; `manifestScanDays`, 1 a 120. A espera é obrigatoriamente 60 minutos. O estado fica fora do Git, em Armazenamento-Projetos.

`scripts/operations/Invoke-ProductionRefresh0766.ps1` executa um lote por padrão. Recebe `-SettingsPath` e `-RuntimeManifestPath`. `-Continuous` mantém a sequência de execução e espera. Cada trabalhador é iniciado pelo executor compartilhado com quota de 2 GiB; o processo Java usa até 768 MiB. A configuração privada do runtime verifica nove JARs e três scripts por SHA antes de usar as credenciais DPAPI e o perfil TLS existentes.

A variável de usuário `RODOGARCIA_ETL_V2_SQL_CREDENTIAL_FILE` indica o caminho do arquivo criptografado da conta dedicada ao extrator. O acionador usa o caminho privado padrão quando a variável está ausente e valida que o arquivo pertence a esse local e contém a identidade esperada. A variável `RODOGARCIA_SQL_ADMIN_CREDENTIAL_FILE` aponta para a credencial administrativa já cadastrada. As variáveis contêm somente caminhos. As duas credenciais permanecem no perfil Windows que as cadastrou.

Em 13/11/2026, a credencial do extrator foi recadastrada com autorização do responsável. O teste JDBC na V2 confirmou o login dedicado e as permissões para iniciar e gravar uma captura. O manifesto privado `runtime-manifest-dashboard-fields-0794.json` registra os hashes do acionador atualizado e dos nove JARs. Essa revisão reconhece o filtro opcional por número da coleta observado no 6908 e mantém a validação dos filtros de data e das colunas. Para iniciar manualmente a carga noturna no PowerShell do servidor:

```powershell
$privado = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos\etl-extracao-dados-v2\atualizacao0766'
.\scripts\operations\Invoke-ProductionRefresh0766.ps1 -SettingsPath (Join-Path $privado 'operational-settings.json') -RuntimeManifestPath (Join-Path $privado 'runtime-manifest-dashboard-fields-0794.json') -Continuous
```

`scripts/operations/Register-ProductionRefresh0766.ps1` prepara a tarefa própria `ETL-V2-History-Refresh-0766`, para o usuário atual, sem elevação e com início no logon. Recusa substituir uma tarefa existente. Registrar a tarefa não inicia a primeira execução imediatamente. Nenhum serviço, conta, grant, ACL ou versão do SQL é alterado.

Na pasta de estado, `checkpoint.json` guarda progresso; `reports/` contém recibos sem dados de negócio; `captures/` conserva respostas e envelopes privados. `supervisor.json` registra o próximo horário elegível. `STOP.operator.json` interrompe novas admissões pelo supervisor; não aborta o trabalhador em curso. Falha cria `STOP.failure.json` e impede repetição automática. O STOP compartilhado de HTTP 429 também é respeitado. Antes de retomar uma falha, investigar e reconciliar a execução no banco; não remover um STOP apenas para tentar novamente.

Cada trabalhador tem limite de 105 minutos de fonte, 2000 requisições e limite externo de 115 minutos. O lote suspende trabalho antes de admitir nova requisição ao atingir 1750 chamadas ou faltar 90 segundos para o prazo. Cada captura admite até 200 páginas, 100.000 linhas e 40 milhões de caracteres acumulados. Fretes usa 25 raízes por página; demais entidades, 100. As chamadas têm intervalo de três segundos após conclusão, 30 segundos por requisição e resposta máxima de 10 MiB. Não há redirecionamento nem repetição automática de chamadas com falha.

Uma captura interrompida por limite não vira snapshot parcial no banco. Seu trabalho fica pendente para a próxima execução. Se uma única data exceder os limites de captura, precisa de diagnóstico específico; não ampliar limites ou aceitar conteúdo parcial automaticamente.

## Verificação executada e limites do aceite

O conteúdo exato dos 15 arquivos de código/contrato preparados no índice passou por compilação, Spotless, Checkstyle, empacotamento e 142 testes específicos. Os cenários incluem 90 dias em lotes, falha de gravação, retomada, descoberta de atualizações, varredura sem filtro de atualização e espera após reinício. Quatro controles adicionais exercitaram o supervisor e três scripts passaram pela análise de sintaxe. Isso não declara toda a suíte histórica do projeto aprovada.

Em 10/11/2026 foi executado o novo acionador real, em lote finito de uma data por entidade, mantendo a política mínima de 90 dias. As onze entidades passaram, com 59 requisições em 254 segundos. A conferência independente reabriu HTTP, JSONL e SQL: **5.973 ocorrências e 264.350 valores idênticos**, onze partições, incluindo vazios terminais legítimos em Consolidação e Sinistros. O checkpoint registra 89 datas iniciais restantes por entidade. Esse teste não equivale à importação integral dos 90 dias nem a dois êxitos consecutivos do agendamento real.

A nova tarefa permanente **não foi registrada nem iniciada** nesta qualificação. O runtime privado qualificado é `atualizacao0766/runtime-v1`; seu manifesto permanece externo ao Git. O runtime e a falha históricos 0762 permanecem preservados. Nenhuma migração SQL adicional ou corte de dashboard foi realizado.

O confronto Excel anterior permanece documentado em [comparação0762](../catalogos/novas-entidades-0761/comparacao-excel-0762.md). Os 39 e 20 atributos das duas novas entidades estão modelados, mas as diferenças temporais observadas não foram transformadas em equivalência de 100%. Esta entrega não realizou outro download Excel e não declara que essa pendência foi resolvida.
