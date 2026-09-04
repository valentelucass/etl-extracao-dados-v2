# Baseline executável de portabilidade — V2-017/V2-017a

Este diretório é o baseline inicial, versionável e reproduzível de fonte → persistência → consumo. Ele classifica o que existe no legado sem copiar sua arquitetura e sem afirmar paridade produtiva. O fechamento final pertence a V2-040.

## Artefatos canônicos

- `manifesto.json`: versão, contagens e inputs externos ainda necessários.
- `inventario-artefatos.csv`: comandos, entidades, tabelas, migrations, índices, constraints, seeds, validações, procedures, views, SQL auxiliares e scripts Windows.
- `matriz-campos.csv`: ledger por campo/saída, com fonte, presença, cardinalidade, destino V2, transformação, proteção, owner, evidência, status e gate.
- `regras-negocio.csv`: as 75 regras canônicas `COL/FRE/MAN/COT/LOC/CAP/FAT/INV/SIN/USR/RAS/MAT/PUB` extraídas de `STATES.md`.
- `matriz-protecao-dados.csv`: finalidade, minimização, criptografia/chaves, masking, perfis, retenção e legal hold por classe.

O schema obrigatório e os enums são executáveis em `scripts/validation/Test-PortabilityCatalog.ps1`. Os CSVs usam UTF-8 sem BOM, LF e ordenação determinística.

## Baseline fechado

| Recorte | Quantidade |
|---|---:|
| Slots ordinais dos nove `/info` Data Export | 442 |
| Paths candidatos de `/data` com evidência local | 270 |
| Colunas das nove tabelas operacionais V1 | 459 |
| Colunas físicas de Usuários current + history | 18 |
| Colunas físicas Raster viagem + parada | 58 |
| Máximo físico sem/com Raster | 477 / 535 |
| Folhas dos cinco selection sets GraphQL inventariados | 181 |
| Outputs das 19 views ETL-owned | 673 |
| Colunas dos cinco fatos | 336 |
| Total de linhas da matriz | 2.437 |
| Regras canônicas | 75 |

As 181 folhas GraphQL são o inventário do legado, não a superfície ativa do runtime. V2-024 ativa
somente 19 folhas governadas: quatro de Usuários, cinco do sidecar de Coletas e dez do sidecar de
Fretes. A igualdade entre esse recorte, os documentos estáticos, o
`../graphql-transitorio.csv` e as linhas `GQL-*` desta matriz é um teste executável.
`field_exit_gate` no ledger é o prazo de retirada da folha; `due_gate` nesta matriz conserva as
dependências de execução/aceite e inclui V2-024. Ambos são deliberadamente distintos, e todas as
linhas continuam com `publication_blocked=YES`.

V2-033 fecha a fatia local de Usuários sem reclassificar a superfície externa: as quatro folhas
`GQL-0178..GQL-0181` e as 18 responsabilidades físicas `USR-SQL-*` agora apontam para o protocolo
limitado `stg/ctl` e para `core.usuario`/`core.usuario_history`, com estado
`IMPLEMENTED_IN_SHADOW`. `user_id` é identidade técnica type-tagged; `name` é pessoal e preserva
`ABSENT/NULL/VALUE`; `updatedAt` continua ausente e não vira frescor. USR-01..USR-04 estão
implementadas no escopo shadow. V009 acrescenta `core.v_usuario_dimension_current_v1`, uma projeção
interna current-only, set-based e sem grant; ela não altera a cardinalidade desta matriz. Os três
outputs legados `PUB-0013..PUB-0015` continuam `CONSUMER_CONTRACT_PENDING`: aliases, trim,
consumidor, publicação e cutover permanecem em V2-037, e ausência/sweep nos gates registrados.

