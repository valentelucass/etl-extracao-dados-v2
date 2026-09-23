# Checkpoint 0041 — B60 físico interrompido e compensado

## Identificação e objetivo

- Data: 10/09/2026 UTC; B60, sem novo bloco funcional.
- Anterior: 0040-correcao-gates-globais-fechamento.md;
  SHA-256 dd6075c205f0f4d4561cadc8e447aacc847fa26b6985f4d4b00258b5f289d62a.
- Objetivo: executar o pacote físico aprovado, conferir recuperação, consolidar
  a evidência e marcar somente os critérios efetivamente comprovados.
- Estado: TESTADO_NA_CAMADA / PHYSICAL_CAMPAIGN_STOPPED_COMPENSATED.
- Critérios: target/bloco60-local/PROMPT-ADOTADO.txt, seções 4 e 11;
  database/proposals/bloco60-local/README.md e package.json.

## Autorização e limites

- Mensagem efetiva do usuário: “Aprovo a execução do pacote físico B60 de SHA-256”
  a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e.
  Registro integral: target/execucao-b60-aprovada-20260909-2334/APPROVAL.txt.
- Alvo: localhost/ETL_SISTEMA_V2_SHADOW; suporte elevado como administrador;
  executor etl_v2_exec e observador etl_v2_view já existentes em RTR-SVW-002.
- Validade original até 16/09/2026 00:00 UTC; um OPEN/60 minutos,
  80 JVMs/240 sqlcmd/400 HTTP sintéticos, sem renovação.
- Ledger: target/bloco60-local/physical/a3d28adeb17775bc/ledger.jsonl.
  19 reservas = 17 sqlcmd + uma JVM + instalação; zero escrow e UNKNOWN.
  CLOSE/NOT_QUALIFIED encerrou a campanha. Saldo ordinário não é reabertura.
- Nenhuma aprovação pendente para a compensação já executada. Nova revisão
  física/campanha não está aprovada por inferência.

## Alterações e decisões

- Inventário inicial e cópias dos 1.744 arquivos:
  target/execucao-b60-aprovada-20260909-2334/initial-inventory.json e before/.
- Mudanças: prefixos de estado/trilha, RETOMADA, sucessão exata no validator
  da manutenção e novos catálogo/manifesto/validator/snapshots deste fechamento.
- Pacote aprovado, JARs, migrations V001–V024 e evidências anteriores preservados.
- Não reabrir a campanha nem executar os 73 casos restantes após CLOSE.
- Diagnóstico offline: entrada RuntimeUsersPhysicalProbe$Fault.class incompatível
  com a allowlist do AdministeredArtifactVerifier. UNCONFIGURED físico não expõe
  a causa interna exata; não alegar que todos os outros gates do artefato passaram.

## Execução e evidência

| Passo | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Controlador aprovado | Windows/SQL/JAR | Token elevado conferido, alvo correto, sem alterar controlador | elevated-start.json, launch.json, wrapper-result.json na consolidação |
| Preflight | SQL real | V023/9.137 linhas/perfil/colisões PASS | PREFLIGHT e COLLISION_PREFLIGHT.sql.log |
| Duas qualificações | SQL rollback | Catálogos iguais; preservação após cada rollback | QUALIFY_UPGRADE/QUALIFY_BASELINE_SUFFIX e PRESERVATION_AFTER_* |
| V024/ativação | SQL commit | Instalação/preservação/perfil ativo PASS | INSTALL_V024, PRESERVATION_AFTER_INSTALL, PROFILE_ACTIVE |
| USUARIOS_RUN | JAR restrito | Exit 20/UNCONFIGURED, zero HTTP; esperado sucesso | USUARIOS_RUN.java.log e readback.json |
| Parada/compensação | SQL real | SERVICE v19, replay/force=0, scopes/policies/grants temporários revogados | RESTORE_B60 e PROFILE_RESTORED.sql.log |
| Consolidação | Offline | Cadeia de 41 eventos íntegra, zero UNKNOWN/processos/listener | physical-verification.json na consolidação |

Arquivos físicos em target/bloco60-local/physical/a3d28adeb17775bc/.
O ledger prende hashes das observações; o manifesto da fase prende os arquivos.
Não há efeito de resultado desconhecido. V024 e três commits administrativos
permanecem confirmados; nenhuma publicação de Usuários. Comparação global de
linhas após a matriz completa não foi alcançada. QUALIFICACAO_FISICA_LOCAL_USUARIOS
não comprovada; nenhum checkbox novo. 67/115 = 58,26%; 48 pendentes, 191 rotas,
zero AGORA. Java anterior: 1393/0/0/4, não reexecutado por esta mudança documental.

## Retomada imediata — até três ações

1. Conferir manifesto desta fase e recibo final da consolidação, incluindo
   validadores privados e diff. PASS documental não converte a matriz em PASS.
2. Reproduzir/corrigir offline a incompatibilidade do bundle numa revisão futura,
   preservando o pacote aprovado e o banco já em V024; demais gates externos
   continuam com seus próprios requisitos, sem sondas automáticas.
3. Se houver novo pacote físico necessário, prepará-lo integralmente contra
   SERVICE v19/scopes revogados/campanha fechada e obter aprovação específica
   antes de novos efeitos. Não renovar OPEN/validade/saldo desta campanha.

Conclusão desta unidade: campanha interrompida conforme contrato, compensação
comprovada, documentação consolidada apenas quando final/receipt.json passar.
