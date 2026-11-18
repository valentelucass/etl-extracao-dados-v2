# Checkpoint 0757 — recuperação explícita de períodos

## Identificação e objetivo

- Referência local: 08/11/2026, America/Sao_Paulo. Frente: ETL V2 → ETL_SISTEMA_V2 → dashboard.
- Anterior: [0756](0756-prontidao-e-caminho-ate-producao.md), SHA256 `A3D2A397C8A0C6E039A2DB90797DAFB0F4D9677F54B451775A8C37130213D556`.
- Objetivo final: operação de extração e consumo com cobertura íntegra, recuperação de paradas e preservação dos contratos existentes. Unidade atual: acrescentar função de recuperação por período no V2, com testes reais da camada local.
- Estado: TESTADO_NA_CAMADA offline. Produção e ensaio físico não qualificados por esta unidade.

## Autorização e limites

Mensagem efetiva do usuário: «consegue colocar essa funcao de recuperar periodo?». Autoriza a mudança específica de código, seus testes e documentação, antes da criação do banco. Requisitos confirmados anteriormente: extração na origem a cada 60 minutos, D-7..D0 conforme conferência do V1 e recuperação de todos os períodos pendentes após parada, sem perder dias antigos.

Alvo desta mudança: checkout V2 existente, com alterações preexistentes preservadas. Sem aplicação de SQL/migration, criação de banco, execução de fonte, ativação de scheduler, deploy, commit ou push. V1, dashboard, versão/serviços SQL, identidades, contas, permissões e TLS não foram modificados. Não herdar autoridade/orçamentos de campanhas históricas. Testes e indexação AST usam executor de memória comum: árvore limitada a 2 GiB, admissão atual e uma tarefa pesada por vez.

## Alterações e decisões

Inventário real de entrada: `work/initial-status0757.json`, cópias `work/before0757-*` e snapshot estrutural original com 2.106 arquivos, no chat `2026-11-08/etl-v2-sql-server-dashboard`. Não usar diff contra HEAD como se todas as mudanças fossem desta unidade.

Sete arquivos Java próprios:

- Novos: `RuntimePeriodRecovery`, `RuntimePeriodRecoveryOperation`, `RuntimePeriodRecoveryTest`.
- Alterados: `Main` (entrada explícita), `RuntimeTemporalOperation` (requisição estável por partição), `RuntimeCompositionRoot` (visibilidade de composição no pacote, mantendo autorização), `RuntimeTemporalBindingTest` (contratos e autoridade).

O executor verifica/planeja/extrai uma partição por vez e só aceita PUBLISHED lido da persistência. Planos ausentes têm identidade determinística compatível com o adaptador SQL existente. Não modifica templates, modo normal ou campos de negócio. O comando usa BACKFILL sem filtro adicional de atualização; recuperação da ocorrência já iniciada e replay terminal conservam os controles atuais. [ADR 0077](../../adr/0077-recuperacao-explicita-de-periodos.md) e [manual](../../catalogos/integracao-etl-v2-dashboard/RECUPERACAO-PERIODOS.md).

Limite concreto: comando explícito, até quatro partições conforme entrada de laboratório, somente workloads/perfis já admitidos. Resultado retorna a fronteira contínua por escopo; o chamador precisa conservá-la para o próximo lote. Supervisor automático, persistência dessa fronteira operacional e ampliação qualificada às demais entradas permanecem no P5. Não declarar recuperação produtiva automática de todas as entidades.

## Execução e evidência

Diretório: `C:\Users\suporte\Documents\Codex\2026-11-08\etl-v2-sql-server-dashboard`.