O inventário registra ainda 37 flags (36 responsabilidades porque `--ajuda`/`--help` são aliases), 35 scripts para 34 responsabilidades de tabela, 58 migrations, quatro grupos/47 índices dedicados, seis índices `UNIQUE` embutidos, 170 constraints semanticamente distintas, 24 validações, cinco procedures, 19 views ETL-owned, dois wrappers a retirar, três SQL auxiliares e os 15 scripts de `scripts/windows`. Aliases Windows fora dessa pasta e o executor manual de banco também possuem destino explícito.

## Lacunas tratadas sem inferência

Os nomes técnicos atuais dos 442 campos de `/info` não foram preservados em artefato versionável. O gerador não associa nomes de DTO ou amostra de `/data` a esses ordinais por coincidência: mantém 442 slots estáveis `__unresolved_info_field_NNN` e registra separadamente os 270 paths candidatos de `/data`. Cada slot:

- conserva a contagem observada do template;
- possui owner-papel e dependência explícita `V2-041→V2-025d/vertical-contract`;
- tem destino tipado pendente, nunca `metadata` implícito;
- bloqueia publicação/cutover;
- só pode ser substituído por evidência sanitizada após a liberação de V2-041 e a rodada autorizada de V2-025d.

Correspondência entre path candidato de `/data` e coluna V1 por nome compartilha apenas um grupo de linhagem provisório; o status diz explicitamente que isso não é prova semântica. A matriz separa canal de observação, versão/fingerprint, campo/tipo DTO, grupo de linhagem e política de proteção, e classifica identidades, aliases, temporais e filhos com `PROMOTE/ALIAS/NORMALIZE/SPLIT` sem autorizar publicação. A classe de proteção inferida por padrão de nome também é conservadora e precisa de confirmação do data owner/compliance em V2-045. Relações N:N, tipos de runtime, moeda/unidade/escala, reducers e grãos continuam com gates explícitos. `updatedAt` de Usuários não foi solicitado; `status_branch_nickname` e `serie_nfse` continuam sem fonte comprovada. Raster permanece presente e bloqueado como condicional, mesmo se for posteriormente `NOT_APPLICABLE`.

Os outputs `Metadata` existentes em fatos/views e os catch-alls físicos são classificados `RETIRE`: o V2 substitui-os por campos tipados, presença e proveniência em `stg/recon`. Isso não remove contrato de consumidor antes do aceite em V2-037.

## Proteção e retenção

Payload bruto não é persistido por default. Os prazos de sete dias para staging bem-sucedido e 30 dias para staging falho são apenas candidatos; data owner/compliance precisa aprová-los em V2-045b. `R-STG-CANDIDATE` só classifica destinos físicos em `stg` e nunca seleciona purge por si só. Auditoria, reconciliação, quarentena e evidências pertencem à política append-only/arquivável PROT-16 e respeitam legal hold. Nenhum prazo deste catálogo autoriza purge produtivo.

## Reprodução offline

Na raiz de `etl-extracao-dados-v2`, com o repositório legado irmão disponível somente para leitura:

```powershell
pwsh -NoProfile -File scripts/validation/Build-PortabilityCatalog.ps1
pwsh -NoProfile -File scripts/validation/Test-PortabilityCatalog.ps1 -VerifyGenerated
```

O gerador usa uma allowlist de código/SQL/documentação governada. Ele não abre dashboards, `.env`, `database/config.bat`, logs, payloads, banco, rede ou credenciais. `-VerifyGenerated` gera em diretório temporário e compara hashes, sem sobrescrever o bundle canônico. O teste falha em drift de contagem, ID duplicado, decisão inválida, destino `metadata`, linha aberta sem owner/gate/bloqueio, ausência de proteção, exemplo de regra genérico e qualquer classificação literal `UNCLASSIFIED`.

## Ownership e aceite

Os papéis responsáveis estão registrados; nomes de pessoas ou times que não existem no repositório não foram inventados. `TIME_NOMINAL_NAO_INFORMADO` é um gap externo e todas as linhas afetadas bloqueiam publicação. Owners/aceitantes das 19 views e cinco fatos, política final de retenção/legal hold e decisão Raster permanecem inputs formais dos gates indicados.
