# Execução do STATES — resultado local

Cinco divergências locais corrigidas em cinco arquivos de produção: precisão Raster, decimais das quatro expansões, preservação de escala na admissão, memória/cancelamento dos suplementos e composição das tuplas fiscais. Nenhuma classe de produção nova, migration ou regra de negócio criada. As11famílias da base0162 permanecem integradas.

**39/45 de construção e67/115 de aceites históricos preservados.** A–N executadas no alcance local; falta nominal retém somente a parcela indicada em [G01–G08](ENTRADAS-E-EFEITOS-EXTERNOS.md). A condição anterior de encerrar por admissão foi expressamente revogada; o diagnóstico anterior continua histórico. A autoridade final M/N é o seletor FINAL-DELIVERY.json, seu selo e readback, após todos os gates.

## Resultado dos testes

- VerifyPhysical full-verify-03: 2115 unitários/238 classes e483 IT/103 classes; zero falhas/erros. 4 skips históricos, nenhum novo. Identidades XML e multiplicidades reconciliadas com2080unit/481IT da base0162.
- Formatter, contratos/arquitetura, análise estática, cobertura e SQL/JDBC passaram dentro das travas existentes. Testes novos:35unitários e2IT; repetições dirigidas não são novos testes.
- Dois conjuntos completos A2/B24 usam IDs, datas, relações, valores e paginação distintos; oráculos escritos antes do SQL. Cinco fatos e19SQL conferidos integralmente. Os valores numéricos pequenos chegam a SQL01/06/11/12; percentual Raster a SQL13; km de parada também conferido no core.
- Contraprovas independentes de SQL13 decimal errado e SQL01 série fiscal errada divergem em ambos os conjuntos, mantendo18saídas corretas em cada contraprova. Raízes/parcelas FAT com separadores permanecem distintas e séries variam por raiz. Os seis processos do JAR extraído incluem sucesso A/B e recusas de USER ausente, decimal SQL13 errado, série SQL01 errada e alvoLOC inexistente, conforme exits esperados na tabela abaixo.
- Replay/revisão, isolamento, frescor conflitante/tardio, ausência/NULL, páginas/duplicatas/reordenação, cancelamento/deadline/lease, materialização e rollback permanecem cobertos pelos testes nomeados em [P01–P24](MATRIZ-DE-PROVAS.md). V099:16regressões executadas com rollback; V099–V102 não reaplicadas.

| Processo do JAR extraído | Exit observado/esperado | SQL exatos | Fatos exatos | Rollback |
| --- | --- | --- | --- | --- |
| set-a-complete | 0/0 | 19 | true | true |
| set-b-complete | 0/0 | 19 | true | true |
| missing-user | 20/20 | 0 | false | true |
| oracle-value | 40/40 | 18 | true | true |
| oracle-fiscal | 40/40 | 18 | true | true |
| missing-loc-target | 40/40 | 18 | true | true |

Campanha ARTIFACT pelo launcher e JAR efetivamente extraídos: inspect/plan/run/status/resume/compare passaram. O pacote usa a revisão alterada e entradas com decimais de fronteira; o runtime completo continua sem fallback para fixtures.33responsabilidades permanecem preview-only, com recusas de incompletude e apply.

## Defeitos reproduzidos e correções

| ID/critério | Observado antes | Correção/prova |
| --- | --- | --- |
| STATES-FISCAL-01; V2-009, V2-030, V2-022 | Concatenação com dois-pontos produzia a mesma chave para duas tuplas válidas; ambas as massas A/B eram recusadas como duplicadas. | Array JSON interno preserva os três componentes sem alterar identidade de negócio ou SQL. Duplicata exata permanece recusada. IntegralSupportPreflightTest; IntegralDecimalPropagationIT; série por raiz em SQL01 A2/B24 e contraprova independente. RED:fiscal-identity-red-01. |
| STATES-DEC-01; V2-034, V2-025, V2-037 | DoubleNode perdeu precisão: quatro entradas válidas recusadas; 1.0000000000000001 virou1 e foi aceito. | BigDecimal exato/escala preservada no parser; leitura numérica por decimalValue com limites antes de setScale. StatesRasterDecimalTest; IntegralDecimalPropagationIT; JAR A/B. RED:raster-red-01. |
| STATES-DEC-02; V2-029, V2-030, V2-031, V2-032 | 0.00000001, -0.00000001 e0.00000000 numéricos eram recusados pela representação exponencial interna. | Regex continua nos textos; números usam decimalValue exato com escala/largura limitadas. StatesExpansionDecimalTest; IntegralDecimalPropagationIT; SQL01/06/11/12 JAR A/B. RED:expansion-red-01. |
| STATES-DEC-03; V2-018, V2-022, V2-025 | Strip de zeros transformava0E-8/1.00000000/1.000000000 em0/1/1, ocultando inclusive excesso de escala. | JsonMapper com STRIP_TRAILING_BIGDECIMAL_ZEROES desabilitado; regras de duplicata/UTF8/limites preservadas. StatesExpansionDecimalTest e recusa de precisão; mesmas entradas completas no runtime. RED:preflight-red-01. |
| STATES-SUP-01; V2-050, V2-043, V2-022 | HashSet acumulava até8192chaves/grupo; token não era consultado por linha. RED: cancelamento no32ºcheck não ocorria. | Até64chaves/lote + filtro fixo128KiB/grupo. Colisão provoca comparação exata de arquivos pinados. Token por linha e durante releitura. IntegralSupportPreflightTest, colisão Aa/BB,2/8/32/128páginas; regressão completa e JAR A/B. RED:support-red-01. |

