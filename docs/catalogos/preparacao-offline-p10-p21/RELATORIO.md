# P10/P11/P12/P14/P15/P21 — preparação offline

Rodada de 21/09/2026: `target/preparacao-offline-p10-p21-20260921-01/`.
Autoridade: pedido atual do usuário, STATES e plano de preparação da trilha.
Sem subagentes. Não houve mudança de runtime, dependência, workflow, schema ou
regra de negócio. Construção **39/45** e aceites **67/115**, sem novos aceites.

## Resultado por frente

| P | Frente local executada | Parcela externa |
|---|---|---|
| P10 | Inventário do worktree, HEAD local, zero remotes, sete usos de Actions fixados por SHA; política/workflow verificados estaticamente | BLOQUEADO_POR_INPUT: G02. Não há conjunto aprovado de publicação, owner nominal, proteção remota ou CI comprovado |
| P11 | Política e implementação fail-closed: 33 casos sintéticos, três admissões e 30 recusas; zero exceção efetiva; fingerprint conferido | BLOQUEADO_POR_INPUT: FEED. Nenhum Dependency-Check real, feed/NVD ou baseline aceita |
| P12 | Manifestos de schema, lifecycle e pacote Windows; gate progressivo estático; lifecycle de logs com arquivos temporários sintéticos | BLOQUEADO_POR_INPUT: G05. Não revalida contas, TLS, grants ou SQL atuais; não executa COMMIT/crash/restore |
| P14 | Quatro catálogos de identidade e seus fingerprints; Raster com 18 aspectos, 51 campos, 16 casos e 12 contraprovas | BLOQUEADO_POR_INPUT: G03/G04. Raiz/filho/título/rekey permanecem conforme as decisões pendentes |
| P15 | Manifesto e contrato de referências; oito famílias da fundação; consumidor de arquivos e cobertura temporal em Java | BLOQUEADO_POR_INPUT: G04. Baselines/releases nominais e ratificação continuam ausentes |
| P21 | DAG das seis dimensões; current de Usuários, binding sintético explícito, fronteiras de arquitetura e preservação dos papéis de frota | BLOQUEADO_POR_INPUT: G04 e fontes qualificadas das fatias usadas. Publicação continua P25 |

A [matriz](matriz.json) contém 17 requisitos, origem, owner-papel e dependências
obrigatórias/condicionais de cada frente. `nominalOwner=null` e
`evidenceReceived=false` registram a falta de input nesta ordem, sem inventar
responsável. Os 36 pins registram a revisão local; não são atestados externos.

## Inputs externos concretos

| Gate | Owner-papel registrado | Requisito sanitizado |
|---|---|---|
| G02 | Owner do repositório | Provider, remote/branch, conjunto exato de arquivos, aprovadores, proteções/checks e autorização individual de publicação. Após efeito autorizado: SHA remoto, histórico escaneado e CI real do SHA |
| FEED | Responsável de segurança | Feed autorizado com procedência/frescor, janela/autoridade, JSON+HTML da revisão exata, tratamento dos achados e aceite nominal da baseline. Exceções exatas por PURL/vulnerabilidade, justificativa, owner e expiração até 90 dias |
| G05 | DBA, Operações, Segurança e Compliance; data owner para ratificação de retenção | Alvo dedicado, principals/TLS/ACL, capacidade/quotas/storage, policy/TTL e hold/release ratificados, WORM/imutabilidade, criptografia, backup/restore, RTO/RPO e autorização por efeito com janela, limites, ledger e recuperação |
| G03 | Fornecedor e owner de dados por fonte | Contrato/garantia ou oráculo independente aprovado com tipos, grão/escopo, estabilidade, colisões, cardinalidade, relações e rekey; requisitos específicos por entidade abaixo |
| G04 | Negócio e owners de referências e consumidores | Grão/precedência fiscal-financeira e fonte própria da série NFS-e; baselines/release/vigência/proveniência por família; identidade/papéis/lifecycle dimensional e ratificação nominal |

G03/10633 exige raiz e shape do mapping, além do papel Frete/minuta.
G03/8636 exige identidade de raiz e relação raiz–parcela–rateio; a candidata de
parcela já foi refutada como chave da linha. G03/4924 separa o ID físico do
título lógico e exige crosswalk título–documento–Frete e filhos. G03/6392 exige
colisão/grão entre sequência, minuta e ocorrência. Raster exige raiz tipada e
escopada, parada estável sob reordenação, cardinalidade e frescor/atomicidade.

