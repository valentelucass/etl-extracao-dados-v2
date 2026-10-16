# Checkpoint0446 — troca de modelo; pacote retido qualificado, SQL ainda não iniciado

Data:05/10/2026, fechamento após20:43UTC. Usuário: “vou precisar mudar de chat para mudar de modelo, documente para continuar dese ponto”. Anterior:0445, SHA256 `677ABE2B44CACE034600314E108699B5338A47CAAFAF00C7616FE05C0F22800D`.

STATES é canônico. Ler AGENTS, topo de STATES, RETOMADA e runbook continuidade-agentes. `../CONTEXTO_GLOBAL.md` foi procurado e continua ausente; não recriar. Este checkpoint registra continuidade; não cria autorização nem renova orçamento.

## Objetivo e autoridade preservados

Preparar o banco de testes e extrair as onze entidades para testar, retendo dados. Primeiro marco: uma captura Coletas6908 em ctl/stg, limitada e honestamente parcial, com commit conhecido e confirmação independente. A extração API anterior, às17:30UTC, comprovou1entidade/1linha em memória; não houve SQL nela.

Usuário aprovou “pode, so n pode mudar versao sql server” para `USER_20261005_E3_TEST_RETAINED`, perfil separado `TEST_RETAINED_LOCAL_SQL_PIN_V1`, alvo literal `localhost:1433/ETL_SISTEMA_V2_SHADOW`, sa existente via provedor externo. Não pedir novamente permissão genérica, banco, usuário ou senha. ADR0060, AGENTS e `target/test-database-all-entities-20261005/authority01.json` conservam o contrato.

Pin DER imutável: `E3A5A7061FB56C225F93CC330CD733F5911F7D3F420D608EBE9AFA7371ACE276`; exceção DNS somente localhost/loopback neste perfil. Autoassinada CN=SSL_Self_Signed_Fallback, RSA3072/SHA256, sem SAN, válida2026-10-05T12:36:39Z..2056-10-05T12:36:39Z. Procedência independente não comprovada. Validar pin, validade, algoritmos, posse TLS e peer antes da autenticação; folha diferente recusa sem aprendizado.

Preservar SQL16.0.1000.6, contas, permissões, credenciais, identidades, serviço/listeners/certificados/trust global e produção. SAMPLE BF antigo permanece rollback-only e inalterado; Main/defaults não ampliados. V1 foi deixado parado pelo usuário: nenhuma ação PM2 por suposição. Antes/depois de cada operação física, ler versão de produto/arquivo e hash atuais do executável; não confiar somente neste texto.

## Estado fechado nesta sessão

- **Agregado04 PASS**, snapshot com4282inputs, dez paths futuros DE/Users excluídos; não é aceite da árvore corrente inteira.
- Surefire2669/0falhas/0erros/5skips; Failsafe7/0/0/0. Formatter/checkstyle/cobertura PASS, controle675/800linhas=84,375%; drift snapshot/fontes0.
- Preflight do JAR com providers sintéticos PASS,9JARs/0SQL/0HTTP; vínculo catalogal offline24tabelas/11módulos PASS.
- Processo agregado próprio52752 ausente; sessão89748 terminou exit0. Banco, Fontes e Runtime encerraram ociosos. Nenhum callback, rotina, processo de qualificação ou operação física deixado em execução.
- **SQL/HTTP reais, reservas, DDL, commit e dados retidos nesta unidade: zero.** Não existem catalog01-dispatch, retained-capture01-dispatch, retained-readback01-dispatch, catalog-execution01 nem retained-capture01.
- Readback independente foi qualificado offline, mas **readback-freeze01.json ainda não foi criado** contra o pacote final.

Unidade privada de todos os caminhos abaixo: `target/test-database-all-entities-20261005/`.

