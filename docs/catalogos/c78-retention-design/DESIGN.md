# D-C78-RETENTION-DESIGN-01 — direção de desenho de retenção C78

**DESENHO_ADOTADO_SEM_PERFIL_OPERACIONAL. RECORTE_DE_MODELO_PURO_QUALIFICADO.**

16/10/2026. Root adota somente D-C78-RETENTION-DESIGN-01 como **DIREÇÃO_DE_DESENHO**, pela ADR0067, após o checkpoint0535. Não adota perfil operacional, orçamento ou efeito. Nenhuma implementação **OPERACIONAL** foi iniciada. O consumidor Python autônomo e seus44 expected literais foram qualificados em cópia privada:44 correspondências/zero divergências, nos bytes aqui publicados.

C78/SAMPLE operacional continua rollback-only. ESL permanece em standby. MainHTTP da ocorrência anterior continua UNKNOWN; ausência de recibo não é reconstruída. SQL Server16.0.1000.6, instalação, identidades, credenciais, permissões/ACL/elevação, DDL/Flyway, serviços/listeners/TLS global, V1 parado e produção permanecem preservados. Root é o único owner de documentos/CI/autoridade; Banco é o único executor SQL/TDS quando uma unidade física futura for expressamente reservada. Este desenho não é essa reserva.

## 1. Decisão adotada e resultado máximo do modelo

A adoção tem dois objetos distintos:

1. Uma especificação de direção técnica para eventual retenção parcial C78, incluindo responsabilidades e requisitos ainda sem implementação.
2. Um recorte de modelo puro para geometria/namespace sintéticos, aritmética de créditos e classificação de observação. Esse recorte não implementa o contrato operacional completo nem um owner físico.

Status do desenho: **DESENHO_ADOTADO_SEM_PERFIL_OPERACIONAL**. Status do recorte: **RECORTE_DE_MODELO_PURO_QUALIFICADO**, com44/44 expected correspondentes no recibo real arquivado no checkpoint0536. Esse resultado é limitado aos subcontratos abaixo, sem intenção/owner/budget físico completos.

A direção não adota API Java, configuração, flag, dispatcher operacional, `Admission`, `Session`, `DataSource`, integração ao build/runtime, conexão, credencial, permit, retenção real ou commit. O consumidor puro não chama adapters do projeto nem representa Root. Resultado positivo sempre conserva `MODEL_ONLY_NO_EFFECT_AUTHORITY` e `authorizesPhysicalEffects=false`.

## 2. Origem e distinção E3/C78

Fontes de projeto: ADR0060,0062,0064,0065 e0066; proposta0533 e detalhamento preservados por hash, com origens/cópias no índice do checkpoint0536. As ADR existentes não são alteradas nem recebem novo significado por esta direção.

| Dimensão | E3 existente | C78/SAMPLE existente | Requisito da direção futura |
|---|---|---|---|
| Aprovação/perfil | Aprovação e opt-ins próprios da ADR0060 | Admissão C78 e SAMPLE próprios | E3 não confere autorização C78; perfil retido exigirá decisão própria |
| Footprint | Contrato E3 de até 1000 linhas, 12 tabelas e 8 CALLs | SAMPLE de 1..12 linhas; proteção all29; antecedente catalogal real delimitado de 29 tabelas/312 colunas/29 procedures/34 módulos | Mapear o footprint real da implementação; evidência catalogal anterior não é observação continuamente atual |
| Terminal parcial | `CANCELLED/TEST_PAGE_LIMIT` no contrato retido | `FAILED` exigido pelo readback SAMPLE | Não converter um recibo SAMPLE no terminal de retenção |
| Finalização | `finishPartial` com tentativa de commit após readback | `finishSample` com `rollbackAndClose` | Caminho separado; proibir flag/booleano que troque rollback por commit |
| Pós-readback | Implementação existente usa a mesma conexão física em autocommit | Prova de ausência/estado após rollback na mesma conexão | Confirmação futura em nova conexão e unidade read-only, com verifier separado |
| Conhecimento | Commit conhecido/incerto e fechamento têm significado próprio | Rollback/ausência/close têm provas próprias | Preservar dimensões; não promover ausência a rollback ou observação atual a ack histórico |

