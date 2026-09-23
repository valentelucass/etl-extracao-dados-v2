# 0072 — encerramento local da integração temporal de Coletas

11/09/2026. COLETAS_TEMPORAL_INTEGRATION_LOCAL_COMPLETE.
Novo escopo adotado no pedido congelado em
target/coletas-temporal-integration-20260910/request.txt; sem B64 oficial ou
reabertura do B63. Critérios e aceites continuam em STATES.md.

Anterior: 0071-coletas-integracao-temporal-schema-instalado.md.
SHA256 do predecessor: 8f71bb7d7edb3e76dad5633b4570eefbecb143f1890be6dec4e9bef3a3df3740;
0071 remete a0070, que remete a0069→0068→0067. Todos preservados.

## Autorização e preservação

Pedido explícito cobre implementação, migrations e testes sintéticos locais.
Alvo confirmado: localhost/ETL_SISTEMA_V2_SHADOW; Windows existente; opt-in Maven.
V025–V028 instaladas separadamente da IT, com reservas e confirmações nos ledgers
schema-install-01 e schema-correction-install-01. Nenhum grant, usuário, banco,
API, .env, credencial, reset, clean, commit, push, V1 ou produção alterado.
Inventário2452 e cópias anteriores em inventory-before.json/before/; snapshots
dos deltas legítimos em docs/continuidade/historico/coletas-temporal-integration/.

## Resultado e evidência

ADR0047/COL-TIME-11–14: captura sintética contratada, auditoria, staging exato,
binding, qualificação, decisão SQL, projeção tipada de laboratório e reconciliação.
O gate GraphQL observacional permanece fechado; Main e permits preservados.

| Prova executada | Resultado | Evidência no diretório próprio |
| --- | --- | --- |
| Dirigidos |60/0/0/0 | logs/directed-03.log |
| Verify/Surefire |1563/0/0/4:1559 aprovados,4 skips históricos | logs/physical-06.log, physical-06-surefire/ |
| Java/JDBC/SQL real |39/0/0/0:38 novos Coletas,1 auditoria existente | physical-06-failsafe/ |
| Duas sessões | staging53008,qualificação1222,consumo53111; segunda executa fluxo após rollback | IT de concorrência no mesmo relatório |
| Resíduos |232 execuções preexistentes,zero linhas próprias | logs/physical-06-after.log |
| Fontes |783 hashes iguais ao build | java-verification.json |
| Schema físico |001/038/057 PASS,20 objetos conferidos | logs/schema-readonly-*.log |
| Baseline |V001–V028 e hashes aplicados PASS; equivalência apenas estática | logs/baseline-02.log |
| Inputs/oráculo sintético |16 casos e15 contraprovas PASS | logs/representative-04.log |
| Input real ausente |exit2 EXTERNAL_INPUT_MISSING esperado | logs/representative-missing-01.log |
| Scanner e gates |logs de verificações individuais e recibo final | docs/catalogos/coletas-temporal-integration/RELATORIO.md |

Tentativas falhas e correções ficam no relatório; nenhum skip foi convertido em
PASS. V028 corrigiu collation observada fisicamente sem reescrever V027.
Não há efeito físico desconhecido ou processo próprio em execução. A leitura
posterior e o recibo physical-06-observed.json reconciliam a última reserva.

Entrega: relatório/matriz/comandos/inputs em docs/catalogos/coletas-temporal-integration/;
diff próprio em target/coletas-temporal-integration-20260910/diff-implementacao.patch.
Test-ColetasTemporalIntegration.ps1 verifica deltas, snapshots, fonte testada,
continuidade e manifest histórico B63. Consultar logs/integration-integrity-*.log
para o resultado efetivo da verificação final, sem inferi-lo deste checkpoint.

## Próximo passo executável

1. Revisão humana do diff/relatório; não equivale a aceite nominal já recebido.
2. Responsáveis fornecerem pacote externo descrito em INPUTS.md: oráculo
   independente, correspondência escopada, janela/casos reais e Segurança/V2-041.
3. Rodar Test-ColetasTemporalRepresentativeInputs.ps1 -InputPath no pacote protegido;
   somente depois reavaliar propósito e requisitos da coleta real condicional.

Frentes locais concluídas. Qualificação representativa real, COL-TIME-01/Q-COL-01,
V2-012a/b/c e V2-041 sem novo aceite. Nenhum checkbox alterado:67/115,191 rotas,
zero AGORA. Zero requisições da rodada condicional de até seis foi utilizado.
Instalação física em banco vazio, publicação, cutover e produção não executados.
