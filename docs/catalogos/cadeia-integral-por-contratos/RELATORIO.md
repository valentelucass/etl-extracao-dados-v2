# Relatório da cadeia integral por contratos

A cadeia local recebe onze fontes por arquivos explícitos e executa captura, auditoria, staging, DQ, reconciliação, cinco fatos e 19 saídas SQL pelo JAR de distribuição. Referências, relações, suplementos, janela, revisão e relógio são entradas obrigatórias. O modo completo recusa faltas; os exemplos históricos continuam em seu modo declarado.

**Escopo concluído localmente.**39/45 e67/115 permanecem com as mesmas unidades e critérios, sem reclassificação. Não houve API autenticada, leitura de segredo, execução V1, produção, deploy ou cutover. Toda prova SQL de domínio usou localhost/ETL_SISTEMA_V2_SHADOW com commit bloqueado e rollback.

## Verificação

- Unitários/contratos/arquitetura: 2080 testes em 235 classes, zero falhas/erros e 4 skips históricos.
- Integração JDBC: 481 testes em 102 classes, zero falhas/erros/skips. Reconciliação por classname#name sem remover caso da base.
- Build completo com measurement receipt, formatter, análise estática e cobertura; perfil inclui Integral*IT. V099 manteve16 regressões físicas.
- Schema102: V100 associa contexto sintético, V101 admite a release FRE vinculada e V102 prova o universo de sweep. Migrations anteriores preservadas; readback normalizado dos16 módulos V099–V102 conferido.

| Processo do JAR extraído | Exit observado/esperado | SQL exatos | Cinco fatos | Preview | Rollback |
| --- | --- | --- | --- | --- | --- |
| set-a-complete | 0/0 | 19 | sim | 33 | confirmado |
| set-b-complete | 0/0 | 19 | sim | 33 | confirmado |
| missing-user | 20/20 | 0 | não concluídos neste caso negativo | 0 | confirmado |
| oracle-value | 40/40 | 18 | sim | 0 | confirmado |
| missing-loc-target | 40/40 | 18 | sim | 0 | confirmado |

A e B usam2/24 raízes, períodos2037-08-11/2039-02-05, IDs não sequenciais e relações permutadas. USER em B percorre20+4; valores e páginas variam nas demais famílias. Os arquivos e hashes são entregues. [PROPAGACAO.md](PROPAGACAO.md) liga cada alteração ao consumidor, com presença e invariâncias legítimas.

## Frentes A–N

