# Checkpoint0503 — Main real entrou; diagnóstico e configuração reais em qualificação

UTC 2026-10-13T01:08:49.135142+00:00, mesmo HERMES_4c5eefc033c7/complemento funcional, EM_EXECUCAO. Anterior0502 SHA256 c14ce1746a82156a6101130248cdb29093961b757ec75f652a6453687adee605 preservado. Objetivo: ciclo RUN real/correção/repetição até inicialização funcional ou dependência externa concreta. Não substituir por mock, DevLocalMain ou log de sucesso.

## Avanço observado

O lançador próprio foi corrigido para -Xlog relativo. RUN02 executou Main pública PID67016, exit20/1140ms, sem timeout; saída real deny-all. Root leu log completo e configuração idêntica ao exemplo oficial com origem/GraphQL/audit=false: classes Main/configFactory/Root/preflight/boundary atuais e ramo de autorização atingido. Matcher do handoff02 procurava Factory no pacote bootstrap; foi refutado por readback, pois ela está em plataforma.configuracao. Sem repetir JVM por esse erro do controller. RUN01 continua antes da Main/exit1; originais e alegações rejeitadas preservados.

RUN03 executou Main pública PID10576, exit2/437ms, com o request técnico histórico real inalterado e a mesma configuração desligada. Mensagem genérica de configuração/pedido inválido; guarda pura de alvo precede leitura do request/authority/provider/SQL/HTTP. Runtime revelou defeito de diagnóstico, implementou allowlist dos dois códigos estáticos da guarda no catch de Main e teste de processo real novo. Não mudou exit2/gates. Contraprova RED de diagnóstico e falha de qualificação por assert indevido de classload preservadas; classloading não implica leitura de request ou chamada SQL. GREEN final e RUN04 ainda em qualificação neste checkpoint. Main.java e MainOperationalStartupDiagnosticProcessTest.java têm ownership Runtime; Root não edita esses arquivos.

Fontes01 e Banco01 entregaram revisões reais. Os dois pares históricos SAMPLE possuem requests19campos, source/tenant/policy, mas URLs dependiam de overrides e SAMPLE-SA não corresponde ao caminho Windows da Main. Fontes02 recuperou somente a URL não secreta da configuração provisionada existente, sem tokens/senhas/provider/HTTP, sem editar .env/V1/originais; arquivo privado56bytes SHA256 99bb6a8cf10761f7b95b7e25d5636015f23008933eca76cfcb234be8392b9586, valor nunca reproduzido em log/documentação/recibo. Root enviou o evento12 ao Runtime: compor metadata dev real e request/plano puros, com target Windows strict local e sem herdar C78; executar até o gate real do artefato administrado. Banco confirmou que CodeSource local de verifier qualificado recusa a localização antes recurso/ACL/config da autoridade e SQL. Nenhum artefato ProgramData pode ser instalado/renovado/alterado por esta prova.

## Camada e limites

Main/CLI/preflight reais comprovados; startup do fluxo ETL, tabelas populadas e dependência externa final ainda não aceitos. Ausência de request/config desligada são escolhas internas a resolver, não bloqueio externo genérico. Bootstrap de origem/persistência permanece após autorização; source enabled em metadata não libera consumo/quota, target declarado não prova isolamento/TLS/writers. Históricos não fornecem novos permits/reservas físicas.

Processos RUN01..03 encerrados e leitores/streams fechados, saídas completas abaixo64KiB. Zero SQL/TDS/HTTP/provider/captura/escrita de domínio/DDL/Flyway/grants/deploy/ACL/serviço/listener/trust/PM2/V1/produção. JAMAIS alterar versão/instalação SQL16.0.1000.6; leitura atual do executável/hash em root/sql-pre05 e banco01/installed-executable01. Nenhum checkbox de implementação produtiva promovido. Baseline01 e histórias/recibos anteriores preservados; root/run02-readback10 e reviewer-pins10 comprovam50pins com snapshots documentais históricos reconhecidos. Falha09 do controller (ACK estruturado versus booleano) foi corrigida só em sucessor10, sem efeito produto.

## Retomada imediata

1. Runtime terminar qualificação da correção/novo teste e executar RUN04 real; depois metadata legítima recebida permitir avançar Main até autoridade sem SQL.
2. Root integrar resultado causal final, revisão MAIN01..08 de Regras, delta qualificado e AST só dos arquivos alterados; distinguir inspeção/CLI de fluxo integrado.
3. Sincronizar STATE/trilha/retomada, checks aplicáveis e recibo4c5eef/PONG/ociosidade após resultado final; encerrar este bloco e aguardar Lucas, sem callback Hermes.