| Critério | Camada e limite | Observado | Evidência |
| --- | --- | --- | --- |
| Compilação e qualidade | Maven offline/Java17, protegido 2 GiB | 896 fontes principais + 842 de testes; Enforcer/Spotless/Checkstyle aprovados | `work/v2-tests0757.log`, `work/v2-tests-memory0757-attempt4.json` |
| Regras novas e regressões | Sete suítes com portas em memória | 78 casos, zero falhas/erros/ignorados; 26 casos novos | `outputs/testes-recuperacao0757.json` |
| Parada longa | 21 dias fechados, incluindo 29/02/2024 | Seis lotes, 21 extrações simuladas e 21 planos, sem restrição a D-7 | `recoversTwentyOneDaysAcrossLeapDayInBoundedPassesAfterRestart` |
| Retomada/falhas | Queda após plano/publicação, falha no meio e dia posterior publicado | Sem repetição indevida ou salto de cobertura; estados terminais pendentes para replay explícito | Casos nominados no recibo de testes |
| Isolamento/autoridade | Dois namespaces, recusa/troca de escopo, leituras/persistências | Efeito somente após autoridade da ocorrência; recusa não executa porta SQL | Casos de `RuntimeTemporalBindingTest` e `RuntimePeriodRecoveryTest` |
| Revisão do delta | Patch apenas contra o checkout real inicial | Conferido por `git apply --reverse --check`, sem aplicação ou reversão | `outputs/Implementacao_Recuperacao_Periodos_0757.patch` |
| Preservação | Hashes de entradas e entregas, Git e históricos | Resultado final consolidado no recibo de preservação | `outputs/verificacao-preservacao0757.json` |

Tentativas preservadas: seleção relativa do formatter não aplicou a formatação; o check global recusou os arquivos novos. A segunda tentativa falhou antes de Maven por interpretação de alternância do regex pelo shell. A terceira passou 74 casos. Após acrescentar guardas/testes de escopo e blackout, a quarta passou os 78 casos finais. Nenhuma exceção foi escondida como sucesso; ver `outputs/preparacao-testes0757.json`.

Indexação estrutural: `graphify update . --no-cluster` atingiu o limite de cinco minutos depois da extração AST, sem modificar o `graph.json` anterior (hash conferido). Atualização seletiva dos sete arquivos testados foi iniciada pelo mecanismo incremental do próprio Graphify, mantendo grafo anterior, sem force/clustering/API. Esta frase é a fotografia intermediária; o fechamento abaixo e `work/graph-memory0757.json` devem informar o resultado final. Recibo inicial: `work/graph-memory0757-attempt1.json`.

Nenhum efeito físico de origem/SQL sem confirmação nesta unidade. Processos de testes encerrados; indexação auxiliar própria observada pelo recibo delimitado. Teste em memória não qualifica fonte real, identidade/granularidade, paginação real, publicação física, paridade ou SLA.

## Retomada imediata — até três ações

1. Fechar P0: pacote SQL, segurança, alvo, contratos e dependências; não executar V002/runbooks proibidos por suposição.
2. Criar ETL_SISTEMA_V2 e qualificar recebimento/publicação não vazia e consumidor em P1–P4, preservando o Shadow e o dashboard atual.
3. Integrar ciclo de 60 minutos e fronteira durável por escopo; exercitar recuperação física, demais contratos, capacidade e corte/retorno de P5/P6.

Bloqueio externo para a função local: nenhum; implementação e testes fechados no escopo descrito. Passos físicos dependem do pacote, alvo e critérios produtivos específicos já registrados. Parar diante de recibo incerto, perfil/escopo divergente ou efeito proibido; conciliar antes de repetir. Conclusão desta unidade exige recibo de preservação e atualização estrutural final; entrada em produção exige P0–P6 e provas físicas próprias.

## Fechamento da indexação estrutural

Atualização incremental concluída às 22:51 de 08/11/2026: exit0, sem timeout, 55.002 nós/138.573 relações; as duas classes novas de produção foram encontradas no grafo. Sem clustering, force, modelo remoto ou descarte do grafo anterior; o próprio Graphify preservou cópia da versão anterior. Processo auxiliar encerrado. Pico da árvore 1.678.880.768 bytes sob limite 2.147.483.648, duração 170.399 ms. Recibo final: `work/graph-memory0757.json`; log `work/graph0757.log`. A tentativa inicial de atualização ampla permanece registrada como timeout, sem inferência de sucesso.

Entrega local: 78 testes aprovados, comando explícito, regra reutilizável, manual/ADR/estados e patch próprio. Preservação e hashes finais: `outputs/verificacao-preservacao0757.json` e `outputs/manifesto-recuperacao0757.json`. Não há processo desta unidade a manter ativo. Supervisor, fronteira automática e provas físicas continuam pendentes conforme as três próximas ações.
