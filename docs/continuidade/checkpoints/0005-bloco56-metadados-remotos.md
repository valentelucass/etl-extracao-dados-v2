# Checkpoint 0005 — metadados remotos B56

Em 08/09/2026, sucessor imutável de 0004. Objetivo: P08–P11 e V2-029–032.

O usuário pediu continuidade pela leitura de APIs/documentação e reafirmou o uso
dos `.env` provisionados após a pergunta sobre rotação. Essa instrução posterior
foi aplicada somente à investigação remota limitada de leitura. A pendência
V2-041 não foi resolvida, e nenhuma substituição/invalidação foi presumida.
Não há adoção de SQL, ETL, escrita, rotação, campanha runtime ou saldo B53–B55.

Inventário anterior conferido: 1.352 arquivos, cópias próprias em
`target/bloco56-continuacao/initial/`. O fechamento da preparação B56 permanece
em `target/bloco56/completion-receipt.json`; seus contadores zero são históricos.

`request-info.json` fixou quatro GET `/info`, templates 8636/4924/10633/6392,
serial, sem retry/redirect, 10 s conexão, 30 s chamada, 10 MiB resposta. Testes
offline do leitor de env, isolamento do segredo, parser e recusa de URI passaram.
As quatro reservas receberam resultado observado HTTP 200, curl exit 0;
`info-ledger.jsonl` e `info-<template>.json` registram somente metadados sanitizados.
Não persistidos payload, URL da conta, token, cursor ou ID de negócio.

Os nomes técnicos atuais permitem preparar sondas mínimas dos relacionamentos.
Metadados não dão garantia de identidade, estabilidade, escopo nem cardinalidade.
Nenhum aceite, implementação de vertical ou resolução de identidade nesta etapa.
Nenhum resultado remoto desconhecido; não repetir as quatro consultas `/info`.

Próximas ações:

1. Amostras limitadas a uma página de dois registros e uma data civil fechada,
   com os filtros obrigatórios de cada contrato; parar no primeiro erro/limite.
2. Conferir documentação e schema de consulta para relações faltantes, sem
   promover semelhança de nomes ou unicidade observada a garantia.
3. Registrar conclusões, sincronizar STATES/trilha/RETOMADA e qualificar a sucessão
   exata dos documentos preservando a fotografia da preparação anterior.

Recuperação: nenhum rollback de banco se aplica. Não repetir resultado desconhecido.
Reverter somente deltas próprios após comparar hashes e possíveis edições posteriores.
