# ADR 0025 — Frota de Manifestos: identidade dimensional fail-closed

- Status: Aceito para a decisão local V2-035c e revalidado pela V02; Veículos e Motoristas permanecem bloqueados
- Data: 2026-09-05
- Escopo: D00/Bloco 38 e sua reavaliação sem novo bloco, sem implementação, schema, relação ou publicação

## Contexto

A vertical 6399 preserva, no grão de cada observação física de Manifesto, placa, nome/tipo e
proprietário do veículo principal, capacidade, tipo de contrato, nome/tipo de contrato do
motorista, duas posições de reboque e o nickname da filial do Manifesto. V2-026 também congela
presença tri-state, validação de tipo/UTF-16/limite, frescor da observação, replay, conflito e
ausência não destrutiva.

Essas garantias pertencem ao Manifesto. O contrato 6399 é transitório e não declara registros
mestres de frota. A identidade P01 resolve a raiz Manifesto e seus filhos Pick/MDF-e, não Veículo
ou Motorista. A migration V012 mantém a frota como atributos da observação; não cria dimensões.

O legado materializa Veículo por placa normalizada mais filial, usando `MAX` independente para
atributos. Materializa Motorista por nome normalizado mais filial e descarta nomes que contêm um
substring genérico. Essas operações produzem compatibilidade histórica, mas não provam
unicidade, estabilidade temporal, identidade de pessoa, propriedade da filial, reatribuição de
placa, mudança de nome ou vigência. A regra antiga PUB-07 é, portanto, hipótese de compatibilidade
pendente e não contrato de identidade V2.

## Reavaliação autorizada V02

O owner autorizou reavaliar o bloqueio exclusivamente com as evidências já fornecidas no chat e no
worktree. Essa autorização altera a permissão de auditoria, não o conteúdo técnico do contrato.
Antes dos artefatos V02, o inventário temporal após o fechamento V01 encontrou zero arquivo novo.
O único anexo aberto é um prompt operacional genérico de 31/08/2026, não um contrato de Frota; os
14 artefatos ancorados na V01 continuaram com SHA-256 idêntico.

A matriz V02 tornou o gate cumulativo explícito. Veículos requer ID imutável do fornecedor ligado a
source e tenant, semântica de placa/filial e reatribuição, lifecycle completo e separação de
principal/reboques. Motoristas requer ID imutável igualmente escopado, política de homônimos,
genéricos, renomeação e merge/split, semântica de contrato/filial e lifecycle completo. A única
prova positiva é limitada: veículo principal, reboque 1 e reboque 2 possuem paths/papéis distintos
na observação de Manifesto. Ela não fornece identidade dimensional.

Os contraexemplos permanecem materiais: placa pode ser reutilizada ou reatribuída; nickname do
Manifesto pode divergir da filial proprietária ou lotação; nomes podem colidir ou mudar; merge/split
não pode ser inferido por texto; tipo de contrato contextual não prova pessoa, vínculo ou vigência;
e frescor do Manifesto não determina a versão current de um cadastro. As 13 fixtures V02 são
estritamente sintéticas e incluem controles hipotéticos completos apenas para testar a condição de
abertura de uma futura fatia, sem alegar que essa evidência existe.

## Decisão

### Veículos: `BLOCKED`

Não existe source key de veículo no 6399. `/data/mft_vie_license_plate` é texto da observação e
somente candidata a business key; não há prova de unicidade global, estabilidade cross-window,
reatribuição, colisão ou normalização aprovada. Consequentemente, nenhuma canonical key pode ser
alocada e o grão dimensional permanece não resolvido.

`/data/mft_crn_psn_nickname` fica como contexto do Manifesto. Não é filial proprietária, lotação,
tenant ou componente da chave do veículo. Nome, proprietário, capacidade e tipo de contrato
também são atributos observados, nunca identidade ou vigência cadastral.

Veículo principal, reboque na posição 1 e reboque na posição 2 mantêm paths, capacidade, papel e
proveniência separados. Um reboque não vira principal; igualdade de placa entre papéis não os
colapsa; troca de posição não autoriza rekey de entidade.

### Motoristas: `BLOCKED`

