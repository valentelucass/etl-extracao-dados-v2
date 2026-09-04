# Catálogo executável de topologia e cutover — V2-048a

Este bundle fecha o desenho offline de V2-048a. Ele não autoriza nem comprova target produtivo,
principal, grant, parada, revogação, rota, deploy, ensaio ou cutover.

## Resultado local

A evidência disponível não prova corte granular. Até que roteamento de leitura e write-fence sejam
positivamente comprovados no mesmo escopo, a unidade segura é:

- ID: `CUTOVER-DB-01`;
- granularidade: `DATABASE_WIDE`;
- topologia: banco V2 novo e dedicado;
- contratos: todos os mantidos da unidade, com condicionais aceitos como incluídos ou
  `NOT_APPLICABLE`;
- ponto de não retorno: primeira publicação V2 aceita como autoritativa em produção, nunca um
  `PUBLISHED` técnico em shadow; e
- recuperação depois do ponto de não retorno: somente roll-forward.

Implementação, bootstrap, paridade e qualificação continuam avançando por entidade. A decisão
database-wide governa apenas a troca produtiva enquanto endpoint/alias e revogação do writer não
têm granularidade material demonstrada.

## Artefatos

- `manifesto.json`: decisão, invariantes, baselines e inputs externos.
- `manifesto.sha256`: fingerprint do manifesto.
- `responsabilidades.csv`: 131 linhas derivadas do inventário V2-017 para fontes, 35 scripts de
  tabela/34 responsabilidades lógicas, cinco procedures/fatos, 19 views ETL-owned, wrappers
  retirados, SQL auxiliares, runner de banco e 55 superfícies de invocação: 38 comandos, dois
  aliases e 15 launchers Windows.
- `dag.csv`: DAG acíclico com fontes, relações, referências, dimensões, cinco fatos e 19 contratos,
  incluindo rota, boundary de escrita, tarefa dona e estado.
- `fences.csv`: sequência fail-closed de target, migrations, bootstrap, gate, freeze, write-fences,
  rota, startup, ponto de não retorno e roll-forward.

O catálogo usa apenas o baseline de portabilidade local e os manifests da V2. Ele não abre o
projeto de consumidores, legado, `.env`, banco ou rede durante geração/validação.

## Como ler as matrizes

`responsabilidades.csv` distingue três decisões:

- `INCLUDED_DATABASE_WIDE`: responsabilidade mantida/substituída/consolidada na unidade;
- `CONDITIONAL_DATABASE_WIDE`: integra a mesma unidade somente se a decisão dona a mantiver; e
- `EXCLUDED_RETIRED`/`FORBIDDEN_CROSS_DATABASE`: responsabilidade retirada, sem rota escondida.

O fence legado `DATABASE_PRINCIPAL_REVOKE_AUTHORITATIVE` significa que freeze de processo é
necessário, mas insuficiente. A prova material é a revogação, negação ou desabilitação verificável
da escrita do principal real, seguida por teste negativo. Lock de aplicação, lease, status de
scheduler ou ausência de processo não substituem esse controle.

Os nós `PUB_CONTRACT` mantêm `EXTERNAL_CONSUMER_MANIFEST_PENDING` até V2-037 e aceite externo. O
contrato de monitoramento permanece interno por default. `DIM_USUARIOS` registra separadamente a
view interna comprovada `core.v_usuario_dimension_current_v1`, sem grant; `PUB_DIM_USUARIOS`
continua pendente. Nenhum nome de `PUB_CONTRACT` é inventado nesta fase; `object name pending
V2-037` é um gate, não um objeto físico.

## Reprodução offline

Na raiz da V2:

```powershell
pwsh -NoProfile -File .\scripts\validation\Build-CutoverTopologyCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-CutoverTopologyCatalog.ps1 -VerifyGenerated
```

O teste recompõe os arquivos em um diretório temporário dentro de `target`, compara hashes, cruza a
cobertura com `docs/catalogos/portabilidade/inventario-artefatos.csv`, valida o DAG, as contagens e
a ordem dos fences. Os scripts exigem PowerShell 7 e fixam leitura/escrita UTF-8 para que hashes não
dependam do code page do host. O gate falha se o bundle:

- alegar granularidade que não foi provada;
- renomear shadow, escolher instalação in-place ou wrapper cross-database;
- inventar banco final, rota ou endpoint;
- omitir fonte, procedure/fato ou view ETL-owned;
- tratar application lock como write-fence material;
- aceitar contrato sem consumer boundary pendente; ou
- transformar publicação em shadow no ponto de não retorno.

## Inputs externos preservados

Continuam indispensáveis em V2-048b/V2-039/V2-042/V2-037:

- servidor/banco final e mecanismo real de rota;
- manifesto, reader principal e aceitante nominal dos consumidores;
- principal do writer legado e autoridade para pará-lo/revogar sua escrita;
- principals e memberships separados de migrator, bootstrap, runtime, reader e cutover;
- janela, `Tcut`, RTO/RPO, backup/restore e replay; e
- decisão/aceite de Raster.

O [ADR 0015](../../adr/0015-topologia-material-e-fences-de-cutover.md) registra a decisão. O
[runbook](../../runbooks/freeze-cutover-e-recuperacao.md) descreve as evidências e condições de
parada para o futuro ensaio, sem comandos produtivos.
