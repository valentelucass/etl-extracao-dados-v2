# ADR 0015 — Topologia material e fences de cutover

- Status: Aceito para o subgate local V2-048a; ratificação nominal e ensaio físico permanecem em V2-048b
- Data: 2026-08-31

## Contexto

A implementação e a paridade da V2 avançam por entidade, mas isso não demonstra que uma entidade
possa ser cortada produtivamente de forma isolada. Um corte granular só existe quando a mesma
fronteira é isolável nos dois lados:

1. o consumidor consegue rotear apenas os contratos daquela unidade; e
2. o writer legado pode ser impedido de escrever somente naquela unidade, com uma prova negativa
   independente do processo que o executa.

As evidências locais não fecham nenhuma dessas condições. O legado compartilha uma configuração e
um pool SQL, seus comandos produtivos chegam ao mesmo banco e o lock de execução não estabelece
handshake com o control plane da V2. Os 18 contratos consumer-facing ainda não possuem manifesto de
consumidor, principal de leitura ou mecanismo de troca fornecidos pelo owner; o 19º contrato,
`vw_bi_monitoramento`, permanece interno por default e também aguarda escopo/grant de V2-037. Na V2, os locks,
leases e publication pointers cercam apenas o próprio banco V2; não revogam a autoridade de escrita
do legado em outro banco.

Ao mesmo tempo, o ADR 0006 e V2-019 já sustentam localmente uma topologia limpa: banco V2 novo,
dedicado e reproduzível pelas migrations, com os schemas `ctl`, `stg`, `core`, `ref`, `mart`, `pub`
e `recon`, migrator separado e runtime sem DDL. A configuração atual continua restrita a sombra e
não contém alvo produtivo.

## Decisão

### Topologia

O alvo material continua sendo um banco V2 novo e dedicado, criado do zero pelas migrations e
validado pelo fingerprint do artefato aprovado.

- O banco local `ETL_SISTEMA_V2_SHADOW` não será renomeado ou promovido como banco produtivo.
- A V2 não será instalada in-place em `ETL_SISTEMA` por decisão implícita.
- O schema `pub` publicará somente contratos aprovados. Não haverá synonym, wrapper ou DDL/DML
  cross-database como mecanismo de transição.
- Nome final do banco, servidor, endpoint/alias de leitura e principals são inputs externos. Nenhum
  valor concreto é inventado neste ADR.

### Unidade segura

Até existir prova positiva e ensaiada de roteamento **e** write-fence por objeto/entidade, a unidade
canônica é `CUTOVER-DB-01`, com granularidade `DATABASE_WIDE`.

Isso não transforma toda implementação em big-bang. Contratos, verticais, bootstrap, paridade e
qualificação continuam fechando incrementalmente. Significa apenas que a troca produtiva espera
todas as responsabilidades mantidas da unidade estarem prontas. Uma responsabilidade condicional,
como Raster, precisa estar formalmente incluída ou aceita como `NOT_APPLICABLE`; não pode
desaparecer do inventário.

Uma futura proposta granular exige novo aceite deste ADR e evidência conjunta de:

- rota de leitura independente para todos os contratos da unidade;
- revogação/negação de escrita do principal legado exatamente no mesmo escopo;
- grants V2, scheduler, checkpoint, rollback e observabilidade no mesmo escopo;
- DAG fechado, consumer manifest, teste negativo e ensaio sem dois writers.

Comando por entidade, feature flag, scheduler parado, PID, `sp_getapplock`, lease ou publication
pointer isoladamente não satisfazem essa prova.

### Fences materiais

O fence do writer legado combina duas evidências: freeze/drenagem do processo e revogação,
negação ou desabilitação verificável da permissão SQL do principal real. O teste negativo de
DML/`EXECUTE` é obrigatório. Parar apenas o job deixa outros launchers e a invocação direta capazes
de escrever; usar apenas o lock não cerca o outro banco/processo.

O writer V2 usa principal diferente, membership mínimo e apenas procedures allowlisted. O migrator,
bootstrap, runtime, lifecycle, leitura e cutover são responsabilidades segregadas. O runtime não
recebe DDL, ownership, grant, synonym ou DML direto de domínio. Antes da ativação, o principal de
serviço não possui membership efetiva de runtime ou a autorização `run` permanece deny-all, e uma
invocação direta do JAR precisa falhar. Scheduler parado sozinho não é write-fence. A etapa de start
libera autoridade `run` e scheduler de forma coordenada, depois do fence legado e da rota. Principals
e memberships reais continuam pendentes de V2-042/V2-039/V2-048b.

