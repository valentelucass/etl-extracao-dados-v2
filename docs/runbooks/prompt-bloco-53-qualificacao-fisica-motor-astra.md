# Prompt para outro chat — proposta de Bloco 53/P02Q

**Abertura recomendada após a ampliação pedida pelo owner:**
[pacote integrado de motor, identidade e autorização](prompt-bloco-53-pacote-integrado-runtime-astra.md).
Este arquivo permanece como especificação detalhada da frente física P02Q; o prompt
ampliado define as demais frentes e suas condições próprias de aceite.

Preparado em 07/09/2026 após o fechamento local do Bloco 52/P02R. Este arquivo não
inicia o bloco, não concede autorização por estar no repositório e não registra prova
física. A execução depende de o usuário adotar expressamente o escopo abaixo no novo
chat. A numeração deve ser reconferida se outro trabalho ocorrer antes.

## Mensagem para enviar no novo chat

```text
Trabalhe no projeto:
C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2

Leia e execute integralmente:
docs/runbooks/prompt-bloco-53-qualificacao-fisica-motor-astra.md

Repriorizo o Bloco 53/P02Q como qualificação física local do motor Coletas/Fretes.
Autorizo o escopo físico delimitado na seção 2: somente SQL Server já disponível
em localhost/ETL_SISTEMA_V2_SHADOW, autenticação Windows existente, evolução
versionada do schema e commits de fixtures sintéticas isoladas. Isso inclui a
transição histórica apenas se comprovadamente vazia e compatível, conforme 2.3.
Estou ciente de que o schema e os dados sintéticos confirmados permanecerão no
banco; não autorizo apagá-los nem recriar o banco para limpeza.

Conclua o pacote integrado A+B+C+D+E com código e provas executadas.
Preserve alterações preexistentes, não contorne os gates operacionais e marque
no STATES.md somente os aceites efetivamente comprovados. Não encerre apenas
com planejamento, documentação, interfaces ou um harness que nunca foi exercitado,
salvo impedimento externo concreto documentado após concluir o trabalho independente.
```

## 1. Objetivo e leitura obrigatória

Execute um pacote grande e coeso usando a preferência registrada pelo owner por Astra:
**Bloco 53/P02Q — V2-022/QUALIFICACAO_FISICA_LOCAL**. O resultado é qualificar e corrigir
o motor existente contra SQL Server físico, mantendo a fonte inteiramente sintética.
Não criar outra implementação do ETL nem outra máquina de estados apenas para testes.

Leia integralmente AGENTS.md, STATES.md, ../CONTEXTO_GLOBAL.md e
docs/runbooks/trilha-de-chats-gpt-5-6.md. Leia também os ADRs 0009, 0010, 0011, 0012,
0014, 0022, 0032, 0033 e 0034; database/README.md; os runbooks
v2-022-motor-local-coletas-fretes.md e v2-022-recuperacao-duravel-local.md.
Inspecione os consumidores, migrations, manifests, validators e testes afetados.
Distinga leituras integrais, trechos consultados e evidências históricas.

O ponto de partida registrado é P02R local concluído: 1023 testes, zero falhas/erros,
quatro skips esperados, 49 testes focados e oito processos Java filhos sobre persistência
sintética. V015, baseline e exercícios 048/049 estão preparados, sem prova física.
São 113 checkboxes, 59 concluídos, 54 pendentes e 196 fatias abertas. Reconfira esses
números no início; não substitua avanços posteriores pela fotografia deste prompt.

**P02Q não é o fechamento de G08.** A rota G08/V2-022b exige V2-042b/V2-042c:
inputs externos de identidade, adapter positivo e operação pelo JAR oficial. O handoff
de B52 usou G08 como direção para a qualificação física; este prompt distingue a
subentrega de laboratório desses gates. A repriorização do usuário autoriza derivar
P02Q sob V2-022 e ajustar seletor/trilha; não autoriza declarar G08 elegível ou concluído.

Preserve RuntimeDispatcher, RuntimeExecutionSession, LocalColetasFretesRuntime,
DataExportRuntimeWorkload, os casos de uso de Coletas/Fretes, os guards e permits reais,
os adapters JDBC e ctl.usp_runtime_recovery como caminho concreto. Faça um resumo
breve das invariantes e da matriz de aceite e prossiga imediatamente à implementação.

