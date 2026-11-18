# ADR0079 — qualificação de recebimento no banco segregado

Status: implementação em qualificação, 09/11/2026. Não representa liberação do dashboard.

O usuário pediu testes de extração real, intervalo escolhido, recorrência60min e prova física de armazenamento no ETL_SISTEMA_V2, preservando V1/dashboard. A aprovação0758 cobre o banco e os acessos necessários; não repetir essa aprovação.

A entrada `ProductionReceivingMain`, acionada pelo executor de operações, é uma rota opt-in para qualificação do recebimento. Preserva Main, ambientes Shadow, os perfis/contratos C78 e as migrations históricas. Não afirma executar o Main operacional antigo. Reutiliza FaturasClienteIndependentRequest e FaturasClienteIntervalCapture, incluindo as qualificações existentes de52 campos/mapper, terminal vazio, service_at e filtros auxiliares vazios. Não reinterpreta capturas históricas como extrações atuais.

O canal produtivo é limitado a GET4924/info e/data, HTTPS validado, token do próprio V2 por stdin, sem redirect/retry, intervalo3s,17HTTP/16páginas,210s,12000ocorrências/8MiB por partição. O launcher mantém o mutex existente de sondas e respeita STOP429 compartilhado; o banco impede escritores V2 sobrepostos por lock de sessão. Isso não coordena a cota externa com processos V1 que não participam do mutex; os testes devem conservar limites e parar no primeiro erro. Não interromper V1 para contornar esse limite.

O pacote SQL próprio `database/production/receiving` usa instalação owner expressamente autorizada apenas no novo banco. Identidade de execução continua dedicada, sem DDL/admin/escrita livre. Novos entrypoints são acrescentados à allowlist exata, sem grants globais. Instalação manual tem hash/diário real; não há história Flyway inventada.

Armazenamento: ctl.production_ingestion_run conserva cada tentativa e intervalo; stg.production_export_occurrence guarda cada envelope de origem, ordem e SHA256UTF16, sem deduplicar nem converter dinheiro/listas/null. A transação só marca STORED e atualiza ctl.production_ingestion_coverage após a captura inteira qualificada. Leitura JDBC independente compara todos os envelopes e hashes persistidos. STORED significa recebido, não PUBLISHED; core/mart/pub de negócio e referências ainda dependem da qualificação própria.

Cada disparo lê cobertura durável. A janela normal é D-7..D0, oito datas inclusivas, extraídas novamente na origem. Recuperação procura lacunas anteriores à janela a partir de uma âncora explícita; nunca usa MAX(data) como fronteira contínua. Falhas conservam ocorrências anteriores e não avançam a data malsucedida. Resultado incerto exige observação da mesma tentativa, sem repetir a escrita automaticamente.

O ensaio de recorrência usa Windows Task Scheduler e CLI one-shot, dois ciclos reais, próxima execução60min depois de terminar a anterior, conforme V1. Não é daemon nem heartbeat de agente. IgnoreNew impede sobreposição; falha interrompe a campanha, sem retry. A prova só é válida com dois recibos físicos e horários efetivos, não pela existência do agendamento. A identidade Windows existente é usada sem credencial adicional/elevação/ACL.

Escopo inicial: Faturas por Cliente. A qualificação desta entrada não habilita automaticamente outras entidades, não resolve os134 contratos do dashboard e não autoriza corte. Ver [ensaios0759](../catalogos/integracao-etl-v2-dashboard/ENSAIOS-0759.md).
