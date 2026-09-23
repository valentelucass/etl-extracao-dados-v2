# V2-047 — Fundação offline de planejamento de bootstrap

Este catálogo fecha somente a rota `Q-BST-01`, fatia
`FUNDACAO_PLANEJAMENTO_OFFLINE` de V2-047. O resultado máximo é
`FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED`: existe um contrato verificável para
planejar, mas nenhuma fonte foi acessada e nenhum bootstrap foi executado.

O recorte existe porque V2-047 separa expressamente as dependências para planejar das dependências
para executar. V2-017, V2-019 e os contratos/identidades aplicáveis permitem planejar seis
entidades. Cada `Q-*-01`, a vertical correspondente e uma fonte histórica autorizada continuam
sendo gates independentes de cada `Q-*-02` real.

## Cobertura

A matriz possui exatamente 17 linhas:

- 11 fontes-entidade: Usuários, Coletas, Fretes, Manifestos, Cotações, Localização, Contas a Pagar,
  Faturas por Cliente, Inventário, Sinistros e Raster;
- uma linha separada para o histórico de Usuários;
- cinco fatos derivados MAT-01–MAT-05.

Usuários, Coletas, Fretes, Manifestos, Cotações e Localização estão
`PLANNING_ELIGIBLE`. Isso não significa `EXECUTION_READY`: Fretes e Localização ainda não possuem
vertical, todas as seis dependem de caracterização/fonte para execução e Manifestos conserva o hold
de Q-MAN-01. Contas a Pagar, Faturas, Inventário e Sinistros permanecem bloqueados pelas decisões
P09/P10/P08/P11; Raster permanece condicional à decisão V2-034a. Os fatos são apenas reconstruções
futuras, depois das entradas qualificadas e de V2-036.

## Contrato temporal e de checkpoint

- `T0` é a fronteira UTC de um snapshot consistente da fonte; nunca é `MAX(data)`, relógio do host
  ou valor inferido.
- `Tcut` é uma entrada futura de ensaio/corte, estritamente posterior a `T0`.
- O snapshot cobre até `T0`; o delta cobre exatamente `(T0,Tcut]`.
- O modelo interno é `[start,endExclusive)`. A tradução para bordas civis inclusivas da fonte
  permanece `Q01_REQUIRED`.
- Partições-base têm duração positiva, são adjacentes e não possuem gap ou overlap. Late data usa
  execução separada de replay/backfill com overlap limitado e explícito.
- O namespace é sempre `BOOTSTRAP`. Conclusão fora de ordem é permitida no ledger, mas nunca move o
  watermark incremental; o conjunto só fecha quando todas as partições-base e o delta estiverem
  reconciliados.

## Identidade, dependências e reconciliação

A chave de ledger preserva ambiente, `source_instance`, `tenant_scope`, entidade, modo
`BOOTSTRAP`, início e fim exclusivo. Escopos são entradas futuras obrigatórias; sentinels reservados
não são aceitos. A source key continua type-tagged e ligada ao fingerprint do contrato. O registry
preserva o canonical ID já atribuído; surrogate legado não vira canonical ID, e alias/rekey só pode
ser versionado.

Manifesto, Pick e MDF-e são atômicos, sem órfão. Manifesto→Coleta continua exclusivamente em
V2-046a. Cotações exige release `QUOTE_TARIFF` explícita no mesmo escopo. Usuários não ganha
`updatedAt` inventado e ausência nunca desativa sem prova de snapshot.

A evidência futura aceita somente contagens agregadas e digests de conjunto produzidos por
canonicalização versionada, sem emitir chave ou valor de linha. Divergência de contagem, digest,
fingerprint, escopo, partição ou terminalidade falha fechada.

## Restart e rollback

Restart só reutiliza receipt de partição reconciliada quando contrato, identidade, configuração e
estratégia continuam com os mesmos fingerprints. Falha antes da promoção desfaz a transação da
partição. Depois de promoção não há rewind destrutivo: uma nova execução evidence-bound reaplica a
partição idempotentemente. Staging, quarantine, auditoria e histórico não sofrem hard delete; down
migration genérica e alteração do legado são proibidas.

## Artefatos e limites

- `manifesto.json`: contrato fechado, vocabulários, bindings e cobertura;
- `matriz-bootstrap-v01.csv`: 17 planos/impedimentos explícitos;
- `fixtures/casos-v01.synthetic.json`: sete positivos e 27 contraprovas sintéticas;
- `manifesto.sha256`: fingerprint bruto do manifesto;
- `scripts/validation/Test-V2047BootstrapPlanningCatalog.ps1`: gate determinístico.

Esta fundação não lê rede, banco, `.env`, credencial, payload, cursor, ID ou dado real. Não executa
SQL, migration, runtime, caracterização, relação, publicação, fato, release ou cutover. O runbook
operacional desta fatia está em `docs/runbooks/v2-047-fundacao-planejamento-bootstrap-sol.md`.