Referências de código, sob a raiz `C:\Users\suporte\Documents\projetos\etl-extracao-dados-v2`: `src/main/java/br/com/esl/etl/v2/plataforma/persistencia/controle/TestRetainedSqlContract.java`, `TestRetainedSqlSession.java`, `C78DevEngineSession.java`, `C78Sample12Owner.java`; e `src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeDevEngineBankBinding.java`. São referências do comportamento existente, não implementação do perfil proposto.

## 3. Direção de contrato completo ainda por implementar

A eventual intenção operacional precisará vincular perfil/operação, ocorrência/ciclo, namespace lógico, entidade/template, modo/janela/fuso, fingerprints de contrato/configuração/plano/release, geometria, terminal e origem/caller efetivamente selecionados. Valores sensíveis permanecem em RAM limitada; resultados públicos devem conter somente códigos/contagens próprios.

A geometria de projeto proposta é Coletas/template6908, per1/página1, uma raiz, um microbatch e1..12 linhas físicas. É um limite de desenho para uma captura parcial futura; não é escolha de janela/data, autorização Source ou prova de completude. Vazio não é candidato retido nesse recorte. O terminal parcial pretendido é `CANCELLED/TEST_PAGE_LIMIT`; sua cadeia efetiva e compatibilidade catalogal continuam trabalho futuro.

Source/tenant novos significam somente isolamento lógico aprovado, vinculado à mesma origem/caller. Não significam rotação de empresa, principal, egress, integração ou quota bucket para escapar STOP429. Nenhum INFO/saldo/prazo consumido é renovado pelo tempo ou pela adoção deste desenho.

O owner físico futuro será único. Adapters receberão handle emprestado sem poder transacional. Elegibilidade, pré-readback, tentativa única, conhecimento do retorno, confirmação e fechamento serão responsabilidades distintas; um sucesso agregado não apagará UNKNOWN. Preservar uma expectativa limitada em RAM pode permitir comparação posterior, mas não concede autoridade. Se ela se perder, observação técnica atual não reconstrói conjuntos de negócio nem ack histórico ausente.

## 4. Matriz de estados e evidências

Os termos abaixo são requisitos de desenho. Não são recibos de efeitos já ocorridos.

| Situação futura observada | Conhecimento conservado | Requisito de tratamento |
|---|---|---|
| Intenção/preparo válidos, sem admissão física | Commit não tentado | Permanecer em preparo; nenhum efeito nasce do DTO/modelo |
| Falha de proteção, prazo, budget, readback ou terminal antes de commit | Commit não tentado | Apenas rollback/close próprios, se a futura unidade os autorizar; preservar causa |
| Expectativa vazia/excessiva, geometria ou vínculo divergentes | Recusa pré-finalização | Não reduzir silenciosamente volume nem inventar terminal |
| Gates e pré-readback íntegros | Elegibilidade, ainda sem commit | Registro de tentativa antes de uma única chamada futura do owner |
| Tentativa retornou sem erro | ACK_KNOWN | Confirmação posterior e close continuam dimensões independentes |
| Tentativa perdeu retorno/lançou erro/timeout | UNKNOWN | Não repetir commit nem fazer rollback compensatório; close próprio e reconciliação read-only limitada |
| Ack conhecido + readback independente íntegro + close conhecido | Candidato a retenção parcial efetiva | Ainda não implica completude, publicação, paridade ou cutover |
| Ack conhecido + readback indisponível/divergente ou close incerto | Preservar ack e falha específica | Não reclassificar como rollback nem apagar dados |
| Tentativa incerta + readback independente correspondente | Retenção atualmente observada; ack UNKNOWN | Não reconstruir ack histórico |
| Ausência observada no escopo/instante próprios | CURRENT_ABSENT | Não inferir rollback histórico, MainHTTP0 ou permissão para nova captura |
| Leitura indisponível/truncada/sem vínculo | OBSERVATION_UNAVAILABLE | Não transformar em ausência/zero/sucesso |
| Abertura/close não retornou prova suficiente | CLOSE_UNKNOWN | `None`/objeto ausente não equivale a recurso fechado; preservar primária e suprimidas categóricas |