| Frente | Resultado local | Provas |
| --- | --- | --- |
| A — Base e reprodução | 3.349 arquivos da base0154 preservados em before; controle válido e dois RED funcionais; V099 e inventário nominal conservados. | P01, P05, P24 |
| B — Contrato integral | Versões locais fechadas, onze famílias e apoios obrigatórios, paths/pins/limites, janela, relógio e escopo explícitos; ausência recusa antes de SQL. | P02, P03 |
| C — Captura das seis famílias | COL/FRE/MAN/COT/LOC/USER usam parsers, extratores, auditoria e staging existentes. FRE compartilha release/revisão nos dois caminhos; USER mantém snapshot. | P04, P05, P06, P07, P08, P09 |
| D — Apoios explícitos | Cinco referências e nove grupos de suplementos, termos, relações e fiscal recebidos; resolução SQL em conjuntos, revisão/vigência e recusas físicas. | P07, P12, P14 |
| E — Composição pelo JAR | A2/B24 executados pelo JAR extraído: onze entradas, cinco fatos,19 saídas, contexto e rollback. Quatro expansões e Raster integrados. | P08, P10, P11, P13 |
| F — Entidades e contratos | Onze entidades/2.437 campos do inventário preservados; decisões explícitas, presença, precisão, frescor e V099 conferidos. V1 lida como comparação, sem execução. | P04, P05, P06, P07, P09, P10, P14 |
| G — Oráculos e variações | Inputs A/B com datas/IDs/valores/relações/páginas distintas e esperados autorados antes do SQL; mutações de valor, chave, cardinalidade, precisão e fato recusadas. | P13, P14, P15 |
| H — Sweep e preview | Quatro observações declaradas, recibo físico, página/owner/contagem por raiz e recusa de apply; matriz nominal33 preservada por ID/disposição/razão. | P11, P16, P17 |
| I — Falhas e recuperação | Cancelamento, deadline, lease, hash, suporte/referência e materialização; replay/revisão/isolações; estados independentes conferidos, rollback e processos reconciliados. | P18, P19, P20, P21 |
| J — Classes e arquitetura | 46 candidatos revisados; nove tipos novos com consumidor, três suportes Raster movidos byte-idênticos para testes; nove remoções anteriores preservadas, sem quota. | P22 |
| K — Regressão e pacote | Build VerifyPhysical, IDs de testes reconciliados, quatro skips históricos, análise estática/cobertura, schema102 e cinco processos do JAR extraído. | P21, P23 |
| L — Segunda revisão | Mesmo agente reviu diff/caminhos, defaults, oráculos, granularidade, resiliência, classes, seleção de testes, logs e embalagem. Falhas locais corrigidas permanecem nos recibos. | P14, P15, P18, P22, P23 |
| M — Sucessão e readback | Sucessor explícito de EntityAlignment, snapshots exatos, pointers e manifests preservados; patch/overlay aplicados e revertidos em cópia; scanner completo. A revisão final é vinculada pelo selo externo. | P24 |
| N — Entrega única | Código aplicável, runtime, inputs/oráculos, testes, tentativas, revisões e matrizes; selo/readback e continuidade.39/45 e67/115 sem alteração; parcelas externas G01–G08 explícitas. | P24 |

## Revisão e limites

As revisões estão em [REVISAO-ENTIDADES.md](REVISAO-ENTIDADES.md), [REVISAO-CLASSES.md](REVISAO-CLASSES.md) e [REVISAO-FINAL.md](REVISAO-FINAL.md). A matriz das mesmas45 unidades preserva todos os campos originais com igualdade estrutural e de tipos, acrescentando somente a revisão desta rodada. Nenhuma regra nominal foi inventada a partir da V1 ou dos exemplos.

Sweep integral usa quatro capturas e mantém os33 previews bloqueados conforme a aplicabilidade nominal; NOT_APPLICABLE não vira exclusão aplicada. Apply é recusado por Java e SQL. SQL04 vazio tem contraprova positiva histórica repetida, sem habilitar apply integral.

[ENTRADAS-E-EFEITOS-EXTERNOS.md](ENTRADAS-E-EFEITOS-EXTERNOS.md) consolida G01 credenciais, G02 governança/CI, G03 fontes reais, G04 regras/referências, G05 ambiente/provas materiais, G06 ensaio de corte, G07 corte e G08 desativação. Cada gate bloqueia somente sua parcela dependente. A prova sintética não ratifica identidade/completude real, regra fiscal pendente ou capacidade produtiva.

## Aplicação e integridade da entrega

[EXECUCAO-LOCAL.md](EXECUCAO-LOCAL.md) contém as instruções. O pacote externo reúne runtime, dois exemplos, oráculos, patch sem renames, overlay/remoções, relatórios e recibos das tentativas, incluindo falhas corrigidas. O diff foi aplicado/revertido/reaplicado sobre a base isolada3349 e o overlay teve o mesmo readback. O índice real não foi alterado.

O catálogo registra o ensaio M efetivamente executado. O **selo externo final** vincula a aplicação/scanner/validadores da revisão final e todos os membros do ZIP. Esse selo e seu readback são a autoridade do fechamento M/N; ficam fora do manifesto canônico para evitar hash circular. O predecessor0154 conserva409pins/115membros e a sucessão explícita preserva seus snapshots.
