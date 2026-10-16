# Checkpoint0502 — Main real: falha do lançador e correção em curso

UTC 2026-10-13T00:54:26.418606+00:00, HERMES_4c5eefc033c7 e complemento funcional do Lucas. Estado EM_EXECUCAO. Anterior0501 SHA256 70046e2562819843f026335a865c3a80b552c300f14c403bcab139fdb7a18ad2 preservado. Critério: RUN da Main principal, observar falha, corrigir causa interna e repetir até startup funcional comprovado ou dependência externa concreta. Não usar mock/DevLocalMain ou log de sucesso como substituto de fluxo real.

## Autorização, ownership e preservação

Pedido completo4c5eef e scratch/etl-main-prioridade-funcional.txt lidos. Supervisor Codex integra documentos/CI; Runtime e Qualificação responde pela Main/lançador/testes bootstrap; Builder ETL revisa fronteira SQL, Fontes e Contratos a fonte, Regras de Negócio o aceite. Quatro despachos reais recebidos; finais ainda pendentes. Não há callback Hermes/Codex, novas sessões ou agentes. Inventário inicial baseline01.json congela arquivos e Git preexistentes no storage privado hermes4c5eefc033c7. ../CONTEXTO_GLOBAL.md permanece ausente; não criado.

JAMAIS alterar versão/instalação SQL Server16.0.1000.6, contas/credenciais/permissões/identidades/ACL, serviço/listener/certificado/trust/global/schema/Flyway/produção/V1/PM2. Executável atual SHA256 73dcd4a89ff178cf6b2978ee29dccd8898fe5c1977abac8208aa4ecb5ec43e32 e versão conferidos por leitura de arquivo em root/sql-pre05.json; nenhuma conexão SQL/TDS. STOP4296908 continua somente no dependente. Esta autorização não libera captura ou escrita nem renova campanha física encerrada.

## Evidência e causa reais

Main principal br.com.esl.etl.v2.bootstrap.Main, comando oficial JAR/CLI no POM e README. Configuração oficial application.example.properties: LOCAL_SHADOW; Data Export/GraphQL/auditoria desligados. Runtime compilou Main e dependências transitivas Java17 -Xlint:all -Werror em target próprio, sem download ou clean. Main run sem request passa por configuração/preflight e deny-all, mas esse ramo ainda NÃO foi observado nesta tentativa.

Primeiro lançamento real java.exe PID75308, exit1/47ms, encerrado/streams fechados: Invalid -Xlog absoluto em Windows; Could not create the Java Virtual Machine. A JVM recusou ANTES da Main, portanto MainActuallyExecuted=true e causa deny-all no handoff parcial são alegações recusadas pelo Supervisor. Originais imutáveis preservados; root/run01-root-review05.json registra a reconciliação. Runtime recebeu correção mínima: arquivo de classload relativo/cwd privado, reutilizar os bytes compilados inalterados e repetir; exigir classload/caminho/saída observados. Sem alteração compartilhada de código até este checkpoint.

run --request exige target Windows/TLS e autoridade administrada antes SQL; C78 local não se torna autoridade/Main por herança. Busca delimitada de configuração/request operacional realmente existente pode avançar validações puras, nunca inventar origem/tenant/contrato nem materializar segredo. Fontes/Banco revisam este caminho; bloqueio final ainda não declarado pela falha recuperável do lançador.

## Retomada imediata

1. Runtime corrigir o lançador próprio, repetir RUN real e entregar PID/classload/config/resultado/fechamento, preservando tentativa01.
2. Integrar revisões Banco/Fontes e corrigir causas internas elegíveis até a dependência externa realmente observada; zero SQL/HTTP/provider/efeitos proibidos.
3. Sincronizar STATES/trilha/retomada e recibo4c5eef somente após resultado funcional ou bloqueio externo concreto, PONG/estados reais; encerrar este escopo e aguardar Lucas.