## 2. Escopo físico proposto para adoção expressa pelo usuário

### 2.1. Alvo, identidade e exclusões

- Somente a instância SQL Server **já disponível** em `localhost` e o banco **já
  existente** `ETL_SISTEMA_V2_SHADOW`. `master` serve exclusivamente para preflight
  read-only do alvo. Cada conexão de trabalho deve confirmar o banco efetivo.
- Autenticação Windows integrada já provisionada, sem senha, novo login, service
  account, membership operacional ou concessão operacional. Não ler `.env` ou credencial ESL.
  Não imprimir connection string, identidade Windows, payload ou valores sensíveis.
- Não criar, excluir, renomear, restaurar ou recriar banco. Não conectar a
  ETL_SISTEMA, esl_cloud, DASHBOARDS, DASHBOARDS_DEV ou qualquer host remoto.
- Não iniciar/reiniciar SQL Server, serviço, container ou processo do usuário. Somente
  processos Java/SQLCMD filhos criados pelo próprio harness podem ser encerrados,
  com PID e ownership conferidos, prazo finito e janela oculta quando aplicável.
- Sem Data Export/GraphQL externos, rede externa, download de dependências, alterações
  em dashboards/legado, agendamento, deploy, cutover, Git commit ou push. Use dependências
  já presentes no cache; ausência de driver/DLL é impedimento de execução, não autorização
  para buscar um binário em outra fonte ou mudar autenticação.

### 2.2. Escritas autorizadas e limites

- Preparar, corrigir, validar e aplicar migrations V2 pertinentes ao motor/recuperação,
  com baseline/manifests sincronizados. O harness Java não aplica DDL durante execução.
- Executar fixtures de Coletas/Fretes com fontes, tenants, chaves e policies DQ
  inequivocamente sintéticos, isolados por campanha. As estruturas prévias das outras
  verticais podem nascer da baseline; não há carga nem implementação nova dessas verticais.
- Os casos de restart podem confirmar transações reais de staging, auditoria, selo,
  candidate set, DQ, core, reconciliação e publicação, incluindo frontier apenas no
  namespace incremental sintético. Esta é uma ampliação explícita em relação à IT
  histórica de auditoria rollback-only; não alterar essa IT para permitir commit.
- Por campanha: até 128 ocorrências, 2048 linhas de entrada sintética, 512 páginas
  auditadas e 65.536 linhas derivadas, incluindo auditoria/history. Até quatro conexões
  físicas e dois processos Java filhos simultâneos. Comandos SQL com prazo de até
  30 segundos, filhos com prazo de até 60 segundos e campanha física de até 15 minutos.
  Barreira e polling também têm prazo; não usar espera infinita ou retry cego.
- O runner deve admitir a massa antes da escrita, contabilizar o consumo e impedir que
  reruns ultrapassem o teto cumulativo dessa entrega. Reservar espaço para cenários de
  correção; não criar novas campanhas ilimitadas para escapar do orçamento. Ajustar um
  teto exige novo escopo explícito, não alteração silenciosa do gate.
- Fixtures de policy são apenas políticas locais sintéticas, vinculadas ao namespace;
  não inventam owners, ratificação ou referências de negócio reais. Os thresholds de
  produção e o encapsulamento dos permits permanecem protegidos.
- Não fazer DELETE/TRUNCATE ou desabilitar constraint/trigger para preparar, adulterar
  ou limpar casos. Construir negativos por fixtures válidas incompletas, comandos
  permitidos e tentativas rejeitadas. Os dados já confirmados ficam preservados como
  evidência sintética; rollback só desfaz transações que ainda não foram confirmadas.

### 2.3. Preflight e evolução do schema

Antes de qualquer escrita, produza inventário sanitizado do alvo: existência, versão
do motor/compatibilidade, topologia, migrations/checksums quando houver histórico,
permissões efetivas necessárias e contagens agregadas exatas. Não confie somente em
`sys.partitions` para comprovar ausência de linhas. Registre a decisão e o caminho SQL
exato escolhido antes de executá-lo; a autorização acima dispensa nova confirmação
para o caminho que satisfaça integralmente essas condições.

