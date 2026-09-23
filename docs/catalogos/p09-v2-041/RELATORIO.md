# P09 — V2-041: validação offline e intake G01

Data da rodada: 2026-09-21. Estado: `BLOQUEADO_POR_INPUT`.

## Escopo executado

- Nenhum atestado existe em `target/v2-041/sanitized-attestation.json`; o
  caminho foi conferido apenas por presença e permanece ignorado pelo Git. Seu
  conteúdo não foi lido.
- `Test-V2041RotationAttestation.ps1 -ContractOnly`: `CONTRACT_VALID`, seis
  classes, sete papéis, três perfis de scan e 19 contraprovas bloqueadas;
  `evidence=NOT_EVALUATED` e gate `EXTERNAL_HOLD`.
- `Test-P08MnDelivery.ps1`: `P08_M_N_ACEITO_NO_ESCOPO_LOCAL`, dez recibos,
  pacote de 724 membros, hashes do pacote/readback conferidos e zero chamadas
  de fonte. Isto preserva a evidência P08, sem convertê-la em aceite externo.
- O autoteste inicial do scanner passou 17 casos. A varredura integral revelou
  localmente uma falha de decodificação UTF-8 de caminho Unicode, classificada
  como `MISSING_CANDIDATE` apesar de o arquivo existir. O scanner passou a
  declarar UTF-8 para stdout/stderr do processo Git, e recebeu contraprova para
  caminho Unicode. O autoteste final passou 18 casos.
- A varredura integral final passou: 3.680 candidatos, 3.671 textos, um
  binário verificado, zero arquivo não-texto inesperado, zero texto excessivo e
  zero achado. Sua saída sanitizada está em `target/p09-v2-041/`.

## Limites e bloqueio

Não houve leitura de `.env`, valores de segredo, chamada remota, rotação,
revogação, recarga, health check, banco, DDL/DML, job, serviço, deploy,
produção, paridade real ou cutover. Não havia processo próprio remanescente ao
fim das observações.

O binário `gitleaks` não está disponível localmente. Por isso as varreduras
canônicas Gitleaks de worktree e histórico não foram executadas; não houve
download, instalação ou substituição manual do scanner.

Os validadores de intake, autoteste do scanner e preparação da trilha passaram.
O validador amplo da trilha permaneceu vermelho em
`P06_SUCCESSION_HASH_docs/runbooks/continuidade-agentes.md`; o runbook corrente
diverge do hash `after` preservado em P06 e não foi modificado por P09. O
manifesto e o selo históricos permanecem inalterados.

O bloqueio externo real é **G01**, de **Segurança e Operações**: falta o
atestado sanitizado operacional de V2-041, com referência restrita autenticada
pelos responsáveis, rotação/invalidação das seis classes, consumidores vivos,
continuidade do writer legado, rollback e resultados dos três scans. O recibo
deve ser disponibilizado exclusivamente no caminho ignorado previsto e só pode
alcançar `STRUCTURALLY_VALID_UNVERIFIED`; autenticidade e aceite nominal continuam
humanos. A disponibilização do Gitleaks versionado/aprovado também é pré-requisito
local para completar os dois scans declarados no recibo.

Os contadores canônicos permanecem 39/45 e 67/115. P09 não autoriza V2-025d,
rede, release, deploy ou qualquer efeito posterior.