Para frota, placas, nomes, filial contextual e coocorrência não são identidade.
Continuam faltando IDs estáveis escopados, colisão/reatribuição de placa,
homônimos/rename/merge/split, papéis de principal/reboques, contrato/lotação e
vigência/current/history/ausência/rekey. V2-035c local não é aceite dimensional.

As fontes de P21 são: Filiais ← FRE/MAN/CAP/FAT; Clientes ← COL/FRE/FAT;
Veículos e Motoristas ← MAN + V2-035c; Plano de Contas ← CAP; Usuários ← USER.
P16/P18/P20 e P15 aplicam-se somente às fontes/referências consumidas. P15
material requer P12; P12 operacional conserva G01/G02. P10 não herda G01,
P11 não autoriza fonte e nenhum gate herda autorização de outro.

## Evidência e correções locais

Resultados agregados e hashes dos recibos estão em [validacoes.json](validacoes.json).
Maven usou JDK17, modo offline, settings vazios próprios e cópia isolada,
preservando os artefatos de build anteriores. Enforcer, Spotless e Checkstyle
passaram. Os testes são unitários/contratos estáticos; nenhum perfil de
integração, feed ou banco foi habilitado.

O seletor Java inicial continha um nome de classe inexistente: somente os sete
XMLs realmente produzidos foram contados (26 testes). O complemento usa três
classes existentes e cobre a admissão de referências/arquivos e arquitetura.
Nenhum teste ausente foi contado como aprovado.

O verificador novo `Test-OfflinePreparationP10P21.ps1` confere escopo, requisitos,
papéis, DAG, pins e recibos. Suas contraprovas recusam autorização inventada,
mudança de contadores, dependência falsa, omissão de gate/fonte, owner inventado,
fixture promovida a identidade, perda do histórico e pin adulterado.

Erros auxiliares foram resolvidos localmente: sintaxe da primeira enumeração
AST, glob Windows passado ao `rg` e comparação PowerShell que tratava o nome
da propriedade como literal. `fleet-drift.json` conserva a classificação
inicial incorreta; `fleet-drift-confirmed.json` contém a comparação corrigida.
Ausência de `.mvn/maven.config` não exigiu criar configuração ou ler settings
locais. Nenhuma dessas falhas autoriza um efeito externo.

## Falhas históricas preservadas

- A trilha ampla continua falhando em
  `P06_SUCCESSION_HASH_docs/runbooks/continuidade-agentes.md`. O runbook e os
  manifests/selos P06 não foram alterados.
- O catálogo histórico V2-035c conserva 14 anchors; 13 ainda coincidem. O mapper
  estava fixado em `f99ad676…11384` e atualmente é `f3865ac8…04d6`.
  `Test-FrotaManifestosV2035cDecisionCatalog.ps1` permanece vermelho. Enum,
  teste, contratos e migration conservaram seus pins. O validador estático da
  vertical corrente e os testes do mapper passaram; a matriz nova separa essa
  prova do aceite histórico, sem reescrever a decisão V01/V02 nem declarar
  identidade nominal. A falha antiga não foi transformada em PASS.
- P09/G01 e a indisponibilidade de Gitleaks continuam conforme checkpoint0214;
  P09 não foi reexecutado. Falhas anteriores P07/P08 e seus ledgers permanecem
  intactos. Não houve retry de efeito desconhecido.

## Conferência final da preparação

O verificador novo passou 13 contraprovas com recibos/XMLs conferidos; o mapa
geral passou 33 etapas/48 IDs. Scanner limitado ao catálogo novo: 3 textos,
zero achado; aos validadores: 292 textos, zero achado. Isso não é Gitleaks
nem reavaliação de P09. UTF-8, diff próprio e preservação de 3.674 arquivos
preexistentes conferidos; 115 checkboxes, 67 marcados, sem delta. Os erros
auxiliares e suas correções estão em local-diagnostics.md da rodada.

## Limite e próximo trabalho

A frente local deste recorte foi esgotada. Próximo macrobloco elegível:
**intake offline do primeiro pacote sanitizado novo de G02, FEED, G05,
G03 ou G04**, limitado aos gates cobertos. Sem input novo, não repetir os
mesmos holds. Efeito externo posterior requer ordem própria, ledger prévio e
evidência sanitizada; a preparação não o libera.

Não houve produção, fonte real, leitura/rotação/revogação de segredo, banco,
deploy, job, serviço, release, paridade real, cutover, revisão humana ou aceite
dos pais V2. Nenhum segredo, token, senha, chave, certificado ou `.env` foi
solicitado ou usado como entrada. Recuperação documental: usar o diff contra
`before/`, preservando mudanças preexistentes; não restaurar todo o worktree.