| Evidência atual | Resultado / SHA256 |
| --- | --- |
| runtime/aggregate04/receipt.json | PASS;83654f53f4c8e9973bea039659320464794682c9aa696f189ed66ba54fee0c6b |
| runtime/aggregate04/input-manifest.json | 737867b370c2b6e77e05dc68e0b63c16b9ba1863c3de752b49e4d33620e17a30 |
| runtime/aggregate04/runtime-package-manifest.json | 9JARs;b98d54fda16f4aef4562ab3931894c384cf3d3f58e8610c90c6eb332e970fb3b |
| runtime/aggregate04/build-snapshot/target/etl-dataexport-v2.jar | 0503636d94e1247d2ff3a0c473f0c55280f14f147e68ec2d3c4f977b19f9f4f2 |
| banco/implementation-manifest05.json | 135membros;82319d564cbc37db84eb9dd21f0ccabaadc23e4292be834767546f81f559dfcc |
| runtime/final-manifest01.json | 64membros;95962111c33292266b776810f2c02c0784970b2711c62a53b3f41889ed3480e7 |
| runtime/diagnostic-controller01/controller-freeze01.json | 166membros+2externos;93e8d0f88d1130d27c4cf6931ecec2bf0f86b72fed778c2d6b3e2743fde23520 |
| runtime/capture-controller-freeze01.json | 228membros+2externos;cde1b01809876f59ae2294f02b0b4c46eb580934dcdb1a70781efc67bf1e2c5f |
| runtime/continuity-final01.json | Handoff completo;79b9f42e411bfb60c55b7f7dcaf4d1f5095d291349ca8abde1ee63f588b83e66 |
| supervisor-handoff-final-review02.json | Root232arquivos únicos/11artefatos conferidos sem drift;3abf4a0ddc3ebecf1e99333557788d4e4887ec8fe8e6b794731054148c744c20 |

Root também conferiu banco135, pacote9, DE03 54, patch futuro28 e protótipo6 membros sem drift. Revisões específicas ficam nos arquivos supervisor-*-review*.json. O review final01 foi preservado: contagem auxiliar de lista nula dizia1externo no manifesto Runtime;02 corrigiu somente para0, sem mudar a prova dos232arquivos.

## Correções comprovadas e histórico que não deve ser apagado

Agregado01 falhou no formatter;02 falhou em duas regras arquiteturais;03 passou2660+7testes, mas recusou cobertura611/800<80%. Banco acrescentou sete provas causais; revisão Root encontrou setup do escopo TLS fora da sanitização/cleanup. RED07 reproduziu2falhas; GREEN08 passou24testes e corrigiu somente Connector, incluindo SCOPE_CREATION_REFUSED antes do driver e preservação de escopo preexistente. Runtime qualificou cinco contraprovas do novo motivo. Não repetir esses testes/builds apenas pela mudança de chat.

Architecture real permanece SHA256 `fc95b1d26b9efc35a227fb5d6d0e18af560f705e2055b605a7dd1d377d799b51`. CI mantém quatro classes bootstrap UNIT_INCLUDED e hash de Source com pacing2s, sem redução de limiar/exclusão. Scanner selftest45 PASS, global06 4293candidatos/0achados; preparação03 PASS33etapas/48abertos/9pacotes e diff-check PASS. Preservar scanner05 FAIL sintético, READ/REDs, manifests e comparações auxiliares de terminadores03→04.

## Próxima unidade física — preparar, depois executar pelo owner

Banco continua **único executor SQL**. Root coordena/despacha e é único editor dos documentos/CI; Runtime controla pacote/launchers; Fontes cuida das origens. Na nova sessão, conferir os handoffs locais antes de reativar agentes; não presumir que nomes de agentes/processos da sessão anterior são reutilizáveis.

1. Conferir freezes/pacote já aprovados e concluir offline `banco/retained-readback01/Freeze-Readback01.ps1`, que produz freeze próprio contra aggregate04 e controlador final. Não editar os bytes atuais nem aplicar o patch futuro de arquitetura antes do primeiro marco Coletas. Root já tem `Issue-CatalogDispatch01.ps1` preparado para04, **nunca executado**. Somente quando Banco estiver disponível, Root emite um despacho novo com hash revisado do controller-freeze; prazo externo10min, reserva física pelo Banco antes de qualquer efeito. Banco executa `runtime/diagnostic-controller01/Invoke-Diagnostic01.ps1`:1conexão/10SELECTprevistos/cap12/query10s/deadline60s, zeroescritas/HTTP/retry. Contrato físico deve provar24tabelas/11módulos, TLS/identidade/sessão limpa/close e versão/hash PREPOST.
2. Com catálogo comprovado, Root usa `Issue-RetainedUnitDispatch.ps1 -Unit Capture` com hash revisado do capture-freeze; Banco executa o controlador preparado. Request imutável: `runtime/retained-request01.json`, SHA `e4a6ec4247e736fd994b96e80bfc04e3b7c7e579c81572b252e3bc46c64f6e1a`, recorte01/10. Captura:1conexão/até1200execuçõesSQL/1000linhas físicas/per1/página1/2HTTP (INFO+DATA)/30s/10MiB/60s, pacing2s. Namespaces TEST_RETAINED_SOURCE_20261005 e TEST_RETAINED_SCOPE_20261005 exclusivos da ocorrência; ocupação inesperada exige reconciliação. Proteção contra writers/readback dentro da própria transação; commit só pelo owner. Término honesto ctl.CANCELLED/TEST_PAGE_LIMIT, staging em stg.coleta_record/stg.execution_record, sem DQ/core/pub/promoção.
3. Após commit conhecido e processo/sessão fechados, Root despacha `-Unit Readback`; Banco usa wrapper `banco/retained-readback01/Invoke-RetainedReadback01.ps1`:nova conexão/5SELECT/query10s/30s/0HTTP/0escritas. Somente após readback independente, close e versão/hash PREPOST iguais pode emitir CAPTURE_RETAINED_PARTIAL. Depois avaliar próximos destinos/fontes. Falha pré-commit reverte sua transação; resultado incerto exige reconciliação por ocorrência antes de repetir. Nunca limpar dados retidos automaticamente.

