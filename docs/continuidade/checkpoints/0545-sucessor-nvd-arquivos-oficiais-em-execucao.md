# Checkpoint 0545 — sucessor NVD por arquivos oficiais em execução

23/10/2026. Continuação de [0544](0544-ferramenta-auditoria-preparada-scanner-real.md), na mesma autorização atual do usuário para avançar V2 enquanto ausente. A unidade API01 foi encerrada como parcial e substituída por uma unidade técnica nova, delimitada antes de seus efeitos. Nenhuma captura ESL ou SQL Server foi executada.

## Ocorrência API01 preservada

A consulta paginada da NVD progredia lentamente em relação ao prazo original. Root decidiu usar o mecanismo de carga inicial por arquivos oficiais suportado pelo scanner fixado. O marcador `cancel-requested` foi registrado às18:04:20BRT; o supervisor parou às18:04:34. A fonte API não produziu relatório nem validação final.

Foi constatada uma limitação do supervisor auxiliar:parar o Python não fazia o executor fechar imediatamente os descendentes, pois `JobRunner.cs` aguarda `ActiveProcesses==0`. A JVM Maven continuava ativa. Root confirmou a identidade da JVM33460 por caminho exato dos settings da unidade01, classe Maven Launcher e horário de criação correspondente; encerrou somente esse processo próprio às18:07:13BRT. V1, WMS, SQL Server e outros projetos não foram tocados.

Último progresso preservado no log:90000/413810registros anunciados como baixados. Isso não prova90000rows integralmente persistidas, cache completo ou freshness:download/processamento eram assíncronos. O cache parcial não recebeu aceite. Recibo final01:exit1, sem timeout,764260ms, pico1064259584B. O resultado RuntimeError e a ocorrência de fechamento foram preservados, sem promover a unidade a PASS.

## Mecanismo oficial conferido

O [código oficial da versão12.2.0](https://raw.githubusercontent.com/dependency-check/DependencyCheck/v12.2.0/core/src/main/java/org/owasp/dependencycheck/data/update/NvdApiDataSource.java) implementa `nvdDatafeedUrl` e a obtenção dos metadados individuais `.meta` quando não existe `cache.properties`. O descriptor local obtido/checksumado em0544 também contém esse parâmetro. Não foi presumida compatibilidade pela documentação de outra versão.

URL do mecanismo selecionado:

```text
https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz
```

GET do arquivo oficial `nvdcve-2.0-modified.meta` retornou200, sem redirect, com body preservado pelo sucessor. Observação21:08:18UTC/18:08:18BRT; `lastModifiedDate=2026-10-23T16:00:09-04:00`. SHA256 do body de metadata:`03b0f280aeca71d9bab1692e49951512da050dac08dfca46c477fa9c27939fa7`. O checksum publicado do JSON não comprimido também foi registrado, sem alegar que essa observação isolada já comprova a importação dos arquivos.

## Sucessor técnico02-bulk

Plano novo ligado aos hashes do controlador, resultado e cancelamento01; mesmo JAR `10D275EE4F0AB342A04FC5FD924E4735F076D35E3230118FE063CBEA1A2A9BA3`, scanner12.2.0, POM e policy. Mantidos threshold0.0, failOnError, exceções locais vazias e todos os analisadores configurados. Mudou o canal oficial de carga inicial NVD; não houve mudança de identidade/egress, arquivo `.env`, caller ESL ou trust global.

`PolicyOnly` e `VerifyImplementation` passaram novamente como preflight da unidade nova; continuam sendo os mesmos33casos lógicos, sem multiplicar a contagem por rodadas. Scanner iniciado em23/10 às18:08BRT, com uma vaga pesada,2048MiB por árvore/processo, até1200s no scanner e22min no executor. Escopo padrão do perfil continua excluindo test scope/plugins; não é auditoria de todo o projeto.

O controlador sucessor passou a encerrar a árvore do subprocesso que ele próprio cria quando ocorre cancelamento/limite, antes de sair. O PID vem do handle do `Popen` dessa própria execução; não busca ou encerra processos por nomes genéricos. O executor continua sendo o limite externo da árvore inteira. Essa correção é auxiliar, fora de fonte/JAR/POM do ETL.

**Execução em andamento.** A confirmação de metadata, o início ou o cache anterior não são relatório concluído. Aguardar resultado real, JSON/HTML quando existentes e validação da política; preservar qualquer erro/parcialidade. Nenhum aceite G05 nominal ou release foi emitido.

## Recuperação da trava própria e início dos downloads

O sucessor encontrou `odc.update.lock` da unidade01 e aguardou a atualização anterior. Conferências:arquivo32bytes criado/escrito às17:54:46, JVM anterior33460 ausente, somente a JVM Maven59092 da unidade02 presente para esse goal e log desta JVM explicitamente em espera. O cache não existia na conferência anterior ao início da unidade01.

Root resolveu o caminho absoluto e confirmou que o alvo era exclusivamente `C:/Users/suporte/.m2/dependency-check-data/odc.update.lock`. Preservou os bytes e SHA256 em `stale-odc.update.lock.original`/`stale-lock-recovery.json`, reconferiu o hash e removeu somente essa trava órfã. Nenhum arquivo do DB foi removido. Não repetir a recuperação sobre a nova trava ativa nem inferir propriedade por CreationTime isolado.

O scanner avançou para `Checking for updates` e iniciou downloads oficiais dos arquivos anuais NVD2.0. Isso comprova a liberação da espera e o início da carga, ainda não a conclusão de todos os arquivos ou do relatório. O aviso de last-checked ausente foi preservado e determinou atualização integral, sem aceitar o cache parcial01 como fresco.

## Evidências e limites

- Unidade01: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/auditoria-dependencias-20261023-01`, incluindo `cancel-requested`, `owned-maven-termination.json`, log, resultado e receipt final1.
- Unidade02: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/auditoria-dependencias-20261023-02-bulk`, incluindo controlador, plano, metadata original, logs, resultado corrente e receipt ao término.

ESL HTTP, token ESL, SQL Server e TDS:zero. O cache H2 público da ferramenta não é carga ETL e não altera a versão SQL16.0.1000.6. V1/WMS não reiniciados. Franquia/escopo/saldo ESL, Fretes6389/422, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, C78 rollback-only, cobertura/oráculos/paridade/retenção e proibições permanentes seguem pendentes/preservados. Não iniciar segundo scanner enquanto esta unidade estiver ativa.
