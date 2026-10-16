# ADR0064 — Main DEV C78 em workspace próprio

- Data: 13/10/2026.
- Estado: mecanismo adotado sob HERMES_8fdb85e78657; implementação e provas físicas em curso. Não constitui aceite integrado, extração ou paridade.
- Responsável: Codex coordena e integra; Runtime implementa composição/CLI/pacote; Banco implementa persistência e executa qualquer SQL/TDS. Regras revisou o mecanismo em `regras02/handoff01.json`, sem alegar revisão humana.

## Problema e autoridade

O Main operacional antigo exige instalações históricas em três diretórios ProgramData que foram observados ausentes no pedido4c5. Renovar manifests Windows antigos, criar pastas ou markers vazios e declarar instalação administrada não resolveria a ausência real. Lucas autorizou composição e implantação DEV isoladas nos próprios artefatos, e continuidade dos blocos internos seguros até funcionamento integrado/extração real/paridade ou obstáculo externo incontornável. Essa autorização substitui a espera por nova decisão entre esses blocos; não libera gates físicos ou de fonte.

Preservar o contrato antigo Windows/ProgramData e criar rota explícita na Main pública: `run --dev-bundle <manifest> --profile C78_LOCAL_SHADOW_SQL_PIN_V1`. A rota usa o JAR real, suas dependências/recursos, configuração e request congelados no workspace próprio. Não alegar isolamento administrativo, PKI global ou prontidão a partir de hashes. O opt-in DEV e os dois opt-ins C78 permanecem desabilitados por padrão.

## MAIN-DEV-C78-001 — composição verdadeira antes do plano

Carregar configuração-base da fonte com armazenamento desabilitado e sem subordinados shadow dormentes; compor uma `RuntimeConfiguration` com metadados C78 explícitos e o alvo literal antes de preparar request/fingerprint. A fábrica comum de armazenamento exige autenticação integrada e permanece assim. Criar fábrica C78 separada; não fingir metadados Windows para autenticação SQL nem herdar flags SAMPLE/BF/E3. Somente seletores bootstrap já validados podem ser retirados da cópia de Properties entregue ao loader; não modificar propriedades globais ou sua política de segredos.

Fonte: HERMES_8fdb85e78657, correção Root03 e revisão Regras02. Exemplo: um C78 com `integratedSecurity=false` não passa pela fábrica Windows. Testes: alvo/perfil/flags/metadata, recusas de configuração, leases/hashes e Main em processo real. Runtime é o responsável. Contratos afetados: apenas a nova composição DEV e metadados C78 separados.

## MAIN-DEV-EFFECT-001 — pacote não concede efeito

Preparação sem I/O externo deve terminar com recibo honesto de admissão física pendente. A etapa subsequente usa despacho concreto do Supervisor vinculado ao prepared invocation/plano/ocorrência, bytes/configuração/ação/alvo e reserva nova. Banco executa o próprio Main com adaptador JDBC real e provedor externo existente; Runtime não executa SQL. Nenhum sucesso automático em porta abstrata, marker ou manifest escrito pelo pacote autoriza conexão.

A primeira unidade catalogal é uma conexão, até12 SELECTs estáticos, timeout por query10s e deadline completo60s, sem retry. Ela não executa negócio, fonte, DDL, grants ou commit. Relatório completo privado e limitado recebe readback/hash; console publica só resumo. Snapshot de locks não é proteção contra corrida de writers para captura futura.

Fonte: despacho Root no escopo8f e critérios AUTH01–06 Regras02. Exemplo: mudar JAR, config ou ação exige sucessor causal e admissão própria, preservando a ocorrência anterior. Testes: consumo exclusivo de reserva, alvo/limites/recusas/cleanup, relatório completo e prova Main→catálogo real. Banco/Runtime são responsáveis. Contratos afetados: a nova rota DEV catalogal; nenhum orçamento histórico é renovado.

## MAIN-DEV-NATIVE-001 — identidade atual antes de autenticar

