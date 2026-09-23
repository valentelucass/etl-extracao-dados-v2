# B62 — replay real de Coletas e comparação independente

10/09/2026. **COLETAS_REAL_REPLAY_VERIFIED_PARITY_GAPS_RECORDED.**
Pedido efetivo: “completar entao”, dando continuidade à correção local e à
autorização para procurar nas APIs com as credenciais já provisionadas em .env.

A correção6908 do checkpoint0056 passou agora sobre corpos reais. Duas páginas,
sete linhas físicas e quatro IDs distintos atravessaram o parser estrito,
validador de contrato, mapper e staging em memória do V2: zero quarentenas,
zero diferenças estruturais entre o JSON de entrada e o payload de staging,
zero status desconhecidos e zero linhas sem o frescor calculado pelo mapper.
Isso prova a amostra e a camada executadas; não prova equivalência temporal.

As sete linhas encontraram correspondência nas duas páginas GraphQL, contendo
40 nós. O pareamento usa sequence_code/sequenceCode como alias e depois compara
o ID canônico; o alias não foi promovido a identidade. A igualdade canônica
INTEGER/Data Export × STRING/GraphQL foi verificada por representação decimal
nesta amostra, sem inferir igualdade universal entre tags ou tenant scopes.

| Comparação por linha física pareada | Diferenças |
| --- | ---: |
| ID canônico | 0/7 |
| status | 0/7 |
| request_date/requestDate | 0/7 |
| service_date/serviceDate | 0/7 |
| finish_date/finishDate | 0/7 |
| cancellation_reason/cancellationReason | 0/7 |
| status_updated_at/statusUpdatedAt | 7/7 |
| Linha Data Export fora da amostra GraphQL | 0/7 |

**COL-TIME-01 permanece aberto:** status_updated_at não integra o contrato6908
observado e está ausente nas sete linhas aceitas. A seleção GraphQL contém
statusUpdatedAt. Não transformar ausência em NULL, copiar updated_at para esse
campo ou presumir que finish_date seja o instante de transição de status.
O frescor derivado pelo mapper permanece a regra existente; seu preenchimento
não elimina a divergência temporal. A solução para o escopo local é conservar
a ausência, detectar a diferença e impedir aceite temporal indevido. Qualquer
mudança de fonte/regra temporal precisa de equivalência demonstrada e decisão
de negócio; nenhuma tolerância foi criada para fazer a comparação passar.

## Execução e proveniência

Rodada própria target/b62-real-replay-20260910-171610: orçamento congelado de
cinco chamadas,240s,timeout30s,intervalo mínimo10s,64KiB por resposta,1.000 linhas,
duas páginas Data Export/per2 e duas páginas GraphQL/first100. Janela09/09/2026,
order sequence_code asc. Cinco chamadas executadas, todas HTTP200/curl0,
47.570ms totais. GraphQL retornou20 nós em cada página apesar de first100;
hasNextPage=true foi respeitado, sem inferir terminalidade de página curta.
O teto encerrou a rodada. Não houve retry, redirect ou renovação automática.

| Operação | HTTP | Evidência de volume |
| --- | ---: | --- |
| COLETAS_INFO | 200 | 31 campos/6 filtros; gate de metadata passou |
| COLETAS_DATA_PAGE_1 | 200 | 5 linhas/2 IDs distintos |
| COLETAS_DATA_PAGE_2_IF_NONEMPTY | 200 | 2 linhas/2 IDs distintos |
| COLETAS_GRAPHQL_PAGE_1 | 200 | 20 nós; hasNextPage=true |
| COLETAS_GRAPHQL_PAGE_2_IF_HAS_NEXT | 200 | 20 nós; hasNextPage=true |

O ledger reserva cada chamada antes do efeito e registra seu resultado depois.
Reserva vinculada aos hashes do request, da sonda e do Java test-only. Nenhum
efeito desconhecido. As duas rodadas anteriores, incluindo /data429, continuam
preservadas; esta rodada decorre do novo pedido explícito, não de retry oculto.

A sonda usa somente curl, endpoints6908 e query estática PickInput com oito
campos já selecionados no legado GraphQLQueries. URL, autorização e variáveis
seguem por stdin de curl. Os corpos UTF8 originais são mantidos em memória e
enviados por frames binários de tamanho limitado ao Java test-only. Nada de
payload, cursor, ID, hash de ID, token, header sensível ou URL de ambiente foi
gravado como evidência. O perfil público contém apenas nomes técnicos e totais.

