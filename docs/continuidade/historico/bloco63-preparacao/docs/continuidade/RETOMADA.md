# Retomada — B62: replay real de Coletas concluído

COLETAS_REAL_REPLAY_VERIFIED_PARITY_GAPS_RECORDED. Pedido efetivo “completar entao”,
continuando a correção e a investigação autorizada via .env. STATES é autoritativo.

[Checkpoint0057](checkpoints/0057-bloco62-replay-real-coletas.md),
SHA-256 eb8964a6cadb712ff985d8e7cd78d395c9db79280661d5bc1f3e23d540d0f296.
[Relatório](../catalogos/bloco62-real-replay/RELATORIO.md).

Escopo local de implementação e replay limitado concluído:7 linhas reais/4 raízes
passaram parser/gate/mapper/staging em memória,0 quarentena,0 diferença estrutural
de JSON. GraphQL independente40 nós;7/7 linhas pareadas,0 diferenças de ID,status,
request/service/finish/cancelamento. status_updated_at ausente6908 diverge7/7;
COL-TIME-01 não aceito. Não substituir por updated_at/finish_date sem prova.

Nova rodada própria5/5,todas HTTP200,47.570ms,teto240s,timeout30s,intervalo10s,
64KiB/resposta,1.000 linhas,2 páginas6908/per2 e2 GraphQL/first100,dia09/09/2026,
sequence_code asc. Encerrada sem retry/redirect. Orçamentos anteriores não foram
reabertos. Nada desconhecido;corpos só em memória/stdin. Sem SQL,UAC,B60,runtime
operacional,produção ou mudança de credencial. Nenhuma pergunta pendente.

Ambas as fontes não terminais. CaptureLimit não fabrica página vazia. Preflight
sintético e control plane de laboratório estão identificados;nenhum binding
operacional nominal foi validado. V2-012a/b/c,Q-COL-01,Q-USR-01,V2-041 continuam
abertos;Q-MAN-01 EXTERNAL_HOLD. Roadmap67/115,48 pendentes,191 rotas,zero AGORA.

Verify offline Java17/512MiB:1448/0/0/4,gates verdes,91,64%linhas/76,50%branches.
21 novos testes Java e6 guardas da sonda. Skips preexistentes:3symlinks,Cotações
opt-in. Sonda executada é imutável/single-use;não executá-la novamente.

Sucessão:Test-Bloco62RealReplay.ps1 -IncludePrivateEvidence -SelfTest. Quatro
snapshots,manifesto e relatório em docs/catalogos/bloco62-real-replay/. Evidências
privadas:target/b62-real-replay-20260910-171610/,request,ledger,before,build,logs,
java-verification,replay-preflight,source-summary,diff-completo.patch,receipt,
final-checks e delivery-checks. Conferir gates finais;0056 e anteriores preservados.

Próximas ações:
1. Conferir sucessão e recibos finais;correção e replay limitado estão concluídos.
2. Demonstrar semântica temporal e ratificar janela/expectativas representativas
   com evidência independente;conservar divergências e limites observados.
3. Prover aceites nominais da matriz B61/V2-041 antes de V2-012a;HTTP200 e amostra
   não substituem aceite. Não reusar teto desta rodada nem iniciar SQL/runtime.