STOP no primeiro não2xx/429/limite inválido; sem retry, redirect ou fallback. Inventário não autoriza Flyway, usuário/grant nem presumir ausência de schema. Nenhum comando físico foi delegado para rodar automaticamente depois deste handoff.

## Demais fontes — prontidão real, sem misturar com Coletas

- Raster:34testes offline, transporte HTTPS limitado, freeze existente em fontes/raster-http. Nenhuma Raster real.
- Users observacional:first1/página1/1HTTP,10testes PASS; vazio/hasNext/quarentena permanecem parciais. `fontes/users-observation/manifest01.json`, SHA0585ee4b65a7ead69874976d11de48d887fd5ab9ae01201f6ad34b99403cd8e2.
- DE físico03:57testes PASS (51novos+6regressões),54membros; `fontes/dataexport-observation03/handoff03.json`, manifestSHA5c5ae47bafa0d2ab22f14deb98e50ed88156fade166629cb235526ab3bc5791e. ObservePhysical valida corpo/envelope inteiro e0/1registro+até1associado conhecido, CAP sem raiz inventada; sourceEntities/remotePer UNVERIFIED, binding nulo e completude falsa. Sete templates MAN/COT/LOC/CAP/FAT/INV/SIN continuam sem despacho de dados aprovado; não converter esse recibo em prova do gate de entidade/per. Pergunta documental opcional ficou sem resposta; usar documentos existentes sem inventar confirmação.
- Patch arquitetural privado atual: `fontes/future-observation-architecture02/proposed-architecture-and-bound-tests.patch`;22testes PASS,28membros, manifestSHA40e111fdf860f8c914082328e17a6e91fcc4f9488ca4f3d1db47d7c08c7d85e5. Cinco APIs exatas, NÃO aplicado. Adotar só em sucessor futuro com manifesto próprio; freezes predecessores preservados, seus fontes correspondem aos snapshots antigos.
- Banco futuro: `banco/resume-handoff01.json` e `banco/observation-prototype01/`. Protótipo privado de duas tabelas ctl.test_observation_capture/stg.test_source_observation,26checks parser/AST PASS,6membros/manifestSHA9162548a7f56fbf7e1052526c52e291577349c8c228abc37b383cac222f69736. Não existe adaptador Java implementado, DDL executado ou ausência instalada provada. Proposta catalogal adicional1conexão/8SELECT/10s/60s ainda sem reserva. FRE/MAN/COT/LOC/Users têm footprints próprios; pais CAP/FAT/INV/SIN/Raster existentes são synthetic-only e não podem receber dados reais sob rótulo sintético.

## Ferramentas e preservação

PowerShell7: `C:/Users/suporte/.codex/tools/powershell-7.6.6-v2/pwsh.exe`. JDK17: `C:/Users/suporte/AppData/Local/Programs/Eclipse Adoptium/jdk-17`. Maven offline e sanitização de ambiente estão no launcher Invoke-Aggregate04.ps1; não usar Java25/global ou rodar Maven concorrente no mesmo target. Comandos/provedores reais já estão encapsulados nos controladores qualificados; não copiar segredo para argumento, chat ou log.

Inventário da árvore no fechamento: `handoff-current-worktree-status.txt` (50paths na fotografia, inclui alterações preexistentes). Não houve commit/push. Preserve arquivos do usuário, checkpoints, FAILs, ledgers e freezes; não faça clean/reset/purge. O problema histórico HANDOFF_PATH de tres-etapas/closed-receipt.json não foi corrigido nem deve ser fabricado. A conclusão das onze entidades continua aberta; este encerramento é apenas a transição de chat solicitada.