A confirmação futura requer nova conexão, unidade e verifier independentes, vinculados à ocorrência e à expectativa permitida. A direção não exige outro processo por inferência. A distinção sintética por nomes no modelo puro não autentica conexão, processo ou usuário reais.

## 5. Orçamentos separados e custo da tentativa

Antecedente catalogal, captura, confirmação e eventual reconciliação são unidades distintas, com reservas futuras próprias. Nenhuma herda saldo, claim ou deadline de outra. O orçamento operacional ainda **não está escolhido nem demonstrado**.

| Rota futura | Inventário obrigatório ainda por fechar |
|---|---|
| Antecedente | Conexão/provider/DPAPI/native/TLS, catálogo completo, contexto, writers/proteção e close |
| Source | Leases/holder, INFO se adotada, requests/bytes/deadline composto e fechamento; STOP no primeiro não2xx/429/limite inválido; sem retry/redirect/fallback |
| Abertura SQL | Conexão/LOGIN/TLS/provider, contexto, controle transacional; limites login/socket/lock/query distintos |
| Trabalho | CALLs/staging/transições/heartbeat/auditoria, prova obrigatória por CALL e recursos de statements/results |
| Pré-readback | Binding, conjuntos tipados, contagens, terminal/motivo, não promoção e prova da transação protegida |
| Decisão/finalização | Revalidações, custo próprio da tentativa única, rollback apenas pré-commit possível, contexto e close; enumeração de todos os sufixos alcançáveis |
| Confirmação | Nova conexão/verifier/unidade read-only, contexto e vínculo/conjuntos tipados/close |
| Reconciliação | Leitura técnica própria, sem replay/KILL/cleanup SQL ou reconstrução de ack |

Contagem de SELECTs não mede todo TDS, custo do driver, sockets ou controle transacional. Os trechos conhecidos do SAMPLE atual não são orçamento de retenção, e sua reserva de3 SELECTs de cleanup não pode virar crédito de commit/pós-readback. O inventário final dependerá da implementação concreta revisada, sem inferir128 comandos nem outro cap por costume.

No ledger abstrato, cada definição de unidade e seu sufixo são explícitos. `CHARGE` admite custo+prova somente se, junto do consumido e da reserva do sufixo, couber no limite com soma int64 exata. Uma falha não devolve consumo. Unidade finalizada não reabre; não há empréstimo entre buckets.

**Regra normativa do modelo refinado:** `COMMIT_ATTEMPT` exige `costCredits` inteiro positivo, sem default. Antes do registro, exige `usado + custo_da_tentativa + reserva_do_sufixo <= limite`, com overflow recusado. Admitido: debita esse custo e registra tentativa1; recusado: não debita nem registra. O custo da tentativa **não está incluído em FINALIZE/sufixo**; FINALIZE é custo distinto da finalização modelada. Falha posterior não recredita; segunda tentativa recusa sem novo débito. Números dos vetores são `ABSTRACT_MODEL_CREDIT_NOT_SQL_OR_HTTP`, sem equivalência a comandos/conexões/tempo autorizados.

## 6. Recorte puro qualificado e suas lacunas

O consumidor stdlib e os vetores são autônomos de revisão. Importar o consumidor não lê arquivo, não inicia thread/filho nem abre socket; o CLI de qualificação lê apenas seu JSON vizinho e cria resultado novo no mesmo diretório. Não há JDBC, provider, adaptador do projeto ou texto SQL funcional.

