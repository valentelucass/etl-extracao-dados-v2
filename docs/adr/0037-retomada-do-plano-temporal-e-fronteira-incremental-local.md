# ADR 0037 — Retomada do plano temporal e fronteira incremental local

Data: 08/09/2026. Escopo: laboratório B54, localhost/ETL_SISTEMA_V2_SHADOW.
Decisão aplicada sob a autorização aditiva e versionada do owner; nenhum grant novo.

## Problema observado

O plano temporal e a execução de origem compartilham a ocorrência, mas possuem
materiais diferentes: a política temporal e o request de extração. Após publicar
Fretes, repetir `run --temporal` encontrava o material operacional na V020 e recusava
a persistência temporal. O SQL preservou as três publicações. Uma janela mensal
também foi publicada, mas o leitor JDBC rejeitou sua sobreposição com planos diários
do mesmo namespace antes de o coordenador selecionar as ocorrências do plano.

## Decisão e consumidores

V021 acrescenta `ctl.fn_runtime_consumed_temporal_scope`, sem permissão direta.
Somente persistência e leitura temporal a utilizam. Ela revalida conta Windows,
decisão e consumo duráveis, papéis, versões, vigências, namespace e os campos do
plano imutável, incluindo o hash de `temporal-intent-v1`. Recusa vincular um plano
novo a uma tentativa preexistente sem janela temporal correspondente. O kernel
operacional continua usando a função original da V020, sem alteração.

O leitor JDBC mantém limite de 64 resumos e exige ordem por início/fim. O
coordenador seleciona as ocorrências declaradas e só avança por janelas publicadas
contíguas. Uma página truncada mantém `limit=true`; um mês não causa conflito com
outro plano diário. Lacuna ou sobreposição dentro do próprio plano continua sendo
erro. Persistir plano não executa fonte; `plan` permanece sem SQL ou HTTP.

Na execução incremental, a primeira janela declarada registra a fronteira pelo
entrypoint já concedido, depois de iniciar a ocorrência autenticada e antes do
fetch. SQL recusa uma fronteira existente diferente. Janelas seguintes e recuperação
não inicializam nem reiniciam a fronteira. O filtro de atualização usa
`extractionStart` com lookback e traduz o fim exclusivo para o segundo inclusivo
anterior. Datas de negócio continuam obrigatórias. BACKFILL usa seu filtro civil;
essa tradução incremental é sintética e não ratifica completude da fonte real.

## Evidência e recuperação

`V021_SCHEMA_01` qualificou upgrade e a cauda pendente do baseline em transações
revertidas, comparou catálogos iguais e voltou exatamente ao estado anterior.
Aplicação e verificação em nova conexão mantiveram 6.215 linhas e 25 grants.
V001–V020 e o catálogo operacional original não foram reinstalados.

A revisão v7 passou em 1.098 testes Java 17 offline, zero falhas/erros e quatro
skips anteriores; estilo, arquitetura e cobertura passaram. `V7_RECOVERY_01`
confirmou repetição do plano de Fretes, retomada mensal, recusa de política trocada
e recusa de RUN do OPERATOR. `V7_LIMITS_01` e `V7_INCREMENTAL_01` contêm as provas
dos limites e do filtro HTTP/fronteira incremental. Consultar o manifesto B54 para
hashes e a conferência final; logs anteriores com falhas não foram substituídos.

Após resposta perdida do instalador, executar apenas a verificação do pacote
`database/proposals/bloco54-temporal-continuation`. Catálogo confirmado permite
retomar com invocação nova e a mesma ocorrência. Divergência mantém a operação
interrompida e exige revisão corretiva versionada. Não restaurar migrations antigas,
apagar janelas ou reabrir tentativas com lease vencido. Não há scheduler ou cutover.
