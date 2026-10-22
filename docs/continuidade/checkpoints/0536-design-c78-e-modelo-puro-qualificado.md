# Checkpoint 0536 — Direção de desenho C78 e recorte de modelo puro qualificado

Data UTC: 2026-10-17T00:35:44.3050876Z. Pedido USER_20261016_CONTINUAR_V2; instrução atual: “conclua entao, um a um até encerrar”. Escopo: encerrar sequencialmente as frentes locais elegíveis enquanto a ESL aguarda o gestor. Anterior: docs/continuidade/checkpoints/0535-auditoria-complementar-dependencias-empacotadas.md, SHA256 2fd5a5f0d9576572f9ec46d37ed6db681ce821445a66fcaecb5f9a9f996780fc.

Zero autoridade física nova. ESL em standby: nenhuma INFO/probe/captura/reserva/quota renovada pelo tempo. C78 operacional rollback-only; E3 não é herdado. MainHTTP anterior UNKNOWN, MISSING_NOT_RECONSTRUCTED, V1 parado pelo usuário, SQL Server 16.0.1000.6 e proibições permanentes preservados. Root único docs/CI/autoridade; Banco único SQL/TDS. Jobs pesados somente no executor vigente, serial, 2 GiB por processo/árvore, piso4096MiB/comprometimento85%, prazos finitos. Documentação, desenho, modelos e testes locais não aprovam efeitos SQL/Source, retenção real, paridade integral ou cutover.
## Terceira frente: direção adotada e recorte puro qualificado

Root adotou D-C78-RETENTION-DESIGN-01 em escopo reduzido: **DESENHO_ADOTADO_SEM_PERFIL_OPERACIONAL**. Publicados ADR0067 e o catálogo `docs/catalogos/c78-retention-design/` com DESIGN/README, consumidor Python e vetores. O consumidor é ferramenta autônoma de revisão de design; não foi integrado a runtime/build, API Java, owner físico ou flag. As749 fontes de produção e o POM permaneceram iguais; a coorte Java continua1422 (749main+673test).

Root leu integralmente os artefatos, e Banco/Fonte fizeram revisões estáticas independentes sem blocker. O detalhamento amplo foi reduzido expressamente: geometria/namespace, aritmética abstrata e classificação de observação podem ser qualificados localmente; intenção completa, máquina/gates do owner, inventário/budgets físicos e verifier real continuam abertos. A proposta0533 permanece histórica e íntegra, sem adoção retroativa de suas três entregas amplas.

## Execução real e bytes adotados

Na unidade privada nova `root/retention-model01`, o Python canônico `-I -B -S` executou44 expected completos literais:44 correspondências, zero divergências. Distribuição:10INTENT/19BUDGET/15OBSERVATION. Os expected foram escritos a partir dos requisitos, não produzidos pelo próprio validador; a comparação distingue boolean de número. O resultado máximo aceito é **RECORTE_DE_MODELO_PURO_QUALIFICADO**.

| Artefato | SHA256 |
|---|---|
| Consumidor executado e publicado |dec2eeed63ba6f8f487c7b0237c75f0dc7a56de9fb702434a6995dab446e3a97|
| Vetores executados e publicados |cf7c093fd1eb82ee9940edb4cfb1260e66d9979f2889c21bbb2fad34afce7fca|
| Resultado privado |69a7efcb27c87edc85095d0434592f34ac2c8ee94e68dd537bba013c751e1b8f|

Os dois inputs canônicos são byte a byte os executados. Todos os44 resultados, inclusive positivos, declaram `MODEL_ONLY_NO_EFFECT_AUTHORITY` e `authorizesPhysicalEffects=false`. A unidade não abriu rede, JVM, subprocesso/worker do projeto, provider ou SQL; gravou somente seu resultado privado. O receipt0, sem timeout, mantém o executor serial2GiB e watchdog1min. Não somar44 aos casos Java, ao JaCoCo ou a provas de persistência real.

O custo de `COMMIT_ATTEMPT` é positivo e explícito, sem default: a soma exata de consumido+custo+sufixo é checada antes do débito/registro. O custo da tentativa é distinto de FINALIZE/reserva; falha não recredita, segunda tentativa recusa e a unidade não reabre. Os quatro buckets são créditos abstratos e não concedem SQL/HTTP/conexões/tempo. UNKNOWN, ausência, indisponibilidade, inconsistência e close incerto continuam dimensões distintas; símbolos diferentes de conexão/unidade/verifier não autenticam recursos reais nem reconstroem ack/rollback históricos.

## Entregas que permanecem abertas

- INTENT não contém perfil/operação/ciclo/template/per/janela/fuso/fingerprints/release/caller completos. Um positivo sintético não fecha a intenção operacional.
- O ledger não inventaria comandos/custos de driver/provas por CALL/deadlines ou proteção contra writers. Não há orçamento físico completo nem owner composto aos gates.
- A observação não executa readback, autenticação, comparação real de conjuntos, expectativa perdida, recuperação ou fechamento físico.
- Perfil operacional retido, implementação própria, catálogo/proteção então atuais, reservas de antecedente/captura/confirmação/reconciliação, TTL/legal hold/storage/backup/restore/release/cutover continuam decisões e qualificações separadas.

As ADR0060/0062/0064/0065/0066 não foram alteradas; E3 não é herdado. C78 continua rollback-only. Proposta0533 SHA `c13832c068bc1350b8ad9294cb3a3860b034208055a4a3022805cb96af6e6f0a` e detalhamento SHA `e31a25064f7669f97541cdeba6298aff6fd0462c5dd966435ac824f1e20333fd` permanecem iguais e estão arquivados. Nenhuma alteração em Source/SQLServer16.0.1000.6/instalação/identidades/credenciais/permissões/ACL/elevação/serviços/listeners/TLSglobal/DDL/Flyway/V1/produção.

O AST de código foi atualizado integralmente pela via nativa sem cluster/LLM, com os bytes do consumidor e a coorte Java1422 conferidos. Resultado factual e outputs constam do completion/receipt anexados; HTML/relatório e documentação semântica permanecem históricos. O incremento de continuidade deste checkpoint é posterior ao corte AST.

Bootstrap continua79,6644%/déficit34 e G05 segue pendente, apesar do complemento OSV completo das5libs. ESL continua standby e MainHTTP histórico UNKNOWN. Próxima e última frente desta sequência: publicar o roteiro condicionado de retomada real, sem comandos, reservas ou efeitos físicos.

Conclusão AST0536:44185 nós/115749 arestas; consumidor com23 nós e ast_hash verificável; Java1422 conferido antes/depois; três outputs adotados sob lock/backup. Receipt0, sem timeout e pico abaixo2GiB.

