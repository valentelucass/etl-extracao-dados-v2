# B62 — correção local executável de Coletas

**COLETAS_LOCAL_FIX_VERIFIED_REAL_REPLAY_PENDING**, 10/09/2026.

Foi corrigida a incompatibilidade estrutural que impedia a fonte Coletas de
atravessar o contrato local. A composição seleciona um release próprio de6908
com ROOT_ARRAY, 31 campos e seis filtros; a configuração HTTP deriva forma e
chave desse release. Metadados sem tipo declarado permanecem UNDECLARED e
`by_updated_at` não recebe o prefixo do parâmetro `search[scopes]`.

O fluxo HTTP loopback → parser estrito → metadata/gate → paginação → mapper →
staging em memória passou. A fixture autoral mantém cinco linhas físicas para
dois IDs com per2, incluindo candidato Manifesto distinto entre observações,
nulo, texto vazio, número como texto, decimal e booleano. Não há deduplicação
indevida, criação de relação ou substituição de frescor por updated_at.

Há tipos estritos para os campos interpretados e a chave. Os 21 campos sem
consumidor de negócio são preservados como escalares, conforme política local
explícita da [ADR0043](../../adr/0043-coletas-contrato-de-captura-e-forma-por-release.md).
Isso não comprova tipos do fornecedor. Containers, caminhos desconhecidos,
chave ausente/nula/textual, drift, JSON duplicado e excesso de IDs são recusados
antes do staging. Falta de terminalidade invalida a ocorrência após staging
parcial; limite de registros impede entregar a página excedente.

O catálogo V2-025a, o consumidor B58 e os recursos de laboratório permanecem
históricos. A seleção corrente tem novo fingerprint de contrato no binding e
no plano. A fixture dos handlers suporta separadamente a forma corrente e a
histórica; os recursos anteriores não foram reescritos. Fretes não recebeu
novo contrato. Não houve modificação de schema ou regra de promoção.

## Evidência executada

| Critério local | Resultado | Evidência |
| --- | --- | --- |
| Metadados reais de6908 | HTTP200,31 campos sem tipo declarado,6 filtros | diagnostic-summary.json; ledger privado |
| Dados adicionais na ordenação corrente | HTTP429; rodada encerrada, sem retry | mesmo ledger,2 reservas/2 resultados |
| Regressão sobre composição anterior | 1 falha esperada: `/data` em vez de `$` | directed-red-04.log; red-reports/ |
| Parser/gate/mapper/staging e adversariais | 21 testes novos passaram | DataExportColetasContractTest |
| Seleção e handlers oficiais | 5 testes passaram,1 novo | RuntimeOperationalExecutionTest |
| Verify completo offline | 1427 testes,0 falhas,0 erros,4 skips | verify-01.log; java-verification.json |
| Qualidade Java | Enforcer,Spotless,Checkstyle,arquitetura,JaCoCo passaram | 91,63% linhas;76,52% branches |
| Validadores correntes offline | 6 checks passaram | runtime-local.log e result.json referenciado |

Build isolado Java17/Maven3.9.14, heap512MiB, sem clean no target canônico.
Skips: três symlinks condicionais Windows e comando Cotações opt-in desabilitado.
As três primeiras tentativas dirigidas falharam por autoria do teste (EmptyBlock,
nome de método e close com InterruptedException); foram corrigidas, sem reduzir
gates. Todos os logs permanecem. O receipt de medição habilitado é sintético.

## Limites, continuidade e entrega

O pedido efetivo foi implementar a solução, após o usuário autorizar procurar
nas APIs com .env. A nova rodada foi própria: só6908, /info e uma página per2,
dia09/09/2026, order sequence_code asc, teto2, duração90s, timeout30s, transporte
10MiB e dados64KiB, serial, sem retry/redirect. A segunda resposta429 encerrou
a rodada. Não houve uma nova rodada de verificação depois dela. O orçamento
anterior B62 de4/4 e o B60 continuam encerrados. Credenciais só em memória,
última definição não vazia do .env, sem editar/exibir valores.

A correção local está comprovada; a execução da revisão sobre uma página real
e a paridade representativa continuam pendentes. Não se marca COL-SHAPE-01
validado em fonte real. O teste HTTP é sintético, /info real não é replay dos
dados, e HTTP429 não demonstra ordenação ou semântica temporal. V2-012a/b/c,
Q-COL-01/Q-USR-01 e V2-041 continuam abertos. Nenhuma rotação, invalidação,
exclusão, snapshot ou completude foi inferida. Q-MAN-01 mantém EXTERNAL_HOLD.

Sem SQL, UAC, campanha física, runtime operacional, escrita produtiva/remota,
alteração de credencial, fornecedor, deploy ou cutover além das leituras
explicitamente delimitadas acima. Roadmap67/115,48 pendentes,191 rotas,zero AGORA.

Evidência privada: `target/b62-coletas-fix-20260910-163750/`, com inventário de
2324 arquivos e before/, plano, sonda, ledger, logs, build isolado, regressão,
diff-completo.patch e recibo. `final-checks.json`/`delivery-checks.json` registram
os gates finais de sucessão, continuidade, scanner e revisão do diff.
O manifesto desta pasta e `Test-Bloco62ColetasFix.ps1` conferem sucessão exata;
dez snapshots preservam revisões anteriores. Os manifests/ledgers antigos não
foram reescritos. Checkpoint0056 sucede0055.

Próximas provas externas: replay limitado desta revisão após tratamento do429
e dentro de nova rodada formalmente delimitada; depois comparação com oráculo
independente e janela representativa por rota, conforme matriz B61. A engenharia
local desta incompatibilidade não depende de nova informação do usuário.
