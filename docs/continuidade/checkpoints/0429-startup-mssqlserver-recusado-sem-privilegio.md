# Checkpoint0429 — startup MSSQLSERVER recusado, sem efeito

## Identificação e objetivo

03/10/2026; Hermesbd127527b2a0 autoriza no máximo um início do serviço existente e exige proibição permanente de alterar versão. Anterior:0428-metadata03-preflight-servico-parado-e-helper-qualificado.md SHA C0E34535FB6C355E2C7E06F8EC6CAEFFD3333C65CB76A4E4438EEE540C6F1CEB. Estado:BLOQUEADO_POR_INPUT no início, unidade encerrada; nenhuma extração.

## Autorização e limites

Banco único executor; Codex editor compartilhados. Até1START,0stop/restart/configuração/SQL/HTTP/segredo/instalador/versionchange. Máximo2fotografias pós-start/60s e readback único se incerto; não usados, pois preflight recusado. Impacto previsto: toda instância instalada local. Recuperação: preservar evidência e ler estado se incerto; sem reversão automática. Nenhuma reserva criada nem efeito incerto. Restrição canônica registrada antes do preflight e vinculada aos hashes em banco/preflight.json; não inferir build histórico.

## Alterações e decisões

AGENTS seção1 recebe proibição permanente; STATES depois trilha/painel/RETOMADA registram resultado. Material preexistente e fotografias preservados em arquivos before e initial-git-status na unidade. Helpers privados Banco observam serviço/arquivo e acesso SCM sem chamar StartService. Não mudar produto/código/schema/segredo/listeners. Treze arquivos qualificados0426 continuam sem drift.

## Execução e evidência

Hermesbd127527b2a0: Banco observou MSSQLSERVER Stopped/PID0 e recusou o preflight por SERVICE_START_ACCESS_DENIED (Win32 5), com oito java.exe como consumidores potenciais não classificados, sem alegar sessões SQL comprovadas. Zero chamadas START/reserva/retry/elevação, conexões/comandos SQL/HTTP/segredo/reconfiguração. Versão real do executável configurado: produto 16.0.1000.6, arquivo 2022.0160.1000.06 ((SQL22_RTM).221008-0913), SHA256 73DCD4A89FF178CF6B2978EE29DCCD8898FE5C1977ABAC8208AA4ECB5EC43E32. Readback único de fechamento em 0.522s confirmou Stopped/PID0 e igualdade de caminho/versões/hash entre preflight e fechamento. Nenhum início ou PID vivo: vínculo de binário em processo/pós-start não aferido e não alegado. Proibição permanente de alterar a versão registrada em AGENTS seção1 e STATES antes do preflight, com hashes vinculados; histórico preservado.

Referências:target/startup-mssqlserver-20261003-bd127527b2a0/supervisor-authority.json,supervisor-before-review.json,banco/preflight.json e manifest/handoff fechados,supervisor-final-review.json. Validação documental/diff/UTF8 registrada no recibo Supervisor. Nenhum Maven/Graphify/consulta física nova. Sem novo aceite;67/115/48abertos e39/45 mantidos. Banco encerra o turno; Codex encerra após recibo ponte, demais builders não acionados.

## Retomada imediata — até três ações

1. Operador fornece contexto autorizado com SERVICE_START e classificação dos oito processos Java potenciais, sem elevação por workaround; serviço ainda parado conforme preflight.
2. Usuário decide unidade posterior; não reutilizar automaticamente a tentativa encerrada nem renovar orçamentos.
3. Antes de qualquer SAMPLE posterior, comprovar liberaçãoSTOP429 e qualificar opt-inSA/loopback/TLS/contrato/trial reservado.

Controlador waiting_user; esta unidade não autoriza execução automática. Nenhum teste offline prova conexão ou extração real. CONTEXTO_GLOBAL ausente não recriado.
