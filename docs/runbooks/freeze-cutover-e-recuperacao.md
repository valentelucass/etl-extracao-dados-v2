# Runbook de freeze, cutover e recuperação

Status: desenho offline de V2-048a. A execução do ensaio isolado pertence a V2-048b; a execução
produtiva pertence a V2-014. Ambas exigem a autorização, janela e os gates de seu ambiente. Este
documento não autoriza comando, credencial, grant, revoke, parada, deploy ou alteração produtiva.

## Objetivo e unidade

Evitar qualquer instante com dois writers autoritativos durante a troca para o banco V2 dedicado.
Enquanto não houver prova física conjunta de rota e write-fence granulares, a unidade é
`CUTOVER-DB-01`/`DATABASE_WIDE`.

Todas as responsabilidades mantidas da unidade precisam estar prontas. Responsabilidade
condicional deve ter decisão formal de inclusão ou `NOT_APPLICABLE`. Implementação isolada por
entidade não reduz a unidade material de troca.

## Papéis necessários

Nomes de pessoas ou times não são inferidos. O plano datado precisa preencher:

| Papel | Responsabilidade mínima |
|---|---|
| Owner da unidade de cutover | autorizar janela, `Tcut`, gates e ponto de não retorno |
| Owner do consumidor | fornecer manifesto/rota, executar smoke e aceitar a saída autoritativa |
| Operações | drenar/parar schedulers, provar estado e habilitar o one-shot V2 |
| DBA | construir/validar banco, aplicar ou revogar memberships e provar fences SQL |
| Segurança/identidade | fornecer principals, RBAC, revogação e trilha sanitizada |
| Owner de dados | aceitar paridade, referências, divergências e completude |
| Backup/restore | executar e medir restore/rebuild/replay dentro de RTO/RPO |

Uma pessoa pode acumular papéis somente se a governança permitir; a evidência continua separando
quem solicitou, aprovou e executou cada ação sensível.

## Pré-condições bloqueantes

Não iniciar o ensaio enquanto qualquer item abaixo estiver ausente:

- subgates de readiness que alimentam V2-014 concluídos para toda a unidade; V2-014 permanece
  aberto até o ensaio V2-048b produzir sua própria evidência;
- target final exato e allowlistado, criado pelas mesmas migrations/fingerprints aprovados;
- 18 contratos consumer-facing e cinco fatos com manifesto, consumers e aceite aplicáveis, além
  do contrato de monitoramento com escopo/grant interno explícito;
- bootstrap consistente e delta reproduzível até `Tcut`;
- principals distintos para migration/bootstrap, runtime, leitura e cutover;
- política de backup/restore, RTO/RPO e replay comprovada;
- inventário real de jobs, launchers, processos e principal SQL do writer legado;
- capacidade autorizada de revogar/negar a escrita antiga e testá-la negativamente;
- mecanismo de rota com rollback anterior ao ponto de não retorno;
- observabilidade/alertas e war room com autoridade de abortar; e
- V2-041, V2-042, V2-045b e V2-039 fechadas no alcance materialmente necessário.

Arquivo, checkbox, lock ou estado de scheduler isolado não substitui a evidência executada.

## Evidência sanitizada permitida

Registrar somente:

- timestamp, ambiente classificado, ID opaco da unidade e versão/SHA do artefato;
- contagens de objetos, grants/denies, partições, chaves divergentes e resultados;
- fingerprints aprovados de schema, contrato, configuração e rota;
- estados, reason codes, duração, RTO/RPO e resultado de smoke/replay; e
- owner-papel, aceitante nominal no sistema de governança e referência do aceite.

Não registrar segredo, principal técnico nominal no Git, conexão, endpoint, payload, cursor, ID de
negócio, documento, amostra real ou comando contendo valor sensível.

## Sequência canônica a ensaiar e posteriormente executar

V2-048b reproduz a sequência em ambiente isolado. Nele, rota, aceite e publicação autoritativa são
simulados e a etapa 9 produz somente `SIMULATED_PNR`. A execução produtiva da mesma sequência exige
V2-014 posterior e é a única capaz de produzir o ponto de não retorno real.