Preservar o pin C78 imutável, validade/algoritmos, TLS e peer loopback verificados no socket real antes de LOGIN7. Associar esse mesmo socket aberto ao reverse tuple nativo, serviço/processo/início bruto/imagem carregada e arquivo atual16.0.1000.6/hash por mecanismo qualificado e finito. PRE de arquivo ou recibo NOAUTH histórico não substitui vínculo atual. A porta `beforeOpen` sozinha não observa um socket ainda inexistente: a integração precisa de callback concreto antes da autenticação e controle próprio de deadline/fechamento.

Um permit técnico privado do controller Banco pode liberar o socket pausado somente após a leitura nativa atual; não é callback Hermes, não abre uma segunda conexão e não concede retry. Qualquer recusa, timeout ou resultado incerto fecha recursos próprios e conserva evidência. Assinatura em cache não é revogação online; caminho de imagem e hash do arquivo não são digest da memória. Não declarar CA ou hostname validados.

Fonte: ADR0062 e decisão Root07 de manter sua fronteira na integração8f. Exemplo: tuple de outra conexão/PID ou pin BF/E3 recusa antes da autenticação. Testes: callback obrigatório na rota DEV, tuple/owner/deadline/permit errado, socket único e fechamento conhecido, seguidos da prova física reservada. Banco implementa o callback/connector e controller; Runtime qualifica e compõe.

## Fonte, recuperação e limites de aceite

STOP4296908 continua até prova vigente do mesmo caller/escopo pelos canais de auditoria existentes. Não realizar nova chamada para descobrir quota. Bloquear somente a operação dependente e seguir integração real independente; não injetar mock/payload para alegar entrega integrada. Preparação, catálogo, componentes construídos, handler iniciado, captura retida parcial, DQ, promoção e paridade são camadas distintas e exigem suas próprias provas.

Recuperação: desabilitar novos opt-ins e fechar exclusivamente recursos/artefatos próprios verificados; não apagar dados retidos ou históricos. Resultado incerto exige reconciliação antes de repetição. JAMAIS alterar versão/instalação SQL Server16.0.1000.6, contas/segredos/identidades/permissões/ACL, serviços/listeners/certificados/trust global, schema/Flyway, V1/PM2 ou produção. Nenhuma parte desta decisão é exceção a essas proibições.

Rastreabilidade privada: `hermes8fdb85e78657/baseline01.json`, `correct-target03.json`, `root/progress04.json`, `regras02/handoff01.json`, `banco01/handoff01.json`, `runtime01/deployment03-receipt.json`, `runtime01/run-cause03.json`, `catalog-cause06.json` e `native-link07.json`. Qualificações e resultados sucessores ficam em STATES/checkpoints sem reescrever falhas ou ledgers anteriores.

## MAIN-DEV-ENGINE-001 — entrada explícita, gates e resultado comprovado

Sucessor interno sob HERMES_863b2573942e, 13/10/2026; implementação e qualificação em curso no checkpoint0514. A nova entrada `run --dev-bundle <manifest> --profile C78_LOCAL_SHADOW_SQL_PIN_V1 --dev-engine` exige também `runtime.dev.engine.enabled=true`, desabilitado por padrão. Ela usa o plano/request C78 realmente preparados, um binding concreto de Banco e o executor operacional existente com DataSource próprio. Não reaproveita a autoridade catalogal33/39 nem muda a fábrica Windows; DevLocalEngine sintético não constitui esse backend.

Origem: pedido863b para avançar o mesmo objetivo e critérios ENGAUTH01–06 de Regras. Exemplo negativo: faltando admissão específica do engine, a Main deve devolver CONFIG_AUTH/exit20 com motivo do gate ausente, antes de provider, helper nativo, TCP/JDBC ou HTTP. Fonte/captura continuam exigindo evidência6908 vigente do mesmo caller, por canal existente legitimamente autorizado; contrato/hash/flag/Retry-After não substituem procedência, quota ou reserva. Proteção efetiva contra writers não é inferida do snapshot39. Gates e orçamentos físicos próprios continuam obrigatórios e Banco é único executor SQL/TDS.

