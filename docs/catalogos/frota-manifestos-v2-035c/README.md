# Frota de Manifestos — decisão e reavaliação V2-035c

Este catálogo encerra somente a decisão local do Bloco 38/D00. Ele não cria dimensão, schema,
runtime, relação ou contrato publicado.

A reavaliação V02 foi autorizada pelo owner em 05/09/2026 e usou somente o chat e o worktree.
A autorização permitiu auditar o bloqueio, mas não constituiu prova dimensional. Antes de criar os
artefatos V02, o inventário após o fechamento V01 encontrou zero arquivo técnico novo; o anexo
`pasted-text.txt` de 31/08/2026, SHA-256
`2d768f7f5474047ba3f4b36f74c59ca54bad2555d7efeab743fee6279db845c0`, contém somente um prompt
operacional genérico. Os 14 hashes de evidência da V01 continuaram íntegros. Portanto, V02 não
cria nova fatia decisória, rota ou bloco funcional.

## Resultado

| Dimensão | Estado | Motivo material |
| --- | --- | --- |
| Veículos | `BLOCKED` | O 6399 fornece placa e atributos no grão da observação de Manifesto, mas não fornece um identificador estável de veículo nem prova placa, filial, vigência ou rekey como identidade. |
| Motoristas | `BLOCKED` | O 6399 fornece nome e tipo de contrato no grão da observação de Manifesto, mas não fornece um identificador estável de pessoa nem resolve homônimos, nomes genéricos, filial, vigência ou rekey. |

Placa, nome, proprietário, capacidade, tipo de contrato e filial são sinais preserváveis; não são
prova de entidade. O veículo principal e cada posição de reboque permanecem papéis distintos. A
coocorrência de veículo e motorista em um Manifesto não cria relação canônica, join ou chave
estrangeira.

Como Veículos não ficou `EXECUTION_READY`, o arquivo
`docs/runbooks/frota-manifestos-v2-035b-execucao-terra.md` deve permanecer ausente. D03 e D04 não
podem iniciar implementação a partir desta decisão.

## Resultado requisito a requisito da reavaliação V02

| Dimensão | Requisito obrigatório | Estado | Lacuna exata |
| --- | --- | --- | --- |
| Veículos | ID imutável do fornecedor com source e tenant explícitos | `MISSING` | Não existe path, wire type, garantia de imutabilidade/unicidade/estabilidade nem binding de ID de veículo ou reboque a `source_instance` e `tenant_scope`; esses escopos pertencem somente à raiz Manifesto. |
| Veículos | Semântica de placa, colisão e reatribuição | `MISSING` | Placa é texto da observação; faltam normalizador/validador versionado e regras de reutilização, colisão, reatribuição e rekey. |
| Veículos | Semântica de filial e mudança | `MISSING` | O nickname é contexto do Manifesto; não existe chave nem contrato de filial proprietária/lotação, participação no scope ou mudança/ausência. |
| Veículos | Vigência, current/history, ausência, conflito e rekey | `MISSING` | Frescor e conflito existem apenas para a observação de Manifesto; falta lifecycle no grão do veículo. |
| Veículos | Principal separado de reboque 1 e reboque 2 | `PROVEN_LIMITED` | Os três papéis e paths são distintos somente na observação; não há identidade cross-Manifesto nem regra de troca de posição/papel. |
| Motoristas | ID imutável do fornecedor com source e tenant explícitos | `MISSING` | Não existe path, wire type, garantia de imutabilidade/unicidade/estabilidade nem binding de ID de motorista ao scope explícito. |
| Motoristas | Homônimos, genéricos, renomeação, merge e split | `MISSING` | Nome exato/normalizado e o filtro de substring legado não fornecem continuidade, aliases, colisão, merge/split ou repoint. |
| Motoristas | Semântica de contrato | `MISSING` | `mft_mdr_contract_type` é texto contextual; faltam ontologia, partes, vigência e transições do vínculo. |
| Motoristas | Filial/lotação e mudança | `MISSING` | A filial do Manifesto não prova lotação do motorista nem participa de identidade aprovada. |
| Motoristas | Vigência, current/history, ausência, conflito e rekey | `MISSING` | Não há lifecycle, snapshot autoritativo, reativação, conflito ou rekey no grão do motorista. |