| Domínio | Parcela representada | Não representado / não concluído |
|---|---|---|
| INTENT —10 vetores | Geometria1..12/uma raiz/página1/batch1, terminal proposto e três símbolos MODEL (`source`,`tenant`,`occurrence`) | Perfil/operação/ciclo/template/per/janela/fuso/fingerprints/release/caller reais; não é intenção operacional completa |
| BUDGET —19 vetores | Quatro buckets abstratos, custos/provas/sufixo, soma int64, tentativa única com custo explícito, não recreditamento/reabertura | Inventário físico/driver/deadline/rotas reais, gates do owner, reserva Root; não é budget operacional completo |
| OBSERVATION —15 vetores | Classificações independentes de ACK/UNKNOWN, readback, rollback e close; confirmação por rótulos distintos | Autenticação de recursos, consultas reais, fingerprints, expectativa perdida ou comparação de conjuntos reais; não é verifier físico |

São44 expected completos **literais** no JSON, escritos a partir dos requisitos, não computados pelo resultado do modelo. A comparação distingue boolean de número. Positivos significam combinação válida dentro do subcontrato; os três domínios não são compostos em uma intenção/owner elegível. `candidateConfirmedInModel=true` só descreve uma combinação sintética; não é confirmação de commit real.

Os vetores incluem fronteiras exata/cap−1, int64 overflow, custo da tentativa ausente/zero, retry e recreditamento recusados, namespace/unidade/conexão/verifier cruzados, readback indisponível/ausente/divergente e close incerto. Root executou os44 vetores nos bytes congelados, sob o executor vigente:44 correspondências/zero divergências. SHA do resultado privado:69a7efcb27c87edc85095d0434592f34ac2c8ee94e68dd537bba013c751e1b8f. Todos os resultados continuam sem autoridade física.

## 7. Três entregas amplas: estado correto

| Entrega da proposta | Parcela elegível nesta decisão | O que permanece aberto |
|---|---|---|
| A — intenção/contrato | Direção escrita e recorte puro de geometria/namespace/terminal | Intenção completa com perfil/operação/ciclo/janela/fuso/fingerprints e vínculo operacional; não declarar EntregaA integral concluída |
| B — owner/worksheet | Direção do owner único e ledger aritmético puro | Máquina completa ligada aos gates, implementação do owner, inventário/budgets físicos e qualificação; não declarar owner/budget completos |
| C — confirmação/falha honesta | Matriz escrita e classificação sintética de observação | Conexão/verifier independentes reais, vínculo e expectativa/conjuntos, recuperação/close concretos; não declarar confirmação física entregue |

A fase3 reduzida pode encerrar uma **parcela local de design/modelo**, quando adotada/revisada/efetivamente qualificada e documentada com seus limites. Não fecha as três entregas amplas, retenção C78 ou a meta de extração completa. Não soma suites sobrepostas nem cria checkbox operacional por inferência.

## 8. Critérios e próximos aceites separados

Root adotou somente a direção, preservou as fontes anteriores e revisou o recorte/expected, com revisões estáticas independentes de Banco e Fonte. A execução real dos44 vetores passou em cópia privada byte a byte igual aos inputs publicados, com hashes e receipt arquivados. Resultado local: `RECORTE_DE_MODELO_PURO_QUALIFICADO`, sempre sem autoridade física.

Depois continuam decisões/entregas próprias: perfil operacional retido; intenção completa; owner e inventário de budgets; resolução ESL/origem/caller/STOP; catálogo e proteção físicos então atuais; reservas de antecedente/captura/confirmação/reconciliação; persistência/readback/close e governança produtiva TTL/legal hold/storage/backup/restore/RTO/RPO/release/cutover. Nenhum purge/job ou rota commit é criado nesta decisão.

Proveniência preservada: proposta0533 SHA256 `c13832c068bc1350b8ad9294cb3a3860b034208055a4a3022805cb96af6e6f0a`; detalhamento de origem SHA256 `e31a25064f7669f97541cdeba6298aff6fd0462c5dd966435ac824f1e20333fd`. Ambos permanecem intactos. Este desenho não os sobrescreve nem adota retroativamente seu escopo maior.
