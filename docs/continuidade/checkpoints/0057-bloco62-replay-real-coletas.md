# Checkpoint0057 — B62: replay real de Coletas

10/09/2026. Anterior:0056-bloco62-correcao-local-coletas.md,
SHA-256 a2b0aaa77bb9f38e73f8526fc414863b6e36b6c821f06cf07d1986ffe9f7ab02.

**COLETAS_REAL_REPLAY_VERIFIED_PARITY_GAPS_RECORDED.** Pedido efetivo:
“completar entao”, após autorizar investigar as APIs com .env. Escopo local
de correção e replay limitado concluído. Nenhuma pergunta pendente ao usuário.
Autorização: manutenção/testes e consultas read-only necessárias6908/GraphQL.
Sem SQL,UAC,B60,runtime operacional,produção,alteração de credencial ou cutover.

Rodada própria target/b62-real-replay-20260910-171610: cinco chamadas reservadas
e observadas,todas HTTP200/curl0,47.570ms. Teto5/240s,timeout30s,intervalo10s,
64KiB/resposta,1.000 linhas,duas páginas Data Export/per2,duas GraphQL/first100,
dia09/09/2026,sequence_code asc. Sem retry/redirect. Orçamento encerrado; não
reusar a rodada nem renovar saldos anteriores. Nenhum efeito desconhecido.

| Prova | Observado |
| --- | --- |
| Metadata6908 | 31 campos/6 filtros; gate aceitou |
| Corpos reais → parser/gate/mapper/staging em memória | 7 linhas,4 raízes,0 quarentena,0 diferença estrutural de payload |
| GraphQL independente | 40 nós/2 páginas;40 IDs distintos;hasNextPage=true |
| Pareamento por alias e comparação do ID | 7/7 linhas pareadas;0 diferença de ID |
| Status/request/service/finish/cancelamento | 0 diferenças por campo nas7 linhas |
| status_updated_at/statusUpdatedAt | 7 diferenças; ausência Data Export preservada |
| Terminalidade/snapshot | Não comprovados; nenhum terminal inventado |
| Verify offline Java17/512MiB | 1448/0/0/4;gates verdes;91,64%linhas/76,50%branches |
| Novas regressões | 21 testes Java;6 guardas PowerShell e framing endian |

Corpos reais somente em memória/stdin binário. Persistidos apenas totais e nomes
técnicos. Java test-only reutiliza componentes reais, mas o control plane é de
laboratório: operationalBindingValidated=false. Smoke vazio sintético serve ao
preflight, nunca à terminalidade real. Preservação é estrutural JSON, não bytes
reserializados. Nenhum payload,ID,cursor ou segredo em arquivo/log/fixture.

COL-SHAPE-01 comprovado para a amostra. COL-TIME-01 permanece aberto: não substituir
status_updated_at por updated_at/finish_date sem equivalência demonstrada.
V2-012a/b/c,Q-COL-01,Q-USR-01,V2-041 abertos;Q-MAN-01 EXTERNAL_HOLD. Sem aceite
nominal de representatividade/Segurança. Roadmap67/115,48 pendentes,191 rotas,
zero AGORA; nenhum checkbox promovido.

Inventário inicial2344 em inventory/before; quatro deltas com snapshots e11
adições mais manifesto. Anteriores preservados. Relatório,source-summary e
manifesto: docs/catalogos/bloco62-real-replay/. Validador próprio sucessor
Test-Bloco62RealReplay.ps1; o anterior propaga sua cadeia histórica. Logs completos,
request,ledger,replay-preflight,java-verification,diff,receipt,final-checks e
delivery-checks ficam na rodada privada. Conferir os gates finais antes de presumir
entrega íntegra. Rollback pelos deltas/before, sem apagar evidências ou repetir API.

Próximas ações:
1. Conferir Test-Bloco62RealReplay.ps1 -IncludePrivateEvidence -SelfTest e os
   recibos finais; implementação e replay limitado estão concluídos.
2. Resolver a semântica temporal e ratificar janela/expectativas representativas
   com evidência independente, mantendo a ausência e as divergências explícitas.
3. Obter os aceites nominais próprios da matriz B61/V2-041 antes de V2-012a;
   não usar HTTP200 ou esta amostra como substituto de aprovação.