Se o banco ainda for a prova histórica V001–V003 **vazia**, o caminho autorizado pode
usar `database/transition/001_reset_historical_shadow_for_schema_foundation.sql`,
somente após conferir todas as suas recusas de topologia, objetos, roles e ausência
de dados. Essa transição remove estruturas históricas vazias; não é uma limpeza geral.
Qualifique primeiro os caminhos rollback-only existentes. Depois, envolva a transição,
as migrations e os validators estruturais em unidade de instalação controlada: erro
deve reverter a instalação, nunca deixar metade da topologia confirmada.

Se o alvo já tiver fundação V2 compatível, preserve todos os objetos/dados e aplique
apenas evoluções pendentes comprovadas. Não rodar o reset histórico nessa topologia.
Não fabricar `flyway_schema_history`, usar `repair` para esconder drift, reescrever
migration aplicada ou aplicar baseline sobre objetos existentes. Sem histórico, a
comparação de manifests/schema deve demonstrar a compatibilidade antes da decisão.

Os principals internos sem login, roles e grants estritamente estruturais das migrations
já aprovadas podem acompanhar a fundação; isso não permite criar identidade operacional,
novos grants para `ctl.usp_runtime_recovery` ou ampliar a allowlist de `v2_runtime`.
Principals sintéticos sem login e membership de teste já previstos nos validators
existentes só podem existir em transações integralmente revertidas; não persistir
essas fixtures de privilégio nem usá-las para abrir a recuperação operacional.
Execute o laboratório com o contexto Windows já autorizado e declare seu nível de
privilégio. Sucesso como owner não prova least privilege nem autorização operacional.

Se houver alvo ausente, serviço parado, dado/objeto inesperado, drift, permissão
insuficiente ou outro impedimento real, recuse só a operação dependente. Conclua código,
testes locais, correções e preparação independentes; documente o bloqueio concreto e
a ação mínima necessária. Não alterar o ambiente para tornar o preflight artificialmente
verde. Não marcar qualificação física completa sem executá-la.

## 3. Pacote integrado obrigatório A+B+C+D+E

### A. Harness físico seguro e composição real

Implemente um perfil Maven opt-in específico, por exemplo
`runtime-recovery-local-integration`, mais flag explícita
`-Druntime.recovery.local.integration.enabled=true`. Perfil, flag, alvo validado e
manifesto da campanha devem ser obrigatórios antes da primeira conexão/escrita.
Sem opt-in não há conexão. Com opt-in solicitado, configuração ausente/incorreta
deve falhar, não produzir BUILD SUCCESS com todas as ITs skipped.

Preserve `shadow-local-integration` e sua conexão que bloqueia commit. Reutilize a
validação estrita do alvo e a DLL Microsoft já disponível, sem `PATH` global e sem
relaxar parsing de host/database/autenticação ou aceitar properties duplicadas perigosas.
Separe preparação administrativa de schema/fixtures da composição Java sob teste.

Integre DataSource físico, control plane, auditoria, staging, DQ, promoção e recuperação
ao motor real. Somente a origem e a injeção controlada de falhas podem ser fixtures.
Não substituir SELECT/commit/rollback por respostas de RuntimeSyntheticJdbc. Não abrir
fábrica de permit, verifier permissivo ou caminho operacional novo no Main/composition root.

O novo runner precisa oferecer preflight, execução limitada e resumo verificável por
cenário. Logs e temporários ficam em diretório próprio sob target; cada handle/conexão/
statement/executor/filho tem dono e fechamento comprovado inclusive em exceção.

### B. Qualificação física do protocolo SQL e correções

Compile e execute V015 e seus consumidores reais no alvo aprovado. Exercite
`048_validate_runtime_durable_recovery.sql` e
`049_exercise_runtime_durable_recovery_rollback.sql`, corrigindo lacunas encontradas.
049 nasce focado em Coletas e possui preparação histórica: não presuma cobertura
de Fretes nem compatibilidade com um banco já modernizado. Acrescente a prova de
Fretes e adapte runners por topologia sem apagar dados ou enfraquecer recusas.

Prove o caminho incremental de migrations e a baseline por comparação estrutural
executada em transações independentes revertidas quando a topologia permitir.
Planeje essa prova antes da instalação persistente. Um grep de arquivos ou o sucesso
de dois scripts sem comparar os resultados não prova equivalência. Preserve o estado
anterior de cada exercício rollback-only, incluindo schema e contagens relevantes.