## Memória e custo local

O primeiro ajuste limitado por lote, com releitura de todos os arquivos anteriores, passou mas levou156570ms para duas validações de128páginas. A medição motivou filtro de128KiB por grupo; ele só evita releituras. Comparação de identidade continua exata e colisões entre chaves distintas não causam recusa. O caso adversarial ainda pode exigir releitura quadrática, limitada a128arquivos; não se afirma custo linear no pior caso.

| Páginas | Linhas | Duas validações no gate final(ms) |
| --- | --- | --- |
| 2 | 128 | 190 |
| 8 | 512 | 331 |
| 32 | 2048 | 704 |
| 128 | 8192 | 2571 |

Streamers existentes foram novamente exercitados com16/256/4096páginas e contraprova de retenção. Aplicação dos suplementos continua em lotes, lookup de proveniência e agregações no SQL. Essas medições não estabelecem platô de heap, SLO ou desempenho produtivo.

## Revisão e limites de conclusão

- [45unidades/A–N](MATRIZ-STATES.md), [75regras](REVISAO-REGRAS.md), [propagação](PROPAGACAO.md) e [classes](REVISAO-CLASSES.md) ligam critérios, consumidores e testes. Os2437registros de campos preservam suas classificações;442opacos e595vínculos condicionados não são declarados campos integralmente implementados.
- Revisão do mesmo agente em duas passagens; sem revisão humana alegada. 646arquivos Java de produção,490de teste,46candidatos. Zero classes novas; nove remoções e três movimentos anteriores preservados. Políticas puras sem consumidor operacional continuam explicitamente identificadas.
- A V1 foi somente lida para comparação: BigDecimal dos DTOs Raster, fallback posicional de parada, parsers monetários e precedência fiscal. Não foram copiados fallback i+1, arredondamento, NULL silencioso ou inferência de série/identidade.
- Segunda passagem corrigiu erro de sintaxe no gerador privado de entrega antes de executá-lo; snapshot falho e parser corrigido preservados. Isso é falha da ferramenta de entrega, não defeito de negócio.
- A extensão da entrega para todos os pins da matriz revelou que pom.xml era um consumidor aceito ainda excluído pela allowlist do empacotador. A recusa foi reproduzida antes da execução; somente esse caminho adicional foi admitido, com cinco caminhos indevidos ainda recusados. Snapshot e recibos estão em delivery-proof-path-preflight-01; ZIP/readback finais conferem a distribuição efetiva.
- A checagem da matriz recusou a associação indevida de RuntimeRecoveryLocalIntegrationIT às provas atuais. Esse teste usa outro perfil, manifesto de campanha e COMMIT durável entre processos, fora desta ordem. A matriz passou a citar somente os testes executados de recuperação com estado/JDBC sintéticos e retomada SQL com rollback. O erro documental e sua correção foram preservados em proof-selection-red-01; a parcela material permanece G05, sem skip ou remoção de teste.
- support-green-02 falhou antes de qualquer teste: JVM não conseguiu reservar memória nativa. Processos reconciliados; partida-Xms64m mantendo teto-Xmx512m permitiu support-green-03 passar42testes. A evidência sanitizada foi preservada; dump de ambiente não foi lido nem distribuído.
- full-verify-01 e full-verify-02 foram interrompidos por revisões superadas, com PID/start conferidos e rollback reconciliado. Não são gates PASS nem foram encerrados por timeout. full-verify-03 valida a revisão completa final; nenhuma falha foi convertida em skip.
- A prova física dirigida fiscal-identity-sql-01 executou ambas travas Maven e produziu seus recibos incondicionais, sem a propriedade measurement. A reserva que dizia receipt foi corrigida no WORKLOG; o gate completo final inclui -Dv2.measurement.receipt=true, conforme seus argumentos registrados.
- SQL exclusivamente localhost/ETL_SISTEMA_V2_SHADOW, Windows existente, transação externa/commit de domínio bloqueado/rollback. Igualdade de agregados complementa essas travas; não prova durabilidade de COMMIT, crash ou restore. Schema102 preservado.
- Sem segredos/.env, API autenticada, V1 executada, produção, serviços/agendamentos, deploy/cutover, commit/push ou índice Git real alterado. G01–G08 permanecem por critério, consumidor e operação em [entradas externas](ENTRADAS-E-EFEITOS-EXTERNOS.md).

## Código e entrega aplicável

Base exigida:0162, inventário3465. O diff é aplicado, revertido e reaplicado em cópia; overlay também aplicado e conferido byte a byte. Scanner usa índice privado completo. O manifesto sucessor conserva snapshots exatos e todos os manifests históricos; o selo externo vincula gates, diff final, ZIP e readback sem ciclo de hashes. Recibos falhos permanecem na entrega.

Runtime/entradas/oráculos e instruções: [EXECUCAO-LOCAL.md](EXECUCAO-LOCAL.md). Seleção final: target/execucao-states-20260915-01/FINAL-DELIVERY.json. O fechamento deve continuar se selo/readback ainda não existirem; este relatório não substitui essas provas.
