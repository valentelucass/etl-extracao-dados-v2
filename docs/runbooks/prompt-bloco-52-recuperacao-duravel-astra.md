# Prompt para outro chat — Bloco 52/P02R

Este arquivo prepara o próximo chat. Sua criação não inicia nem conclui o Bloco 52.
Use o texto abaixo como instrução de execução no projeto.

---

Trabalhe exclusivamente no projeto:
`C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2`

Quero avançar em um bloco grande e coeso de construção Java usando Astra. Execute o
**Bloco 52/P02R — V2-022/RECUPERACAO_DURAVEL_LOCAL** até concluir o pacote local
definido aqui. Não encerre apenas com análise, documentação, interfaces ou um reader isolado.
Tome decisões técnicas rotineiras autonomamente e comunique achados e limites durante o trabalho.

## 1. Contexto e ponto de partida

Leia integralmente AGENTS.md, STATES.md, ../CONTEXTO_GLOBAL.md e
docs/runbooks/trilha-de-chats-gpt-5-6.md. Confira a numeração e o seletor atuais antes de agir;
não sobrescreva avanços que tenham ocorrido depois deste prompt.

Leia especialmente ADRs 0009, 0010, 0011, 0012, 0014, 0022, 0032 e 0033;
docs/runbooks/v2-022-motor-local-coletas-fretes.md; database/README.md; os manifestos,
SQL, protocolos, consumidores e testes efetivamente envolvidos. Registre um resumo das
decisões aplicáveis; distinga leitura integral, trechos consultados e fotografias históricas.

O Bloco 51 já integrou Coletas/Fretes: dispatcher, contrato/travessia, staging auditado,
candidate set, DQ e promoção. Reutilize RuntimeDispatcher, RuntimeExecutionSession,
LocalColetasFretesRuntime, os casos de uso reais, adapters JDBC e os dois permits existentes.

Limitação conhecida: recoverPromotion() depende de permits vivos. Após perda da JVM ou
incerteza de start/transição falta leitura durável tipada e reconstituição verificada.
O ControlPlane atual não oferece essa leitura. O SQL é autoridade de estado, lease,
concorrência e relógio; start registra PLANNED e EXTRACTING atomicamente.

Baseline registrada: 1001 testes, zero falhas/erros e quatro skips esperados.
Após a auditoria documental: 113 checkboxes, 58 concluídos, 55 pendentes e 197 fatias abertas.
974/949 e as contagens anteriores são históricas. Quantidade de itens não mede prontidão produtiva.

## 2. Resultado principal esperado

Construir a recuperação local do motor **sem depender dos objetos da execução anterior**:
consultar evidência persistida, identificar o resultado efetivo, decidir a única ação
permitida, recuperar ou recusar explicitamente e produzir resultado tipado coerente.

Exemplo de aceite: o apply confirmou a publicação, mas sua resposta foi perdida e a JVM
terminou. Uma nova instância deve reconhecer o recibo durável da mesma ocorrência, devolver
PUBLISHED confirmado e não reextrair, reaplicar efeitos ou avançar novamente a fronteira.

Outro exemplo: há staging/candidate set e evidência completa que autoriza continuação,
mas o processo anterior desapareceu. A recuperação deve validar esses vínculos e seguir
o protocolo existente; se faltar evidência, deve explicar o bloqueio sem fabricar permits.

Antes de codificar, registre brevemente escopo, dependências satisfeitas, invariantes,
estados recuperáveis e critérios de aceite. Em seguida implemente o conjunto integrado.

## 3. Pacote obrigatório — construir em conjunto

### A. Contrato durável de leitura e evidência

- Definir e implementar porta tipada e adapter JDBC para leitura da ocorrência e dos
  resumos necessários de lease, contrato/configuração, candidate set, DQ e publicação.
- Diferenciar NOT_FOUND, resultado conhecido, evidência insuficiente/inconsistente e
  indisponibilidade. Erro de leitura não significa ausência de execução ou de commit.