Verifique especialmente SQL dinâmico/batches, escopo de variáveis, triggers, permissões,
result sets intermediários, ausência de INSERT EXEC aninhado, `XACT_ABORT`,
`@@TRANCOUNT`/`XACT_STATE()`, collation BIN2, wire Java/SQL UTF-16, hashes e UTC em
milissegundos, inclusive `.000`. Asserts devem usar estado persistido e não só PRINT.

V001–V014 permanecem intactas. V015 pode receber correção somente enquanto comprovadamente
não aplicada de forma persistente; após aplicação, criar a próxima migration disponível
e atualizar baseline/manifests/checksums. Não escolher V016 sem conferir concorrência
de trabalho. Preservar COL-03 e a prova de que a evolução do entrypoint tipado é aditiva.

### C. Recuperação durável entre JVMs sobre dados SQL confirmados

O escritor deve atravessar guard, mapper, auditoria e staging reais, fechar o selo
no ponto correto e produzir o estado SQL pela execução normal. O processo termina;
um leitor em **outra JVM**, com outro DataSource/conexões/dispatcher/sessões, recupera
somente a partir do SQL e da expectativa imutável da campanha.

Arquivo de campanha pode guardar configuração sintética e correlação técnica; não
pode transportar selo aprovado, recibo, estado autoritativo, guard ou permit serializado.
Não simular restart abrindo outro objeto dentro da mesma JVM.

Para cada vertical, provar pelo menos:

| Cenário | Evidência exigida |
| --- | --- |
| Apply confirmou; ack perdido; escritor terminou | PUBLISHED e recibo durável exato na nova JVM; zero fetch e nenhum efeito/publicação/frontier duplicado |
| Prepare/selo completo persistido; escritor terminou antes do apply | Continuação elegível sob lease ainda válida, revalidação de contrato/DQ e um único apply |
| Falha antes do commit da promoção | Nenhum efeito parcial em core/recon/publicação; recuperação decide pelo estado realmente persistido |
| Extração parcial, terminal ou lease perdida | Recusa tipada, sem reinício na mesma ocorrência ou aquisição furtiva de lease |
| Replay explícito e nova tentativa | Origem correta, tentativa separada e replay sem avanço da fronteira incremental |

Use instrumentação test-only na fronteira JDBC para suprimir um ack **depois da
confirmação física** e antes da entrega ao consumidor; observação SQL independente
deve comprovar a posição da falha. Diferencie perda de resposta provocada no cliente
de queda real de transporte. Encerre somente filhos próprios. Esta prova não implica
restart do serviço SQL Server ou recuperação de desastre.

O recibo de Coletas deve conservar as onze propriedades tipadas, inclusive caso em
que COL-03 diverge das contagens genéricas; Fretes deve conservar seu recibo comum.
Confirmação histórica íntegra continua possível após mudança de pointer/core/policy.
Coletas histórica sem recibo tipado continua EVIDENCE_MISSING: não reconstruir números.

### D. Concorrência, fencing, cancelamento e matriz adversarial

Execute sessões SQL físicas concorrentes atravessando as procedures reais. Um probe
isolado de `sp_getapplock` não comprova concorrência do protocolo de publicação.
Use barreiras explícitas e deadlines, sem depender de sleeps longos para ordenar eventos.

- Duas recuperações da mesma ocorrência e corrida entre apply normal e RESUME:
  apenas um conjunto de efeitos; o segundo caller confirma o resultado ou recebe
  razão coerente, sem duplicação de eventos, aplicação, watermark ou recibo.
- Namespaces independentes não se contaminam. Dependente falha/bloqueia sem apagar
  publicação de workload independente; o agregado pós-recuperação usa os resultados reais.
- Lease expira ou estado muda entre READ e RESUME: revisão/fence rejeita a continuação.
  O relógio é o SQL; não manipular o relógio do host nem renovar lease pela recuperação.
- Cancelamento/query timeout durante espera por lock e durante operação autorizada:
  attention/rollback, conexão encerrada e lock liberado no prazo. Confirmar ausência
  de transação órfã observável nas próprias sessões e ausência de publicação parcial.
- Commit seguido de cancelamento/falha de leitura: readback limitado pode confirmar
  PUBLISHED; indisponibilidade continua RECOVERY_REQUIRED/razão equivalente, não FAILED
  fabricado e não retry cego de apply.
