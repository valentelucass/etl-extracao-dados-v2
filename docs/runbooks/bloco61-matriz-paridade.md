# B61 — inputs para caracterização e paridade

PREPARADO_LOCALMENTE_ORACULOS_PENDENTES, 10/09/2026. Nenhuma caracterização real
executada. A–D do B61 não fecham V2-012a/b/c. Nenhuma rota está liberada a AGORA.

A próxima candidata preferencial é **Q-USR-01**, porque V2-025a, V2-009a,
V2-009d/Usuários e V2-033 já têm evidência local, e B59/B60 acrescentaram provas
do runtime local. Isso reduz dependências de implementação, mas falta o oráculo
GraphQL autorizado e representativo. Q-COL-01 também tem base local (V2-010) e
pode precedê-la se seus inputs chegarem antes. Não há nova dependência serial
entre as duas. Q-MAN-01 conserva EXTERNAL_HOLD.

| Entidade / rota | Contrato e identidade existentes | Oráculo necessário | Janela/volume a ratificar | Comparações e impedimento exato / evidência de desbloqueio |
| --- | --- | --- | --- | --- |
| Usuários / Q-USR-01 | V2-025a `graphql-individual`; V2-009a e enforcement V007/V2-033; `/node/id` INTEGER ou STRING com tag e escopo; `user_id` legado exige correspondência explícita | Export/páginas GraphQL `individual(enabled=true)` e expectativa independente do owner sobre a população incluída, tipos e presença; não usar saída V2 como seu próprio oráculo | Instante/intervalo da captura, representatividade e limites de execução; `first` ≤20, páginas/linhas/bytes dentro dos tetos locais abaixo | `id`, `name`, ABSENT/NULL/VALUE, Unicode, grão edge/node, duplicidade, tags, `pageInfo` e cursores. Falta arquivo autorizado com scope/binding, aceite de Segurança aplicável e owner. B58 aceita name ABSENT no release atual, enquanto o perfil Q-FND histórico só NULL/VALUE: ratificar diferença sem reescrever a fixture histórica. Sem campo temporal de fonte; observed_at é técnico. |
| Coletas / Q-COL-01 | V2-025a `dataexport-6908`; V2-009a `/id` INTEGER escopado; `/sequence_code` é alias; V2-010 shadow | Páginas/export 6908 e expectativa independente para campos, expansão e bordas temporais; comparação V1 somente com export read-only nominalmente autorizado | Intervalo fechado de `picks.request_date`, fuso America/Sao_Paulo, repetição/bordas e atualização complementar a ratificar; `per` ≤100 IDs distintos, não teto de linhas físicas | Tipos/presença/chaves/grão, status/cancelamento, request/service/finish/status_updated_at/updated_at, expansão, contagens, candidatos relacionais já disponíveis. Falta oráculo, tradução temporal comprovada, scope/binding e permissão de fonte. Artefato owner com referência de contrato e amostra representativa resolve o input; fixture não resolve. |
| Fretes / Q-FRE-01, extensão Q-FND-02 | V2-025a `dataexport-6389`; V2-009a `/id` INTEGER; alias corporation_sequence_number; V2-011 shadow | Canal Data Export autoritativo; GraphQL sidecar separado, observacional e autorizado por conta própria | Janela service_at e fronteiras a comprovar, `per` ≤100 raízes; limites independentes por canal | FRE-01–07: tipos ainda UNVERIFIED, timestamps de performance, status, presença, candidatos financeiros/Coleta. Falta oráculo de cada canal e tradução temporal; sidecar não substitui raiz nem frescor. |
| Manifestos / Q-MAN-01 | V2-025b/6399, V2-009b/6399, V2-026; raiz INTEGER:sequence_code; Pick/MDF-e pertencem à raiz | Oráculo 6399 e remoção formal do EXTERNAL_HOLD | Janela representativa do contrato, expansão raiz/filhos e volumes a aprovar | MAN-01–07, frescor/competência, filhos, métricas, conflitos. Hold externo e falta de scope/oráculo impedem a execução; B61 só corrige o redutor local. |
| Cotações / Q-COT-01 | V2-025b/6906, V2-009b/6906, V2-027; INTEGER:sequence_code escopado | Oráculo 6906 com expectativas COT-01–07 | Janela e representação temporal a aprovar, dentro do perfil Q-FND-01 | Presença, terminalidade, frescor, moedas/UFs e referência tarifária aplicável; faltam oráculo e garantias da fonte, sem inferir contrato das fixtures. |
| Localização / Q-LOC-01, extensão Q-FND-02 | V2-025b/8656, V2-009b/8656, V2-028; INTEGER:corporation_sequence_number escopado | Oráculo 8656 com expectativas LOC-01–07 | Janela service_at, bordas/fuso e volume a ratificar | Tipos/lexemas numéricos, nulos/zero, frescor, status e candidato Frete; faltam oráculo e garantia temporal. Nenhuma relação/crosswalk inferida. |

Os bindings completos e fingerprints estão nos perfis existentes de
[`Q-FND-01`](../../src/test/resources/contracts/v2-012/profiles/)
e [`Q-FND-02`](../../src/test/resources/contracts/v2-012/extensions/q-fnd-02/profiles/).
O catálogo [B58](../catalogos/bloco58-local/README.md) registra as diferenças
entre perfis históricos e parser/mapper atuais; preservá-las nominalmente.