Uma recusa anterior à tentativa de abrir sessão pode receber `DEV_ENGINE_GATE_REFUSED_ZERO_EFFECTS`, com engine/fonte/captura não executados, somente após prova da ordem e do fechamento. Ao tentar `Admission.open`, já se cruza uma fronteira possível de efeito: recusa/falha posterior não pode ser convertida em zero efeitos, mesmo se o executor ainda não foi invocado. Após tentativa do executor, falta de readback é resultado não verificado, não booleano falso nem sucesso integral. Causas são registradas de forma limitada por tipo/frame, sem mensagem que possa revelar SQL, escopo, payload ou segredo; falhas de cleanup e contadores de tentativa não são descartados.

Responsáveis: Runtime implementa CLI/composição/recibos/pacote e testes em MainDevWorkspaceProcessTest; Banco implementa o adaptador C78, gates, sessão e fechamento; Fontes define prova de quota/caller; Regras revisa as evidências ENGAUTH. Contratos afetados: somente a nova rota DEV engine e seu binding. O RUN em processo real com gate ausente qualifica a recusa nessa camada, não engine de negócio, extração, DQ, promoção, dados retidos ou paridade. A prova positiva ainda depende dos gates reais; nenhuma autoridade física foi emitida por essa regra.

Recuperação: desabilitar `runtime.dev.engine.enabled` e fechar apenas recursos próprios; conservar recibos, claims e resultados desconhecidos para reconciliação, sem replay automático. Nenhum efeito em versão/instalaçãoSQL16.0.1000.6, contas/permissões/identidades/ACL, DDL/Flyway, serviços/listeners/certificados/trust global, V1/PM2 ou produção. Evidências atuais ficam em `hermes863b2573942e/{runtime01,banco01,fontes01,regras01,root}`; o checkpoint final registrará o que realmente foi testado e as limitações.

## MAIN-DEV-ENGINE-001 — qualificação dirigida863b e admissão positiva pendente

A forma com despacho explícito acrescenta `--dev-engine <Rootdispatch> --dispatch-sha256 <sha256>`, mantendo o mesmo adaptador concreto. Handoff02 Runtime/JAR02 e checkpoint0516 registram coorte735/1286, Banco16, arquitetura7, CI3 e requalificação dos dois consumidores Runtime PASS. Dois processos públicos reais do JAR final encerraram com exit20/DEV_ENGINE_GATE_ADMISSION antes de open; bytes/classload/ordem/fechamento conferidos por Root. Isso qualifica somente a recusa real. Os construtores públicos usados pela Main ainda não recebem produtor legítimo de admissão independente; port tipado e fixture não constituem implementação/vínculo positivo. O produtor real depende de canal/procedência/acesso e quota6908 vigente do mesmo caller legitimamente admitidos, além dos contratos/proteção/reserva próprios. Não criar autenticidade por hash/flag ou renovar trust/autoridade para o canal audit de produção. Main positiva, engine, extração, DQ, paridade e aceite produtivo permanecem pendentes; zero efeito SQL/TDS/HTTP/provider nesta rodada.

## MAIN-SOURCE-ACCESS-MIN-001 — correção causal do formato local de quota

Origem HERMES_f4eda62e83d1/AGENTS/ADR0058, diagnósticos Fontes02/Regras01. Owner técnico Fontes/Banco/Runtime; decisão de unidade Root; responsável conhecido Lucas. 6908 é relatório, não quantidade. Formato863b com quotaBucket/TTL fornecedor/saldo não tem origem ESL demonstrada; não obrigar operador a fabricar campos. Critério mínimo: evidência operacional real6908 após recusa relevante, contexto efetivo correspondente, procedência/leitor legítimos, recência/contradições observáveis. Budget/deadline novos são cliente, não promessa de disponibilidade futura. STOP429/preflight antes fornecedor/token preservados; mismatch/desconhecimento corrente recusa.

Exemplo: INFO429 real02/10; INFO200/DATA2xx1row05/10 confirma acesso naquela ocorrência sem prova do caller ATUAL. Hash/JSON/status/PID/fixture isolados não autenticam. Não exigir identidade humana extra/saldo/TTL sem contrato original. Inspeção Root e autoridade física separados; catálogo independente não depende quota fonte. Somente nova rota DEV engine/shape de admissão afetados; históricos/defaults/Windows/BF/E3/C78/trust preservados. Fonte executou sete checks sobre cinco originais; contraprovas de componente/Main integrada em execução no0517, não alegadas. Sem liberação/sucesso de extração.