- Identidade, ciclo/plano, tenant, entidade, janela, modo, replay, fingerprints,
  policy ausente/reprovada/obsoleta, selo ausente, auditoria incompleta e recibo
  inconsistente: nenhuma contradição abre a promoção. Não desabilitar triggers para
  fabricar corrupção; separar testes que provam recusa de escrita de testes de leitura.

Meça limites de login/query/network do driver instalado. Não chamar query timeout
de socket timeout, nem inferir queda de rede de um decorator que lança SQLException.
Onde o caso exija indisponibilizar servidor/rede ou privilégios fora deste escopo,
registrar a lacuna específica; não desligar infraestrutura para produzir a prova.
Essa lacuna não impede qualificar os cenários físicos autorizados, mas impede alegar
que a falha externa correspondente foi testada.

### E. Regressão, evidência auditável e fechamento

Corrija defeitos encontrados no código de produção e no SQL com regressões que
reproduzam a falha. Não aceitar o fake como oráculo do próprio fake, reduzir cobertura,
alterar thresholds, desativar testes ou adicionar skips para esconder uma IT vermelha.

Execute testes focados, a campanha física autorizada e `clean verify` offline com
JDK 17, Enforcer, Spotless, Checkstyle, arquitetura e JaCoCo. Execute validators
afetados de schema/progressivo/P02R/Coletas/Fretes/trilha, scanner offline, UTF-8
estrito sem BOM e `git diff --check`. Não acionar scanners online/perfis externos.

Se houver interferência do editor em target, use saída isolada e POM temporário
comprovadamente equivalente exceto build.directory. Preserve o POM canônico e
processos alheios. Limpeza só de arquivo próprio com caminho resolvido e conferido;
artefato ignorado pode ficar preservado se remoção segura não for possível.

Produza runbook de reprodução e relatório sanitizado por cenário: revisão dos
artefatos, topologia/caminho instalado, perfil/flags, classe/caso, sessões/processos,
ponto de falha, contagens antes/depois, resultado e limite da evidência. Não versionar
logs brutos, IDs/payloads de negócio, segredos ou fingerprint de identidade real.
Registrar claramente quais linhas sintéticas e mudanças de schema ficaram confirmadas.

Atualize ADR se a correção mudar protocolo, runtime ou limites de privilégio. Registre
recuperação da instalação por rollback antes do commit e evolução compensatória
versionada depois dele; não prometer que um rollback futuro apaga commits anteriores.

## 4. Aceite e continuidade

O pacote só fecha como **QUALIFICACAO_FISICA_LOCAL_COMPLETA** quando A+B+C+D+E estiverem
integrados e os casos obrigatórios autorizados tiverem provas SQL/JDBC executadas,
incluindo sucesso entre processos para Coletas **e** Fretes, concorrência do protocolo,
fencing, rollback e readback idempotente. Harness pronto, migration preparada,
validação estática, testes skipped e persistência em arquivo não satisfazem esse aceite.

Na abertura autorizada, represente P02Q sob V2-022 e sincronize seletor/trilha/validator
sem alterar o histórico de B52. Ao fechar, marque apenas o subcheckbox comprovado;
recalcule contagens a partir dos itens reais. Não preencha [x] para alcançar um número.
Preserve V2-022 pai, V2-022b/G08, V2-041 e V2-042b/c abertos enquanto faltarem seus
próprios aceites. P02Q não fecha V2-038, V2-050, paridade externa, escala produtiva,
relações de negócio, segurança operacional ou cutover.

Prossiga autonomamente nas correções e nas partes autorizadas até o pacote estar
concluído. Diante de bloqueio externo concreto, termine primeiro todo trabalho
independente necessário e entregue o estado real, sem renomear preparação como
qualificação concluída. Não consumir outro bloco somente para documentação.

Na resposta final informe o comportamento agora comprovado no banco, defeitos
corrigidos e seus diffs/arquivos, testes e resultados, schema/dados que permaneceram,
aceites marcados e lacunas. Aponte o próximo trabalho elegível: se identidade continuar
ausente, listar os inputs exatos de G06/V2-042b antes de propor G07/G08, sem inventar
authority, provider, principals, mapeamentos ou autorização positiva.