Inputs mínimos a entregar para **uma** rota, antes de qualquer efeito externo:

| Input | Conteúdo mínimo e aceitante |
| --- | --- |
| Autorização nominal | Rota/entidade/canal, finalidade V2-012a, fonte ou export read-only exato, owner, vigência, principal autorizado, local privado e descarte/retenção. Nenhum token ou dado de negócio no documento público. |
| Segurança aplicável | Atestado real sanitizado de rotação/invalidação e saúde do writer legado, aceite nominal e grants/scopes da classe de credencial envolvida, conforme V2-041. O [validador de atestado](../../scripts/validation/Test-V2041RotationAttestation.ps1) valida estrutura, não autenticidade nem aprovação. Autorização B61/B60 e fixture de atestado não substituem isso. |
| Binding e proveniência | source_instance, tenant_scope, versão/fingerprint de contrato e identidade, operação, referência do export/oráculo e responsável independente pelas expectativas. Não usar DEFAULT/GLOBAL/SINGLETON. Registrar hash do arquivo privado, nunca hash de ID como evidência pública de identidade. |
| Janela e orçamento | Início/fim/fuso ou instante de captura, critérios de representatividade, páginas, raízes, linhas físicas, bytes, duração total, timeout e regra de parada aprovados. Não reutilizar teto/saldo B60. Limite temporal novo precisa ser explícito; esta matriz não concede minutos/requisições. |
| Expectativas e divergências | Campos e casos de nulo/ausência/zero, chave/tag/escopo, status, datas, expansão/paginação; divergências esperadas com motivo, dono e decisão; nenhuma tolerância genérica. |

Os tetos existentes de Q-FND/B58 são 65.536 bytes por entrada lida, 1.000 linhas,
100 páginas, profundidade 16, 256 paths e 4.096 nós estruturais onde aplicável.
São limites técnicos, não orçamento de acesso autorizado. Aplicar o menor teto
entre harness, contrato e autorização. Em Coletas, expansão física não altera
o limite de raízes de `per`; se a contagem não for verificável, recusar. Em
Usuários, página curta/fim de paginação não prova snapshot completo.

Procedimento revisável após chegada dos inputs:

1. Validar hashes, escopos, vigência, proveniência/owner e segurança aplicável.
   Selecionar uma rota, congelar pacote privado com expectativas independentes,
   limites e recuperação (sem publicação; interromper captura ao primeiro erro,
   não repetir efeito desconhecido). Reavaliar elegibilidade nominal no STATES.
2. Reusar `CharacterizationProfileRegistry`/`CharacterizationEvaluator` e os
   perfis Q-FND-01; para Fretes/Localização, `qfnd02` e seus canais. Para comparação
   com o código atual de Usuários/Coletas, reusar `UsuariosCharacterization`,
   `ColetasCharacterization` e `LocalCharacterization.Pages` de B58. Implementar
   apenas a ligação test-only ao input aprovado se necessária, sem nova fundação,
   dispatcher produtivo, flags de autorização fictícias ou real data em fixtures.
   Os adapters atuais Q-FND são sintéticos; executar seus testes não consome um
   oráculo real nem gera aceite externo.
3. Avaliar primeiro parser/contrato, depois mapper/staging alcançados. Gerar receipt
   sanitizado pelos writers existentes, com camada, limites, contagens e razões,
   além de hash de revisão/proveniência. Classificar cada divergência e obter aceite
   nominal ou correção. Só então confrontar o critério V2-012a da entidade.

| Gate original | Evidência que ainda será necessária / consequência permitida |
| --- | --- |
| V2-012a | Janela representativa limitada comparando campos, tipos, presença, chaves, grão, status, timestamps/fuso, expansão, contagens e relações disponíveis, divergências classificadas. Só habilita bootstrap controlado específico. |
| V2-047 | Depois de V2-012a e vertical aplicável: fonte histórica autorizada, horizonte, estratégia, T0/Tcut/delta, reconciliação e recuperação do histórico. Reusar catálogo bootstrap existente; sem bootstrap neste bloco. |
| V2-012b | Depois de V2-012a, V2-047 quando houver histórico e V2-046a/b quando aplicáveis: janelas fechadas/repetidas, comparação set-based no SQL de conjuntos, volumes, nulos, status, datas, relações/órfãos/financeiro/somas; Java só contagens e TOP(N) sanitizado. Divergência corrigida ou aceita nominalmente. SQL exige autorização futura. |
| V2-012c | V2-012b de todas as entradas, V2-036 quando aplicável e V2-037 por saída; comparar grão/chaves/cardinalidade/schema/filtros/datas/financeiro/labels com oráculo aprovado. Nenhuma saída foi qualificada no B61. |

V2-025d mantém gate V2-041 e ratificação owner de janela/teto da rodada própria;
não é autorização implícita de Q-USR/Q-COL. V2-015d (feed/baseline real), V2-016b
(governança remota), V2-045b (retenção) e V2-039 (operação/backup/restore) conservam
inputs e aceites próprios; não bloqueiam a manutenção offline nem recebem aceite
por ela. Users SHADOW_UPSERT_ONLY continua transitório: ausência não autoriza
exclusão, Sweep and Prune, snapshot completo ou cutover.
