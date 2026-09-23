# Coletas — captura e ligação temporal locais

COLETAS_TEMPORAL_LINK_LOCAL_VERIFIED.10/09/2026. Continuação autorizada da direção
do checkpoint0063, conforme STATES e pedido para aplicar neste chat.
A implementação captura referências GraphQL em páginas limitadas e verifica
cada par explícito com a linha6908, mantendo o timestamp e sua origem separados.

## Entrega e comportamento

- PICKS_TEMPORAL_REFERENCE, contrato2026-09-10.coletas-temporal.1,first máximo20,
  id/status/statusUpdatedAt/requestDate/pageInfo; seleção histórica preservada.
- Ledger transitório próprio, OBSERVATION_ONLY, bloqueio de promoção e saídaV2-040.
- ExtrairReferenciasTemporaisColetas: parser/gate/streamer existentes, cancelamento,
  limite por página/travessia e consumidor síncrono sem acumular páginas.
- ColetaGraphQlTemporalMapper: IDs tipados, campos ausente/null/valor preservados,
  instante ISO com offset, nanos, execução/escopo/janela/seleção/captura.
- LigarReferenciaTemporalColeta: exige correspondência explícita de identidades,
  execuções e janela, status conhecido igual e contrato compatível. Recusa
  discrepâncias; timestamp nativo válido é preservado e conflito temporal bloqueia.
- Resultado mantém a linha6908 e a referência distintas. Ausência6908 continua
  ABSENT e fallback civil permanece visível; o complemento não é enviado ao JDBC
  antigo. Não há seleção de vencedor nem cruzamento de conjuntos na JVM.

Exemplo sintético: status pending no6908 sem status_updated_at, mais referência
pending17:00-03:00, produz candidato20:00Z com origem da referência preservada.
Uma referência done para a mesma linha pending é recusada; captura e updated_at
não substituem o evento. Sem offset, a referência fica sem instante utilizável.

[ADR0045](../../adr/0045-coletas-referencia-temporal-local-com-proveniencia.md)
detalha COL-TIME-05/06/07, contrato, fundamento, limites e recuperação.
O novo catálogo muda o fingerprint agregado GraphQL. Os três documentos
históricos e seus contratos individuais não foram modificados; bindings antigos
não foram regenerados. Matriz histórica de19 campos permanece intacta.

## Evidência executada

| Verificação | Resultado |
| --- | --- |
| Primeira execução dirigida | Checkstyle recusou linha de fixture com144 caracteres; log preservado |
| Dirigido após correção | 272 testes,0 falhas,0 erros,0 skips |
| Verify completo offline | 1530 testes,0 falhas,0 erros,4 skips anteriores |
| Segunda execução completa | Verify-02 após renomear variável de cancelamento; mesmo resultado |
| Revisão final corrente | Verify-03 inclui5 revisões concorrentes de testes;1530/0/0/4 |
| Casos novos | 38:27 de semântica/ligação e11 de contrato/paginação/integração |
| Ferramentas | Java17,Maven3.9.14,heap512MiB;build isolado e offline |
| Gates | Enforcer,Spotless,Checkstyle,arquitetura e JaCoCo passaram |
| Cobertura | 91.73%linhas;76.71%branches |
| Proveniência do build | 770 fontes Java iguais byte a byte aos arquivos testados |

Os skips anteriores são3 cenários de symlink Windows e Cotações opt-in.
A primeira entrega documental foi recusada pelo scanner por7 ocorrências de
token como nome de variável CancellationToken no teste novo. Renomeada para
cancellation, sem modificar o scanner ou criar allowlist. Seu status FAIL
prevaleceu sobre exit0 incorreto do wrapper; falha, fonte e candidato anteriores
preservados em delivery-attempt1/. O checkpoint0065 permanece imutável.
O verify final é verify-03; o dirigido272 é anterior à renomeação semântica neutra.
Durante o verify-02, cinco arquivos de teste receberam mudanças fora desta
implementação. A conferência dos hashes detectou a concorrência; os arquivos
foram preservados e o verify-03 testou a revisão atual, sem sobrescrevê-los.
São ArchitectureRulesTest, RuntimeAuthorizationPolicyTest,
DataExportStagingPipelineTest, GraphQlFixtureContractTest e
FailClosedSweepPreviewKernelTest. Alterações: supressão de aviso em fixtures,
asserção equivalente, iteração explícita e remoção de código/import sem uso.
Origem CONCURRENT_CHANGE_NOT_AUTHORED_IN_THIS_DELIVERY no manifesto;
inventário e cópias em concurrent-observed/. Não são parte dos38 casos novos.
A integração sintética cobre serialidade, página/cursor, limite, falha do consumidor,
cancelamento, drift, ausência/null, identidade INTEGER/STRING, escopo/execução,
janela, divergência de status, conflito entre fontes, repetição, ordem inversa e
nanos que colidiriam no SQL. O guard recusa promoção mesmo após terminalidade.

[Resumo verificável](verification-summary.json). Logs/saídas, relatórios Surefire,
JaCoCo, hashes Java, diff e recibos: target/coletas-temporal-link-20260910/.
O diff é da rodada contra o inventário2400, não contra HEAD, preservando todo o
trabalho preexistente. Snapshots seletivos e manifesto sucessor mantêm os
manifestos/ledgers/checkpoints anteriores byte a byte; validators conferem a cadeia.

## Limites e próximo trabalho

Esta entrega conclui captura e ligação unitária locais. Ainda falta implementar
o consumidor SQL do novo tipo: staging separado, correspondências escopadas
recuperadas do SQL, cardinalidade/duplicatas/conflitos set-based e promoção
controlada. ColetaStageBatch não transporta source/tenant; o futuro chamador SQL
deve recuperar a linha no escopo associado ao binding. Fingerprint de evidência
sintética não qualifica correspondência real. Status igual não exclui ABA.

Sem API,.env,credenciais,SQL físico,DDL,UAC,novo orçamento,campanha B60/B62,
composição Main ou produção nesta fase. V004/V010 e erro51428 intactos.
Os5 acessos e7 pares SQL anteriores conservam sua prova e seus limites.
Transições reais representativas, conflitos no mesmo lote, concorrência e
bindings/aceites próprios seguem pendentes; não alegar equivalência temporal
da fonte, snapshot, rotaçãoV2-041 ou aceite nominal com testes locais.

COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 sem novo aceite.67/115,191 rotas,
zero AGORA. B63 permanece encerrado localmente, sem ampliação de roadmap.
Próximo passo técnico: integrar o complemento no SQL e qualificá-lo nas camadas
correspondentes antes de ativação. Não redescobrir a ausência por outra campanha.

Recuperação: restaurar apenas os9 deltas próprios pelos snapshots e retirar o código novo
do caminho de execução, preservando relatório/logs/checkpoints. Nenhum banco ou
dado de negócio foi alterado nesta rodada. Preservar as5 alterações concorrentes;
não revertê-las como parte desta implementação. Nada foi commitado ou publicado.
## Conciliação da documentação concorrente

A manutenção de avisos Java também atualizou STATES, trilha e RETOMADA e criou
0066-avisos-java-testes.md durante o fechamento desta entrega. Seus textos foram
preservados. O checkpoint0067-coletas-continuidade-conciliada.md identifica por
caminho/hash os dois0066 e fornece o ponteiro atual; nenhum deles foi renomeado
ou apagado. Os três documentos têm origem mista explicitada no manifesto.
As cinco alterações concorrentes de testes permanecem byte a byte como
observadas e estão cobertas por verify-03. Esta conciliação não alterou Java.