Não existe source key de motorista no 6399. `/data/mft_mdr_iil_name` é texto da observação e nunca
identidade. Nome exato, nome normalizado, nome mais filial e tipo de contrato não distinguem
homônimos, nomes genéricos, renomeação, merge ou split de pessoas. Nenhuma canonical key pode ser
alocada e o grão dimensional permanece não resolvido.

A filial continua contexto do Manifesto, não lotação do motorista. O tipo de contrato é preservado
como atributo contextual e não prova pessoa, vínculo ou vigência.

### Regras comuns preservadas

Cada path mantém `ABSENT`, `NULL` e `VALUE`. Texto bruto permanece exato; não há trim, truncamento,
case folding, remoção de diacríticos ou normalização Unicode silenciosa. Tipo inválido, surrogate
UTF-16 não pareado e overflow são quarentenados.

O frescor de V2-026 ordena somente observações de Manifesto. Replay canonicamente idêntico é
no-op, observação antiga não regride o estado da observação e divergência no mesmo frescor é
quarentena. Nenhuma dessas regras inventa effective time, current/history ou rekey de cadastro.
Ausência em página/janela não produz sweep, exclusão ou desativação.

Coocorrência de veículo e motorista não prova relação. Este bloco proíbe join, FK, associação
canônica ou cardinalidade Veículo→Motorista.

## Consequências

- V2-035c fecha como `COMPLETE_LOCAL_DECISION_ONLY`; os dois resultados dimensionais são
  `BLOCKED`.
- PUB-07 deixa de autorizar `placa+filial` e `nome+filial` como chaves. Essas expressões permanecem
  somente alvos de caracterização/paridade do legado até nova decisão sustentada por prova.
- D03 e D04 ficam bloqueadas. Não se cria o runbook Terra de D03 e nenhuma implementação começa.
- V2-035b e V2-035 continuam abertas. A vertical V2-026 pode preservar sinais de frota no
  Manifesto sem promovê-los a dimensão.
- O catálogo `docs/catalogos/frota-manifestos-v2-035c/` é o contrato decisório desta fatia e suas
  fixtures são estritamente sintéticas.

## Evidência para desbloqueio

Veículos requer conjuntamente ID imutável do fornecedor ligado a source e tenant explícitos;
algoritmo versionado de normalização/validação da placa e políticas de colisão, reutilização,
reatribuição e rekey; chave/semântica exata da filial; identidade/papéis de principal e cada
reboque; e lifecycle com vigência, current/history, rekey, conflito, reativação e ausência.

Motoristas requer conjuntamente ID imutável do fornecedor ligado a source e tenant explícitos;
política formal de homônimos, genéricos, renomeação, aliases/repoint e merge/split; semântica,
partes e vigência do contrato; chave/semântica de filial/lotação; e lifecycle com vigência,
current/history, rekey, conflito, reativação e ausência.

## Alternativas rejeitadas

- Usar placa ou placa+filial como identidade: a evidência só mostra texto em Manifestos.
- Usar nome ou nome+filial como identidade: homônimos e renomeações não são resolvidos.
- Copiar `UPPER/TRIM`, filtro de substring ou `MAX` do legado: altera valor e pode fundir entidades
  ou sintetizar atributos sem prova.
- Tratar reboques como veículo principal: perde o papel e a proveniência da observação.
- Usar chegada, página, hash ou máximo para resolver empate: não representa frescor da entidade.
- Inferir relação Veículo→Motorista pela coocorrência: um Manifesto não prova vínculo estável.

## Limites

A decisão não prova schema/fingerprint atual do fornecedor, unicidade global, estabilidade
temporal, completude, master data, snapshot, branch ownership, relação, paridade, publicação,
sweep, deploy ou cutover. Não houve rede, banco, payload real, implementação ou ação externa.

A reavaliação V02 não cria nova fatia, bloco, rota `AGORA`, migration ou prompt Terra porque nenhum
pacote de evidência satisfaz todos os requisitos de qualquer dimensão. D03 e D04 mantêm seus holds;
o próximo número funcional 39 permanece não atribuído.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FrotaManifestosV2035cDecisionCatalog.ps1
```