| Ordem | Gate | Condição para avançar | Condição de parada |
|---:|---|---|---|
| 1 | Target | banco novo, migrations, fingerprint e grants mínimos conferem | target ambíguo, drift ou permissão excessiva |
| 2 | Bootstrap | snapshot consistente e partições fechadas; delta até `Tcut` reproduzível | lacuna, replay impossível ou completude não provada |
| 3 | Readiness | subgates prévios de V2-014 cobrem toda a unidade e condicionais têm decisão | qualquer responsabilidade/consumer sem gate |
| 4 | Freeze | jobs/processos legados drenados, sem execução em voo | processo desconhecido, ciclo ativo ou telemetria inconclusiva |
| 5 | Fence legado | escrita/execução SQL antiga revogada, negada ou desabilitada; teste negativo passa | qualquer caminho antigo ainda escreve |
| 6 | Fence V2 | role mínima preparada, mas principal sem membership efetiva ou `run` deny-all; scheduler parado e invocação direta falha | DDL/DML direto, membership ativa, invocação direta aceita ou auth fail-open |
| 7 | Rota | consumidor troca database-wide e executa smoke read-only | contrato, fingerprint ou contagem divergente |
| 8 | Start V2 | autoridade `run` e scheduler one-shot são liberados coordenadamente para a primeira janela explícita | writer legado recupera escrita, ativação parcial ou plano diferente |
| 9 | Publicação | rehearsal registra `SIMULATED_PNR`; V2-014 produtivo registra a primeira saída V2 aceita como autoritativa | DQ, contrato, checkpoint, observabilidade ou aceite falha |
| 10 | Estabilização | monitorar budgets, lag, divergências e replay pelo período aprovado | limiar aprovado é cruzado |

O lock do legado e o lease da V2 podem apoiar drenagem e concorrência interna, mas não formam um
fence comum. O gate 5 permanece obrigatório. Do mesmo modo, scheduler V2 parado não impede uma
invocação direta: o gate 6 exige ausência de autoridade efetiva e teste negativo antes da ativação.

## Ponto de não retorno

O ponto de não retorno é a primeira publicação V2 aceita como autoritativa em produção depois de
freeze, fence de escrita e troca de rota. Ele precisa de receipt opaco vinculado aos fingerprints e
aceite do owner.

O marcador `SIMULATED_PNR` de V2-048b demonstra o comportamento do runbook, mas não muda ownership,
rota ou autoridade produtiva e não é ponto de não retorno.

Não contam como ponto de não retorno:

- extração, staging, promoção ou `PUBLISHED` em shadow;
- bootstrap ou replay antes da rota produtiva;
- smoke read-only;
- criação do banco, migration ou concessão preparatória de grant; e
- mera habilitação do scheduler sem publicação aceita.

## Recuperação antes do ponto de não retorno

Somente o ensaio V2-048b ou a execução V2-014 aprovados, conforme o ambiente, podem decidir retornar
ao legado congelado. A ordem conceitual é:

1. impedir nova execução/publicação V2;
2. confirmar que nenhuma publicação autoritativa V2 ocorreu;
3. voltar a rota para o snapshot legado congelado;
4. restaurar a autoridade de escrita antiga pelo owner/DBA;
5. replanejar o intervalo perdido sem lacuna nem duplicação; e
6. registrar o abort e preservar toda evidência.

Se houver dúvida sobre publicação V2 autoritativa, tratar como pós-ponto de não retorno e não
reativar o writer legado.

## Recuperação depois do ponto de não retorno

Depois da primeira publicação aceita, a estratégia é somente roll-forward:

1. manter o writer legado revogado e read-only;
2. isolar a falha V2 sem alterar evidência imutável;
3. restaurar ou reconstruir o banco V2 a partir das migrations/backup aprovados;
4. repetir o delta desde o checkpoint/evidência V2 dentro do RPO;
5. reconciliar set-based, executar DQ e republicar; e
6. reabrir a rota somente depois do smoke/aceite.

O legado pode servir leitura explicitamente defasada durante o incidente somente por decisão do
owner do consumidor. Não volta a escrever silenciosamente. Se restore/rebuild/replay não cumprir o
RPO, o cutover permanece bloqueado antes da janela.

## Critérios de aborto imediato

- target, principal, rota ou unidade diferente do plano aprovado;
- duas autoridades de escrita simultâneas ou impossibilidade de provar o fence antigo;
- migration/fingerprint/grant divergente;
- consumer manifest, aceite ou smoke ausente;
- página/partição/checkpoint incompleto, DQ parcial ou publicação ambígua;
- backup/restore/replay fora de RTO/RPO;
- observabilidade indisponível, identidade fail-open ou segredo exposto; e
- ação solicitada fora da janela/autoridade nominal.

## Evidência de encerramento

O ensaio V2-048b só encerra quando registra, de forma sanitizada, a unidade real, versões,
fingerprints, sequência dos fences, testes negativos, `Tcut`, rota simulada, `SIMULATED_PNR` ou
abort, RTO/RPO, resultado do replay e aceites. O resultado deve atualizar `STATES.md` sem incluir
valores reais de infraestrutura ou negócio. A primeira publicação produtiva e o PNR real são
evidências posteriores de V2-014.