A troca do consumidor é database-wide enquanto o mecanismo real de endpoint/alias/configuração não
provar outra granularidade. Os 18 contratos consumer-facing permanecem bloqueados por V2-037 e por
manifesto/aceite externo; `vw_bi_monitoramento` aguarda escopo/grant interno em V2-037 e Raster
continua condicional.

### Sequência e ponto de não retorno

A sequência material é:

1. criar e qualificar o banco V2 pelas migrations;
2. concluir bootstrap e delta até `Tcut`, sem rotear consumidores;
3. fechar os subgates de readiness que alimentam V2-014, sem declarar V2-014 concluída antes do
   ensaio V2-048b;
4. drenar/parar o writer legado;
5. revogar/negá-lo no banco e provar a falha de escrita;
6. confirmar os grants mínimos do V2, manter sua autoridade `run` efetiva negada e provar que a
   invocação direta falha;
7. trocar a rota read-only para o banco V2 e executar smoke dos contratos aprovados;
8. liberar a autoridade `run` e habilitar o scheduler V2 de forma coordenada; e
9. registrar a primeira publicação V2 aceita como autoritativa em produção.

O item 9 é o ponto de não retorno. Um estado `PUBLISHED` no ambiente de sombra é apenas evidência
técnica e não o aciona. Antes desse ponto, um ensaio aprovado pode voltar a rota para o legado
congelado e restaurar sua permissão. Depois dele, o writer legado não volta a escrever: recuperação
é roll-forward por restore/rebuild do V2 e replay desde checkpoint/evidência imutável. O legado pode
servir leitura explicitamente defasada somente com aceite do owner.

V2-048b reproduz essa sequência em ambiente isolado e registra um marcador `SIMULATED_PNR`; ele
precisa provar os dois ramos de recuperação, mas não publica saída produtiva nem aciona o ponto de
não retorno real. A execução produtiva e seu receipt autoritativo pertencem ao gate V2-014, depois
de V2-048b.

## Evidência executável

O bundle em [`docs/catalogos/cutover`](../catalogos/cutover/README.md) materializa:

- cobertura das responsabilidades de fonte, tabela, fato, contrato e das 55 superfícies de invocação
  (38 comandos, dois aliases e 15 launchers) herdadas de V2-017;
- DAG de fontes, relações, referências, fatos, dimensões e 19 contratos;
- rota, boundary de escrita, owner-tarefa e unidade de cutover por nó;
- sequência de fences, evidência sanitizada, owner-papel, gate e regra de recuperação; e
- manifesto/fingerprint que fixa `DATABASE_WIDE`, separa shadow do ponto de não retorno e mantém os
  inputs externos explícitos.

`scripts/validation/Test-CutoverTopologyCatalog.ps1` recompõe o bundle, confere sua cobertura contra
V2-017, valida o DAG acíclico e falha se aparecer alegação granular, wrapper cross-database,
endpoint concreto ou ponto de não retorno em shadow.

## Consequências

- V2-048a fecha localmente a decisão e o desenho; não prova principal, rota, backup, RTO/RPO,
  scheduler, revoke, smoke ou cutover real.
- V2-048b continua dona da ratificação nominal, dos principals/targets reais e do ensaio completo.
- V2-014 continua dona da autorização e execução produtivas, inclusive do ponto de não retorno real.
- V2-039a/V2-042/V2-045b/V2-047/V2-014 mantêm seus gates próprios; este ADR não os antecipa.
- Não há migration, grant, alias, synonym, banco, job, deploy, chamada externa ou efeito produtivo
  nesta decisão.
- Se a origem/evidência não permitir roll-forward dentro do RPO aprovado, o cutover permanece
  bloqueado.

## Alternativas rejeitadas

- **Cortar por entidade porque a CLI aceita entidade:** granularidade de comando não isola escrita
  nem consumo.
- **Usar o application lock/lease como fence entre V1 e V2:** os mecanismos não compartilham banco,
  namespace e autoridade material.
- **Parar apenas o scheduler:** outros launchers ou o JAR direto continuam possíveis.
- **Renomear o banco shadow:** transforma evidência local transitória em ambiente produtivo sem
  reconstrução, grants, backup e fingerprint aprovados.
- **Instalar in-place ou criar wrappers cross-database:** amplia o risco de dois writers e repete o
  acoplamento expressamente retirado.
- **Voltar a escrever pelo legado depois do ponto de não retorno:** cria divergência sem replay
  autoritativo e viola a estratégia de roll-forward.