Coocorrência continua sem contrato de relação, identidade, cardinalidade ou vigência. Como cada
dimensão exige todos os seus requisitos e ao menos um está ausente, ambas permanecem `BLOCKED`.

## Decisão fail-closed congelada

- Cada path conserva `ABSENT`, `NULL` ou `VALUE`, bruto e proveniência no limite da observação de
  Manifesto. Não há complemento por versão antiga.
- Texto não recebe trim, truncamento, case folding, remoção de diacríticos ou normalização Unicode
  silenciosa. Tipo inválido, surrogate UTF-16 não pareado ou overflow vai para quarentena.
- Não há algoritmo aprovado de normalização ou validação de placa. O `UPPER/TRIM` legado é apenas
  contraevidência e nunca forma business key.
- Nome de motorista nunca é identidade. Homônimo, nome genérico e mudança de nome não são
  resolvidos por string nem pelo tipo de contrato.
- `/data/mft_crn_psn_nickname` é contexto exato do Manifesto; não prova filial de origem, lotação,
  propriedade, tenant nem componente de identidade de veículo ou motorista.
- O frescor `finished_at → closed_at → departured_at → created_at` ordena observações de Manifesto,
  não vigência de cadastro. Replay idêntico é no-op; divergência no mesmo frescor é quarentena;
  observação antiga não regride o redutor.
- Ausência em página ou janela não desativa, exclui ou varre entidade.

## Artefatos

- `decisao-v01.json`: baseline histórica do Bloco 38, com 14 evidências ancoradas por SHA-256.
- `decisao-v02.json`: resultado corrente da reavaliação autorizada, inventário de entrada, requisitos,
  lacunas exatas, origens, limites, contraexemplos, fixtures/testes e decisão de rota.
- `matriz-evidencias-v01.csv`: uma origem local, limite, contraexemplo, disposição e teste por
  regra.
- `matriz-reavaliacao-v02.csv`: onze requisitos reavaliados, incluindo a prova limitada de
  separação principal/reboques e a ausência de contrato Veículo→Motorista.
- `fixtures/casos-v01.synthetic.json`: somente classes simbólicas e tokens `SYNTH_*`; não contém
  valor produtivo.
- `fixtures/reavaliacao-v02.synthetic.json`: 13 pacotes de evidência puramente simbólicos; inclui
  controles positivos hipotéticos que apenas demonstram quando uma nova fatia poderia ser aberta
  e não alegam que tal evidência existe.
- `../../adr/0025-frota-manifestos-identidade-dimensional-fail-closed.md`: decisão arquitetural que
  impede copiar as chaves heurísticas de PUB-07.

## Evidência necessária para reabrir D03 ou D04

Veículos exige conjuntamente identificador imutável do fornecedor ligado a `source_instance` e
`tenant_scope` explícitos; semântica aprovada de placa, colisão e reatribuição;
normalizador/validador versionado; chave/semântica exata de filial; papéis e identidade de principal
e de cada reboque; e lifecycle com vigência, current/history, ausência, conflito e rekey testáveis.

Motoristas exige conjuntamente identificador imutável do fornecedor ligado a `source_instance` e
`tenant_scope` explícitos; política versionada para homônimos, genéricos, renomeação, merge/split,
aliases e repoint; semântica/vigência do contrato e da filial/lotação; e lifecycle com vigência,
current/history, ausência, conflito e rekey testáveis.

Até essas provas existirem, observações podem continuar preservadas pela vertical V2-026, mas não
podem ser promovidas a dimensões.

## Revalidação local

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-FrotaManifestosV2035cDecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6399ContractCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6399IdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ManifestosV2026DecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ManifestosV2026ShadowVertical.ps1
```

Nenhuma rede, banco, segredo, payload real, Java, SQL, migration, deploy, commit ou push é
necessário ou autorizado por este catálogo.
