# Checkpoint0477 — integração do runtime compartilhado em execução

Autorização direta de Lucas em08/10/2026: ler `.runtime` na raiz de projetos e começar a aplicar as etapas restantes de ESL→banco local. É uma unidade nova; Hermes a04/b77 e ledgers encerrados permanecem históricos, sem renovação de orçamento.

## Unidade e responsabilidade

Unidade externa: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/execucao-esl-shadow-20261008-164952/`. Código permanece no repositório. Root é único editor de documentos/CI/estado; Banco integra o provedor e prepara catálogo/SQL; Fontes verifica origem/limites; Runtime integra launcher/caller e qualifica. Foram usados os três builders existentes após leitura dos estados ociosos, sem novo terminal ou subagent. Regras não tem nova tarefa.

## Evidência de leitura, sem autenticação

`../AGENTS.md` e `.runtime/{README.md,SQL-SERVER-EXISTENTE.md,sql-server/README.md,config.json,SqlServerProjetos.psm1}` lidos. SQL comum: local1433, Standard16.0.1000.6, executável configurado SHA256 `73DCD4A89FF178CF6B2978EE29DCCD8898FE5C1977ABAC8208AA4ECB5EC43E32`. O recibo SQL protegido existente documenta E3, atualização06/10/2026, certificado/truststore existentes e mesmo hash DER já aprovado no V2; o arquivo administrativo DPAPI existe. Isso não comprova a identidade atual do processo nem autenticação atual.

O recibo `.runtime/pm2/tls-current.json` BF4 é distinto; não substitui E3 por inferência. `Get-ProjetosSqlProfile` pode coletar TLS/recriar arquivos/alterar ACL em mudança de processo; não foi executado. Setter de credencial, teste BAT/PS1, módulo de conexão, SQL, HTTP, DPAPI real e mutações PM2/servidor não executados por Root.

## Critério concreto da unidade

Integrar/qualificar a seleção do provedor compartilhado existente mantendo driver/pin/escopo V2; obter preflight SQL local somente leitura e preparar catálogo próprio com persistência dos pares das33expressões/contrato estrito. Uma prova física, se elegível após qualificação/admissão, terá reserva e despacho novos:1conexão/até12SELECT/10s/deadline60s/sem retry/HTTP/escrita. Captura Coletas limitada e confirmação independente pós-commit são unidades separadas, após catálogo/proteção writers/origem/janela atuais. Não alterar versão, contas, permissões, serviço/listeners/certificados/trust global/schema/V1/produção. Não presumir liberação429 ou captura por sucesso DEV.

## Próximas três ações

1. Consumir os handoffs causais Banco/Fontes/Runtime e verificar pins/resultados atuais.
2. Corrigir/admitir a integração mínima e, somente com requisitos físicos atendidos, emitir a primeira unidade catalogal própria pelo Banco.
3. Prosseguir à captura pequena e confirmação pós-commit se os gates efetivos passarem; caso contrário registrar a causa e o input concretos depois de concluir o trabalho independente elegível.

Estado: implementação/preparação em execução, nenhum aceite físico ou extração retida novo.0475/0476 mantêm fechamento DEV PASS, sem alteração de seus artefatos.

## Avanço Root observado

`scripts/runtime/Test-SharedSqlRuntimeReference.ps1` aplicado: referência protegida real conferida sem decifrar credencial, executar o módulo ou abrir conexão. Prova `root/reference-result01.json`: referência atual+8fixtures/9processos nativos PASS, físicas0. Recusa de versão, endpoint remoto, pin, caminho escapado, bytes TLS alterados, credencial ausente e tamanho de arquivo; controle positivo explícito. Primeiro harness recusou antes de executar por casing da chave pública SYSTEMROOT; versão anterior e recibo preservados.

Banco `os-result01.json` recusou identidade do processo antes de TCP/SQL:2CIM,0TCP/SQL/TLS/DPAPI,679ms, sem retry. Root realizou uma unidade independente de leitura nativa com direitos limitados: primeira razão genérica permaneceu desconhecida; contraprova demonstrou perda de causa em MethodInvocationException. Classificador limitado a8níveis/4casos PASS; sucessor novo com teto próprio retornou `SQL_NATIVE_PROCESS_ERROR_5` em491ms. OpenProcess limited-query recusado no token atual; handles fechados,0SQL/HTTP/CIM/credencial/mutação de permissões. Não reconstituir a primeira ocorrência nem inferir falha TLS/senha/serviço. Provas e reservas em root/native-image-*.json; nenhum próximo read físico idêntico automático.

Provedor06 Banco já referencia o módulo comum e recusa arquivos ausentes antes de importar; manter bytes qualificados. Runtime prepara launcher próprio catálogo/captura/confirmação, sem execução. [Runbook concreto](../../runbooks/runtime-compartilhado-esl-shadow.md). Acesso de leitura da imagem atual ou evidência operacional atual validável de mesmo processo ainda precisa ser resolvido para o guard original, sem alteração de contas/permissões ou elevação improvisada. Fonte/writers/catálogo físicos seguem não comprovados.

## Handoffs02 e revisão da execução

Fontes: preparação11rotas/192readbacks; sem sucesso/quota recente no MESMO escopo6908. Inputs externos exatos em fontes/external-inputs01.json; pergunta de referência pendente, nenhum HTTP. Root/source-pins-result01.json conserva recusa do índice244 por3drifts; source-scoped-result02.json confere241componentes, excluindo somente2metadados PM2 mutáveis e módulo Runtime em edição. Não admitir os3contextos nem alterar o índice histórico.

Runtime/source-binding02/handoff02.json:15classes públicas do overlay histórico divergem do JAR atual. Contraprova de own-close falha no legado e3casos atuais PASS; guard11PASS;10processos nativos fechados. Sucessor rejeita captura/confirmação históricas antes de lançamento, nenhum byte antigo apagado. Instrumentação privada atual e confirmação pós-commit independente continuam exigidas.

Banco/readonly02/handoff02.json:19Java+26PowerShell+12admissão=57casos offline PASS, sem repetir agregado. Novo observer/launcher read-only instalado+sessão própria; LoadedImagePath/Bytes=NOT_PROVED, nenhuma proteção de writers/captura/P08/produção. Guard original preservado. Root/handoff-readback02.json:148artefatos Banco,23membros freeze e101artefatos Runtime conferidos,0drift; contagens por índice, não total deduplicado.

Defeito local da API02: Root deve vincular reserva Banco ainda inexistente e três owners devem usar o mesmo issued/deadline60s; entrypoint físico não está implementado. Review root/readonly-protocol-review03.md e tasks03 tratam causa no mesmo macrobloco, sem declarar SQL bloqueado por erro5. Banco prepara protocolo/reserva/executor03; Runtime composição privada e revisão independente; Root emissor own03. Autorização Root real somente depois de freeze final/review/admissão, uso único dentro de15min; reserva Banco nova antes de OS/TLS/DPAPI/SQL, prazo min(reserva+60s,expiração Root), claim durável contra replay. Nenhuma renovação/retry/orçamento histórico.16checks Root do emissor e1interop UTC nativa PASS; nenhum dispatch real emitido.

Próximas três ações atuais:1) integrar/revisar o executor03 e freeze final;2) com admissão exata, emitir uma unidade read-only e Banco executar/reconciliar no limite próprio;3) somente com origem/writers/catálogo comprovados, preparar captura/confirmação separadas. Fonte429 aguarda referência externa concreta já solicitada; não descobrir liberação por sonda. Status: preparo causal03 em execução, SQL/HTTP/DPAPI/captura novos0.

## Conferência sucessora Root/Runtime03

Root/readonly03/qualification05.json:19cases de validação do contrato03/caps/roles/pin/version/nonclaims PASS. file-qualification09.json:8processos CLI nativos fechados usando fixtures isoladas, controle .cs público, drift/duplicatas/renomeação/recusa de -Issue em fixture; nenhuma autoridade real escrita. claim Root durável por unidade impede segunda emissão automática. Históricos qualification03/04 e file06/08 preservados, não agregados como novos.

Check-RootUtcWire03: interop .NET UTC7digits PASS. interop-result11.json:3cases API Root Python→validador Banco atual extraído PASS, sem importar módulo/getter/executor. frame-binding-result10.json: função pura de frame Banco atual produziu344bytes e controle de3argumentos PASS; nenhuma recusa Banco por esse teste nem qualificação de lançamento completo inferida. Root/bank-draft-review07.md conserva revisão de causa sanitizada e closure a verificar no executor final, não declara correção futura.

Runtime/readonly03/handoff03.json:20cases próprios PASS,57Banco reutilizados sem repetição,13processos nativos fechados. Origem real de47classes privadas e9TLS públicas, sem public shadow. Falha Stream.Write Runtime antes de Java preservada; sucessor com3argumentos PASS. Root/runtime-handoff03-readback11.json confere42artefatos atuais/0drift. API mantém NEEDS_FINAL_BANK_FREEZE; nova admissão final será de bytes Banco03 realmente revisados. Driver efetivo configureDriver é login5/query10/socket10000ms/cancel5/retry0: não alegar aplicação dos overrides socket5000/cancel1 do URL temporário ignorado pelo boundConnector.

Scanner repo depois da sincronização daquele ponto:4389candidatos/4386textos/3binários/0findings, native0. scope-scan07:6textos Root iguais aos pins daquele snapshot/0findings, native0; primeira falha antes de candidatos por scanner exigir Git preservada, fixture isolada sem remote/commit criada, nenhuma mudança no scanner. Alterações posteriores de política .cs/fixture-issue ainda precisam do scan final dos bytes correntes; não promover scan histórico a final. Preparação validator33/48/9 e UTF8 seisdocs/code PASS naquele snapshot; documentação corrente será validada no fechamento.

## Handoff Banco03 e revisão causal04

Banco/readonly03/handoff03.json entregue OCIOSO:46cases novos (38executor+3transport+1JavaGate+4providerWriter), compilação Java/C# estrita e formatter, freeze07 SHA256 `586d94685075e74044ad220b945e9b831e40aebf7b7fe9c04a9d25a78eef2662`. PRE/provedor/Java/POST completos preparados, nenhuma unidade física executada. Root review07 confirmou que o recibo externo perdia status/failure/pinReason/tlsReason do recibo SQL seguro ao substituir tudo por STOP_SQL. Task Banco04 aplica projeção tipada/allowlist e casos dirigidos, preserva versões/freezes/falhas e classificação UNKNOWN sem prova de cleanup. Runtime04 revisa fechamento/buffers/causa independentemente, snapshot próprio, sem admissão futura ou edição concorrente.

Root/source-scoped-result12.json:240componentes exatos,4contextos separados não admitidos; STATES agora faz parte da sincronização documental Root. Os resultados anteriores241/3 são históricos, índice244 integral não foi reescrito/admitido. Root/scoped-scan13/input-pins13.json:10arquivos públicos Root correntes copiados por mesmos bytes a fixture diagnóstica Git sem remote/commit; scan10textos/0findings/native0. Nenhuma credencial/fixture/binário lido por esse scan. Origem429 e proteção writers continuam sem prova; Root autorização real/catálogo SQL/captura ainda0. Tasks Banco04/Runtime04 em execução; próximos responsáveis e limites permanecem os mesmos.

## Conferência final das entregas04

Evento: pedido direto de status08/10, “todos parados, como estamos finalmente”. Leitura dos handoffs concluídos e conferência readonly `maestri check` dos quatro builders: todos OCIOSOS, sem novo despacho, callback ou efeito externo. Root mantém editor único de STATES/trilha/continuidade.

Runtime/readonly04/handoff04.json,19:11:56UTC: revisão independente do freeze07,9casos causais/9processos próprios fechados, sem drift. Recusa material por perda da causa sanitizada, fechamento nativo não observado na falha e buffers tardios não limpos. Esse resultado continua preservado e não admite bytes futuros.

Banco/readonly03/handoff04.json,19:30:16UTC: sucessores04 tratam essas três causas,38novos checks PASS (34causa+4fechamento), compilação estrita/formatter/sintaxe PASS. Causas tipadas/allowlist e desconhecidos preservados; fechamento próprio limitado e limpeza de buffers não transferidos. Não inferir frequência natural da corrida a partir dos schedules controlados. Freeze07 e provas46/57/FAILs mantidos; nenhuma suíte histórica/Maven repetida. Qualificação34causa usa freeze09; literal de referência09→10 é delta posterior declarado, quatro casos de fechamento usam freeze10. Admissão independente do freeze10 ainda false.

Root conferiu47membros do freeze10 por tamanho/hash atuais,0drift, SHA256 `ab0bd2e42b295e5a74da7100330f626aac0ef41f3f71d4b8d95fc4ff1de32e63`. Composição em banco/readonly03/composition-final04.json; causas/fechamento em safe-cause-results04-02.json e closure-results04-03.json. Readback de integridade não substitui revisão operacional ou admissão Runtime. Nenhuma autoridade Root real/dispatch/reserva física nova/SQL/HTTP/DPAPI/captura/gravação nesta integração. LoadedImagePath/Bytes e proteção writers continuam NOT_PROVED, captura/P08=false.

Próximas três unidades:1) revisão final Root e admissão Runtime dos bytes atuais freeze10;2) Banco executa uma prova catalogal read-only em unidade nova delimitada, se elegível, com reserva própria;3) captura pequena e confirmação independente apenas após catálogo, guard de identidade/proteção writers e origem/quota atuais. Fontes não encontrou sucesso/liberação recente6908 no mesmo escopo; referência externa já solicitada, sem sonda de descoberta. DEV LOCAL0475/0476 concluído permanece separado. Ainda não liberado para extrair/gravar ESL; todas as descrições anteriores de tasks04 em execução são históricas.

## Entrega da unidade própria Root

Root/root-handoff14.json registra preparo material concluído e testes/referências; overallIntegrationComplete=false, sem emissão Root real/SQL/HTTP/DPAPI/captura. Documento/code snapshot14:6arquivos UTF8/diff/scan6/0/native0 e validator33/48/9/authorized=false PASS. Banco04 e Runtime04 permanecem em trabalho concreto; Root não fica WORKING só aguardando nem cria loop/callback/retomada automática. Próximo evento elegível é a entrega04 realmente concluída, seguida de revisão dos bytes/causa/closure/admissão exatos; quota6908 requer o registro externo já solicitado. Não repetir3036+7/DEV por falta desse input ou atribuir READY a preparo.