- Vincular ocorrência, ciclo/plano quando aplicável, namespace, modo, janela, origem do
  replay e fingerprints. Nunca selecionar dados de outra execução por proximidade de horário.
- Manter leitura limitada a resumos; não carregar staging, payloads ou conjuntos de IDs na JVM.
- Definir consistência da leitura e revalidação no comando de escrita: um snapshot lido
  antes não permite ignorar mudança de estado, revogação de evidência ou perda de lease depois.
- Identificar quais evidências já são persistidas e quais ainda faltam. Se necessário,
  preparar migration aditiva, baseline, manifesto, ADR e exercícios transacionais em arquivos.
  Não modificar migrations históricas aplicadas para esconder a evolução.
- Evidência nova deve nascer no ponto correto do protocolo, somente após seus gates;
  deve ser imutável/vinculada e possuir integridade verificável. Fingerprint informado
  pelo caller, estado PROMOTED ou booleano de fixture não substituem comprovação de contrato.

### B. Reconstituição verificada e plano de recuperação

- Criar os objetos de retomada por caminho interno validado, preservando o encapsulamento
  dos permits; não expor construtor/factory público que aceite arbitrariamente “aprovado”.
- Reconstituir os permits apenas quando houver evidência durável suficiente e vínculos
  íntegros. Se o desenho mais seguro dispensa reemitir um permit, registrar a decisão e
  preservar as mesmas garantias no consumidor concreto.
- Preservar as regras de validade de DQ, policy, contrato/configuração e revalidação SQL.
  Ler PUBLISHED confirmado é diferente de autorizar uma publicação ainda não realizada.
- Cobrir a matriz dos estados existentes: publicação confirmada, execução em andamento,
  extração/staging parcial, candidate set preparado, DQ ausente/reprovada/obsoleta,
  terminal FAILED/CANCELLED/BLOCKED, ocorrência ausente e estado contraditório.
- Distinguir recuperação da mesma ocorrência, nova tentativa com outra ocorrência/chave,
  replay com origem explícita e retry de transporte. Não reiniciar automaticamente uma
  extração parcial na mesma ocorrência se não houver contrato seguro de checkpoint.
- Não ressuscitar ocorrência terminal, roubar lease ou substituir o relógio SQL pelo da JVM.
  Inspecionar o impacto de recoverStaleExecutions antes de executá-lo na entrada da recuperação.
- Fornecer reasons tipados/sanitizados; preservar a causa técnica para diagnóstico interno.

### C. Integração real ao motor local

- Integrar a recuperação a dispatcher/sessões e aos gateways reais de Coletas/Fretes.
  Não deixar um serviço novo sem consumidor concreto ou uma segunda máquina de estados divergente.
- Resolver ack perdido de start/transição/prepare/apply usando estado durável e o protocolo
  idempotente correspondente, sem retry cego e sem criar eventos/efeitos duplicados.
- Preservar resultados independentes e produzir novo resumo após recuperação. Dependente
  só fica elegível com a evidência exigida na mesma janela/namespace; não ressuscitar
  automaticamente uma ocorrência dependente já terminal BLOCKED.
- Tratar cancelamento antes/durante recuperação e após confirmação do commit. Cancelamento
  não apaga publicação durável nem transforma commit desconhecido em falha confirmada.
- Integrar limites explícitos de timeout/deadline/cancelamento onde o caminho de recuperação
  usa JDBC; reutilizar mecanismos existentes. Não adicionar thread ou pool sem consumidor
  e política de fechamento claros. Timeout de query não equivale a timeout de conexão/socket.
- Manter a composição operacional oficial deny-all. A API local de recuperação é interna
  e não concede identidade, capability operacional ou acesso por flag/configuração.

### D. Prova integrada independente da sessão anterior

- Exercitar casos de uso e adapters reais, com fonte e persistência sintéticas controladas.
  A recuperação não pode reusar RuntimeExecutionSession, guard, permits ou cache antigos.