O processo Java não é o runtime operacional. ContractTestSupport cria apenas
um control plane de laboratório; operationalBindingValidated=false permanece
explícito. O release e seu fingerprint são os correntes da ADR0043. O smoke
test e o gate prévio de metadata usam uma página vazia sintética, identificada
como tal em replay-preflight.json; ela nunca integra as páginas reais nem prova
fim de fonte. Durante o replay real, pedir página além das duas capturadas
gera CaptureLimit e invalida a travessia. Nenhuma página terminal foi inventada.

## Implementação e testes

Regra técnica R-B62-REPLAY-01, origem AGENTS §4/§5 e matriz B61: uma captura
limitada só comprova corpos efetivamente recebidos; não pode produzir snapshot
ou sucesso completo artificial. Exemplo: duas páginas não vazias resultam em
captureLimitReached=true e dataTerminalObserved=false. Responsável técnico:
manutenção B62; aceite nominal de negócio não obtido.

ColetasSourceReplay é um consumidor test-only, sem cliente de fonte ou SQL,
que reutiliza parser, gate, streamer, mapper e staging. Valida framing, UTF8,
duplicidade JSON, forma, IDs, per, metadata, campos selecionados e cursores.
Compara campos sem normalização arbitrária; ABSENT/NULL/valor continuam distintos.
A saída possui vocabulário fechado conferido novamente pela sonda PowerShell.
Preservação testada é estrutural JSON, não identidade dos bytes reserializados.

ColetasSourceReplayTest:21 testes novos, incluindo casos negativos e fixture
escalar anterior preservada. Sonda:6 guardas offline e teste de endianness.
Maven verify offline isolado,Java17/Maven3.9.14/heap512MiB: **1448 testes,0 falhas,
0 erros,4 skips**, Enforcer/Spotless/Checkstyle/arquitetura/JaCoCo verdes.
Cobertura91,64%linhas/76,50%branches. Skips preexistentes:3symlinks Windows e
Cotações opt-in. Nenhum perfil físico/SQL foi habilitado.

## Critérios e continuidade

| Critério | Resultado desta rodada |
| --- | --- |
| COL-SHAPE-01, replay da correção6908 | Comprovado na amostra real:7 linhas,4 raízes |
| Campos comuns e ID nas linhas pareadas | Sem diferenças observadas em7/7 |
| COL-TIME-01, instante de transição | Diferença7/7; não aceito |
| Paginação completa/igualdade de conjuntos | Não comprovadas: ambas as fontes permanecem não terminais |
| Representatividade, bordas, relações e oráculo nominal | Não comprovados pela amostra limitada |
| V2-012a/b/c, Q-COL-01 e Q-USR-01 | Permanecem abertos |
| V2-041 | Acesso funcional não comprova rotação ou aceite de Segurança |
| Q-MAN-01 | EXTERNAL_HOLD preservado |

Escopo de implementação e replay limitado concluído. Não há consulta pendente
nem saldo reutilizável nesta rodada. Roadmap67/115,48 pendentes,191 rotas,zero
AGORA; nenhum checkbox de paridade alterado. Sem SQL,UAC,campanha B60,runtime
operacional,produção,mudança de credencial,deploy ou cutover.

Sucessão exata de2344 arquivos: quatro deltas com snapshots,11 adições mais
manifesto. Test-Bloco62RealReplay valida hashes, histórico, orçamento, ledger,
provas e nove mutações negativas. O validador anterior resolve sua fotografia
preservada e propaga os deltas verificados aos ancestrais. Checkpoint0056,
contratos e recibos anteriores permanecem íntegros. Verificar final-checks.json
e delivery-checks.json privados para resultados dos gates finais de entrega.

Diff revisável: target/b62-real-replay-20260910-171610/diff-completo.patch.
Logs, request, ledger, java-verification, inventory/before, source-summary e
receipt ficam na mesma rodada. Rollback: reverter apenas as adições/deltas do
diff usando before, preservando a evidência executada; não reexecutar a sonda.
Próximas provas: classificar equivalência temporal com evidência independente,
ratificar janela/expectativas representativas e prover os aceites nominais da
matriz B61 antes de V2-012a. A investigação técnica não substitui esses aceites.
