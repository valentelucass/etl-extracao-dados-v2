# V2-035c — Frota de Manifestos: decisão local em Sol Ultra

## Finalidade e limite

Este runbook executa apenas a decisão que prepara as dimensões em sombra de Veículos e Motoristas a partir da vertical Manifestos 6399 já implementada em V04. É o Bloco 38, rota `D00`, em `SOL_ULTRA`.

Ele não implementa dimensão, não cria Java, migration, schema, tabela, view, procedure, grant, runtime, relação, fato ou contrato `pub`. Não abre rede, API, `curl`, `.env`, credencial, payload real, banco externo, deploy, commit ou push. Não executa SQL Server: este bloco é documental, estático e sintético.

## Prompt para iniciar o chat Sol

```text
Leia integralmente AGENTS.md, STATES.md, docs/runbooks/trilha-de-chats-gpt-5-6.md,
docs/runbooks/frota-manifestos-v2-035c-sol.md e ../CONTEXTO_GLOBAL.md.

Execute exatamente esta linha da trilha:

STATUS=AGORA | ROTA=D00 | BLOCO=38 | TAREFA=V2-035c | FATIA=DECISAO | ESCOPO=Frota de Manifestos - Veículos e Motoristas | MODELO=SOL_ULTRA | DEPENDE=V2-026 | RUNBOOK=[frota-manifestos-v2-035c-sol.md](frota-manifestos-v2-035c-sol.md) | SAIDA=DECISAO_FAIL_CLOSED_E_PROMPT_TERRA_SOMENTE_SE_VEICULOS_EXECUTION_READY

Faça somente a decisão local e sintética descrita no runbook. No encerramento, atualize
primeiro STATES.md, depois a trilha e o validator. Crie o prompt/runbook de Terra para D03
somente se Veículos terminar como EXECUTION_READY; caso contrário, registre o bloqueio e não
inicie implementação.
```

## Insumos que podem ser usados

Somente artefatos locais versionados do V2 e a caracterização estática já permitida do legado. A decisão V03/V04 preserva, entre outros, placa, nome/proprietário/capacidade do veículo, placas de reboques, nome do motorista e tipo de contrato. Esses sinais não são, por si, prova de identidade, filial, vigência ou vínculo entre entidades.

Não converter uma ausência de ID estável em fallback por nome, não remover ou normalizar silenciosamente texto bruto, não assumir que reboque é veículo principal e não usar uma relação observada em Manifesto como relação canônica Veículo→Motorista.

## Decisões obrigatórias

Produzir, para cada dimensão, uma decisão explícita sobre:

1. source key, canonical key e business key; grão, escopo de ambiente/fonte/tenant e unicidade;
2. presença `ABSENT`/`NULL`/`VALUE`, normalização permitida, validação de placa e tratamento de texto Unicode;
3. frescor, vigência/current-history, rekey, replay, conflito no mesmo frescor, ausência e quarentena;
4. veículo principal versus cada posição de reboque, e se algum deles fica fora do escopo;
5. motoristas homônimos/genéricos, tipo de contrato e impossibilidade de usar somente nome como identidade;
6. filial: fonte exata e regra aprovada, ou ausência de vínculo. Filial não pode ser inferida;
7. inexistência de join, FK ou dimensão relacional Veículo→Motorista neste escopo, salvo prova local explícita que satisfaça todos os itens anteriores.

## Entregas e aceite

Criar um catálogo em `docs/catalogos/frota-manifestos-v2-035c/`, com README, decisão versionada, matriz de evidência e fixtures estritamente sintéticas. Criar ou atualizar um validator determinístico que prove os vocabulários fechados, as decisões, os contraexemplos e a ausência de dados sensíveis. Criar ADR quando a escolha modificar chave, grão, vigência ou regra de quarentena da arquitetura-alvo.

Cada dimensão recebe exatamente um estado:

- `EXECUTION_READY`: chave, grão e regras estão congelados por evidência local suficiente para uma implementação Terra delimitada;
- `PARTIAL_DECISION_ONLY`: parte do desenho ficou definida, mas não há autorização para implementação;
- `BLOCKED`: falta prova e a dimensão deve permanecer sem promoção.

O Bloco 38 só pode ser marcado concluído se os dois resultados, suas limitações e seus testes estiverem registrados. Isso não marca V2-035b nem qualquer dimensão como implementada.

## Handoff obrigatório para Terra

Antes de encerrar, atualizar primeiro `STATES.md`, depois a trilha e seu validator. Somente se **Veículos** receber `EXECUTION_READY`, criar `docs/runbooks/frota-manifestos-v2-035b-execucao-terra.md` com uma mensagem copiável para o próximo chat Terra XHigh.

O prompt Terra deve conter, sem campos em aberto: rota `D03`, identidade/grão já aceitos, campos permitidos e proibidos, tratamento de ausência/conflito/quarentena, limites Unicode, relações explicitamente proibidas, artefatos Java/SQL esperados, testes/rollback e os limites de autoridade. Ele deve dizer que Motoristas é somente candidato, ainda que também esteja `EXECUTION_READY`; D04 não entra no chat D03.

Se Veículos não receber `EXECUTION_READY`, não criar um prompt de implementação fictício. Registrar um handoff bloqueado com a evidência faltante e manter zero promoção indevida para Terra.

## Verificação do bloco

Executar os validators novos e os já aplicáveis ao catálogo 6399/V03, `Test-Gpt56ChatTrail.ps1`, o scanner offline e `git diff --check`. Revisar integralmente o diff e registrar somente comandos realmente executados.