- Incluir teste com processo Java filho próprio e estado sintético persistido entre
  processos para demonstrar que a retomada não depende de objetos da JVM anterior.
  Criar/encerrar somente processos do próprio teste; não tocar nos processos do usuário.
- Demonstrar sucesso nas duas verticais, publicação já confirmada com resposta perdida,
  continuação elegível, bloqueio por evidência incompleta, lease perdida, cancelamento,
  replay sem avanço incremental, nova tentativa e preservação de resultado independente.
- Incluir adulteração de execução/tenant/janela/fingerprint/policy, recibo inconsistente,
  falha de auditoria/leitura e mudança entre leitura e comando. Nenhuma deve abrir promoção.
- Usar contadores de chamadas/efeitos para provar ausência de nova extração e duplicação
  de aplicação/fronteira. Não fazer o teste apenas comparar o fake com a regra do próprio fake.
- Explicitar o limite: restart com fixture persistida comprova a independência da JVM,
  mas não comprova restart, atomicidade, locks, concorrência ou recovery físicos do SQL Server.

## 4. Critérios para fechar este bloco

O aceite exige A+B+C+D integrados, testes comportamentais verdes, documentação e validadores
coerentes. Um catálogo de estados, reader isolado ou a recusa genérica de todos os casos não
fecha o pacote. Deve existir recuperação positiva comprovada sem permits vivos anteriores,
além das recusas corretas. Diferencie cenários já recuperáveis dos que precisam de nova tentativa.

Se houver parte realmente dependente de informação externa, descreva o artefato/contrato
faltante e continue as partes locais independentes. Não use os holds de identidade ou a
ausência de autorização de SQL físico para abandonar implementação/testes sintéticos.
Não enfraqueça os gates para produzir um caminho positivo artificial.

## 5. Limites desta execução

- Somente Coletas/Fretes e a infraestrutura comum consumida por elas neste pacote.
- Sem credenciais, .env, Data Export/GraphQL externos, remote, deploy, agendamento, restart
  de serviço, push, cutover ou alterações no projeto de dashboards.
- **Não executar SQL físico neste bloco.** Pode preparar código JDBC, migrations,
  manifests/baseline e exercícios rollback-only como arquivos para revisão futura.
- Não acionar o perfil Java/JDBC físico: a autorização histórica cobre somente auditoria
  numa conexão que bloqueia commit; não cobre esta prova integral de recuperação/promoção.
- Sem novos grants/principals, política financeira, identidade de fornecedor, crosswalk,
  relação de negócio, fato, view de consumo ou expansão para outras verticais.
- Não alterar thresholds, desativar testes ou enfraquecer validadores. Sem contornar deny-all.
- Preserve todas as mudanças preexistentes. Nenhum commit faz parte deste pedido.

## 6. Validação e handoff obrigatórios

Execute testes focados durante a construção e a suíte completa ao fechar, com estilo,
arquitetura, cobertura, validadores afetados, scanner offline, UTF-8 e diff check.
Use JDK 17. Se o editor interferir em target, use saída isolada com POM equivalente,
sem parar processos do usuário e sem reduzir gates; remova somente seus temporários.

Atualize STATES.md, trilha/painel, contagens e validadores. Marque somente os aceites locais
comprovados de P02R; crie subcheckboxes apenas se necessários para representar entregas reais.
V2-022, V2-022b, V2-041 e V2-042b/c continuam abertas enquanto seus próprios gates faltarem.
Não converta um teste sintético positivo em evidência física ou autorização operacional.

Registre arquivos/diff, mudanças de contrato/schema preparadas, comandos/resultados,
reprodução dos testes, rollback, cenários ainda não suportados e próximo bloco executável.
Não consuma outro bloco apenas para documentação ou invente avanço produtivo por percentuais.

Na resposta final, diga objetivamente: qual recuperação passou a funcionar; como foi provada
sem objetos da JVM anterior; principais arquivos; o que foi marcado concluído; validações;
limites físicos/operacionais e próximo pacote grande. Continue até concluir o pacote local.
