# 0647 — Diagnóstico do Progressive e contrato histórico de Fretes

27/10/2026. O novo supervisor recebeu a liberação de transferência e executou
somente trabalho local. O diagnóstico preservou a falha interna; a correção
causal passou na nova visão privada. Nenhuma API, conexão SQL, DDL ou processo
produtivo foi acionado. O objetivo agregado de extração, banco e comparação
externa integral continua aberto.

## Entrada e transferência

Base inicial limpa: `d8849952dd1ce3a295d45a97dd5c71c74cc3ba47`, main igual à
referência local de origin/main. Checkpoint0646, regras efetivas, backlog,
RETOMADA, protocolo, ledger e seis pins principais foram conferidos. O recibo
`B/RootSupervisorChatTransfer0646.json` confirma o novo supervisor fixado e os
três auxiliares antigos arquivados. B conserva o caminho definido em0646.
O arquivo global CONTEXTO_GLOBAL.md continua ausente; não foi reconstruído.
Memória e grafo foram consultados como apoio, sem substituir as provas atuais.

## Diagnóstico efetivamente executado

O caller pendente0644 foi preservado. O sucessor final está em
`B/users-p3-progressive-diagnostic-root-executor0647-v1`, manifesto
`F68693FF9BA67B7B2C4A29DCB36249813D62247DF80EFC62D564120F85D356E0`.
O caller acrescentou exigência de revisão pinada antes do efeito e referência
dessa revisão no claim/qualificação. Sua revisão foi feita pelo próprio Root,
somente arquivos, não por revisor humano ou agente independente: prova
`0BD413827EF681768837344DB6FACB1A98A3DC82682C3DB18336404A60B26AF4`.
A revisão independente do componente350BB permanece inalterada.

O componente3E76/worker246D/entradaAB34 executou uma vez sobre a mesma visão
pg42 de1.000 arquivos, sem rematerialização. Controle
`B/pd44-06440001-root-control`: Original0/13.959ms/134.078.464bytes,
filho1 sem timeout, logs completos sem truncamento e11 marcadores válidos.
Qualificação F315C574DA5443F8750B2B25BE80F835B26D52332EC4D4AC96E9AC6AA8DB98A8.
O resultado é exclusivamente DIAGNOSTIC_CAPTURED_ONLY, não13PASS.

A falha reproduzida está em Test-FretesV2011ShadowVertical:
`FRETES_SHADOW_VERTICAL_MISSING: o contrato 6389 versionado divergiu.`
O validador exigia versão2026-08-31/release23ae, mas lia o catálogo corrente,
que já contém versão2026-10-24/releasef78f. A baseline histórica preservada
tem exatamente a versão/release exigida, SHA
`D7910FF0E3D0F78BEE57AAAD5178561DB8BCB0D0D253448531B606BCEF45950C`.
Esse mesmo pin já é exigido pelo Test-FirstWaveContractCatalog canônico.
Não houve OOM nem timeout neste diagnóstico. Os buffers da execução histórica
anterior continuam inexistentes; não foram reconstruídos nem atribuídos a logging.

## Correção e prova causal nova

Único arquivo canônico de código alterado:
`scripts/validation/Test-FretesV2011ShadowVertical.ps1`. A validação histórica
V09 passou a ler explicitamente o descritor histórico e conferir seus bytes.
Nenhum predicado semântico, release antigo, manifesto ou contrato atual foi
relaxado/reescrito. Isso não qualifica o sucessor observado de Fretes por herança.

Nova visão `B/pg47-06470001`:1.001 arquivos/779Java/107migrations propostas.
999 arquivos iguais ao pg42, um validador corrigido e um descritor histórico
adicionado. Proposta/revisão Root somente arquivos em
`B/users-p3-progressive-static-successor0647-v1`; manifesto
`D5030D7B5DBD4C2F327970CF4F003A05B99239FBDCDA8B8D282229E84DC386DC`.
As propostas V106/V107 são texto privado; nada foi aplicado ao banco.

Root executou três casos reais sob Original1GiB/2min:

- Fretes com histórico correto: filho0 e mensagem final esperada.
- Um byte extra no histórico privado: filho1 e recusa pelo hash esperado;
  bytes restaurados no finally e conferidos.
- Progressive completo: filho0, stderr vazio,13 mensagens dos13 scripts
  existentes, incluindo RuntimeDurableRecovery; sem truncamento.

Original0/13.250ms/pico144.011.264bytes. Qualificação
`B/pg47-06470001-root-control/qualification.json`, SHA
`06FEF0402033F2E3445D31DFCC819388E35527EE4D7A23C0ECE03ED0BC8D6D63`.
Resultado SHA7A3CC32FEB7BD101ECCF71864AF3714B71896431A15874725440567E574DF1C3.
Todos os1.001 pins foram conferidos antes/depois. Os testes leram SQL e Java
como texto; não executaram engine, migrations, probes SQL ou testes Java.
As mensagens históricas impressas pelos scripts não criam prova física atual.

As duas unidades tiveram admissão atual4GiB/85%, slot Root único, fechamento
conhecido e liberação. A segunda restaurou os bytes exatos do ledger anterior.
Contadores Source5931/5932 permanecem históricos/não finais; última Source real
continua Main Manifestos0645/315linhas, provas36DF/7460/2AFE.

## Limites e próximas ações

A varredura integral real passou em4.767 candidatos/4.763textos/quatrobinários,
zero achados/oversized/nãoinspecionados: Original0/152.481ms/314.564.608bytes,
`B/git-static-0647-03-root-control/qualification.json`, SHA
`10E4DB84142620DE195F86B37F34E50234C8736EEA37743CF9BE92D40E14633F`.
A tentativa anterior parou no teto110s, sem relatório; foi preservada como falha.
Esta anotação posterior exige scan delta próprio antes da publicação.

O mapa Graphify NÃO foi atualizado: duas tentativas incrementais locais,
semAPI/force e sob1GiB, falharam. A primeira mantinha uma cópia extra do grafo
87MB; o sucessor passou a medir SHA por streaming, mas também não fechou dentro
do limite de memória. Picos1.083.650.048 e1.096.331.264bytes, Original1 nas duas;
o erro Python interno não foi preservado além de uma exceção vazia do rebuild.
Não declarar causa exata de alocação nem PASS do mapa. O grafo anterior foi
preservado; os dois slots foram liberados apenas por auditoria dos recibos,
do Runner pinado (retorno não-timeout exige ActiveProcesses0) e ausência dos
PIDs próprios, com restauração dos bytes históricos do ledger. Provas em
`B/graph-static-0647-01-root-control` e `B/graph-static-0647-02-root-control`,
root-closure-audit.json/root-audited-slot-release.json. O índice auxiliar fica
pendente; as qualificações estáticas e a varredura de código acima são distintas.

1. Implementar/revisar o catálogo read-only estreito dos oito objetos Users/P3,
   com17constraints/57colunas/33parâmetros, owners/permissões/triggers/dependências
   e BEFORE/AFTER protegido, no owner existente. A visão estática não é catálogo.
2. Fechar adapter operacional, guard/proveniência/bounds P3 e engine/recovery
   somente nas camadas autorizadas. Guard0/registryEMPTY não viram permissão;
   qualquer operação física exige escopo, qualificação e reserva próprios.
3. Avançar bancos e comparação por registro/campo no mesmo recorte. As18SQL dos
   quatro grupos e Raster7 separados continuam sem exceção específica; carga
   permanente, EOF Users e paridade Excel/ESL integral permanecem abertos.

Preservar SQL16.0.1000.6, contas/credenciais/identidades/permissões, serviços,
listeners/TLSglobal, PM2/V1/WMS/produção. ESL somente consulta/filtro/download,
sessão expirada/2FA sem nova evidência. Futuras Source exigem LatestMainTyped e
escopo próprio, sem reutilizar CODE/janela/unidade concluída. Root permanece
executor único. Publicação exige varredura integral por haver alteração de código,
verificação do diff/UTF-8 e commit E push; recibo próprio registra o resultado.
