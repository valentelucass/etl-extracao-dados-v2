# B61 — consolidação local de arquitetura e validadores; preparação da paridade

**PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO**, preparado em 10/09/2026.
Este arquivo conserva a proposta. A mensagem efetiva do usuário adotando este
prompt autoriza a implementação local descrita abaixo, sem confirmação repetida
para cada edição ou teste já coberto. O pedido de preparar o prompt não iniciou
a implementação no chat anterior.

## Objetivo e leitura inicial

Trabalhe em `C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2`.
Conclua as frentes A–D deste bloco: corrigir a fronteira e o limite do redutor de
Manifestos, consolidar a validação corrente e preparar os inputs necessários à
paridade. Corrija e teste até cumprir os critérios locais; não entregue apenas
um plano ou encerre enquanto existir trabalho independente coberto pelo escopo.

Leia `AGENTS.md`, `STATES.md`, `../CONTEXTO_GLOBAL.md`,
`docs/continuidade/RETOMADA.md`, `docs/runbooks/continuidade-agentes.md`, este
prompt e `docs/continuidade/checkpoints/0050-avaliacao-e-preparacao-bloco61.md`.
Confira `docs/catalogos/bloco61-preparacao/manifesto.json` e execute seu validador
antes de editar arquivos vinculados por hash. O estado pode evoluir depois da
proposta: confira a sucessão existente, preservando todo trabalho preexistente.

Ponto de partida: 67/115 critérios, 48 pendentes, 191 rotas abertas, zero AGORA.
Esses contadores são históricos da preparação, não metas numéricas do bloco.
O B60 foi ACEITO_NO_ESCOPO físico local, com 74 casos e sete adversariais. O
recibo está em `target/b60-conclusao-20260910/final/receipt.json`; seu SHA-256 é
`37f35f77e160f4fdfa88c361089d2b99f9544153921c2a049588dde3bc88478e`.
Use a sucessão documental para conferir os quatro arquivos evoluídos: um hash
atual diferente do snapshot antigo não autoriza reconstruir o recibo B60.

Relatório de origem: `target/avaliacao-v2-v1-20260910/RELATORIO.md`.
Os achados essenciais já estão no STATES, para não depender apenas do target.

## Escopo autorizado quando este prompt for adotado

Edições locais revisáveis no V2, testes Java 17 e PowerShell, fixtures sintéticas,
build Maven offline em diretório isolado e documentação/continuidade. Os testes
podem usar loopback sintético quando já fizer parte da suíte offline; não podem
conectar fornecedor, banco, autenticação Windows operacional ou endpoint real.
JVMs de compilação/teste offline não são uma renovação da campanha física B60.

Não executar SQL, migration, JAR operacional, UAC, concessão, replay/force físico,
bootstrap, fonte-oráculo remota, feed de vulnerabilidades, deploy ou cutover.
Não acessar dados produtivos, alterar V1, credenciais, contas ou agendamentos.
O orçamento B60 encerrou em 204 SQL/83 JVMs físicas/75 HTTP; não reutilizar sua
validade, saldo ou autorização para um ensaio novo. A antiga autorização de
renovação era para concluir B60, não uma expansão automática deste bloco.

Preserve V001–V024, baselines, pacotes físicos, ledgers, logs falhos, snapshots,
receipts e decisões de negócio. Não alterar hashes antigos nem introduzir uma
exceção genérica de integridade para facilitar a passagem de validadores.

## A — fronteira e limite do redutor de Manifestos

Inspecione `modulos/manifestos/domain/ManifestoRootReducer.java`, tipos, testes,
contrato e decisões vigentes MAN-01–MAN-07/V2-026. Confirme os consumidores reais
antes da mudança. Na revisão anterior só foi encontrada a declaração em main e
uso nos testes: não ligar o redutor ao runtime para justificar a refatoração.

Separe a interpretação/serialização JSON da regra de domínio, usando a borda
adequada e tipos de domínio existentes sempre que possível. Imponha limite
explícito antes de acumular a entrada, coerente com o contrato da coorte; não
invente novo tamanho de página ou regra de negócio. Preserve presença/nulo,
frescor, conflito, precedência e invariância à ordem/replay. Agregação de massa
continua em SQL; a refatoração não cria alternativa de execução em memória.

Aceite A: testes demonstram comportamento anterior preservado e rejeição de
entrada excedente/preguiçosa sem percorrer uma sequência ilimitada; JSON deixa
de ser dependência do redutor no domínio. Inclua limite exato, limite+1 e casos
de nulo/conflito/ordem pertinentes. Registre o limite e sua fonte contratual.

Fortaleça o teste de arquitetura para abranger os domínios dos módulos e detectar
essa regressão com um contraexemplo que realmente falhe. Não produzir teste que
apenas procure o nome da classe corrigida, nem flexibilizar regras para manter
o acoplamento. Se uma regra geral revelar casos adicionais, limite a correção
ao mesmo tipo de fronteira, justificando o diff e preservando a semântica.

## B — validação corrente e complexidade de SQL/scripts

Mapeie os pontos de entrada usados para qualificar o runtime e diferencie
validação atual de reprodução histórica. Priorize uma entrada corrente clara,
mensagens úteis e remoção de duplicação local com benefício demonstrável.
Não iniciar reescrita ampla do SQL ou unificar controles de propósitos distintos.

Use a evidência B60: o controlador b776e40f… permanece NOT_QUALIFIED; o aceite da
concorrência decorreu da revisão física independente. SQL059 corrige igualdade
com NULL, mas o observador inteiro não foi repetido com duas JVMs. SQL060 é o
adversarial adotado; SQL057/058 são históricos. Leia `concurrency-review.json`,
os controladores e testes correspondentes antes de propor o caminho corrente.

Aceite B: entrada/índice executável corrente inequívoco, com regressões offline
que detectem saída antecipada do observador, tratamento incorreto de NULL,
evidência concorrente ausente/divergente e falha de recuperação. Exercite o
validador real sobre dados sintéticos/replays identificados; não duplicar sua
lógica num teste nem transformar comparação textual em prova de semântica SQL.
Preserve resultados históricos e qualifique os resultados novos como offline.
Um teste local não pode declarar que o observador corrigido passou fisicamente.

## C — preparação concreta da paridade da primeira onda

Confira V2-012a/b/c, V2-025, V2-009, V2-047 e as rotas Q-USR-01/Q-COL-01,
incluindo as dependências de segurança e as permissões aplicáveis. Produza uma
matriz curta: entidade, contrato, identidade, oráculo necessário, janela/volume
a ratificar, campos/comparações, impedimento exato e evidência que o resolve.
Escolha a próxima caracterização candidata pela dependência realmente atendida;
se nenhuma estiver liberada, registre isso sem promover uma rota a AGORA.

Use os harnesses já existentes de Q-FND-01/Q-FND-02; não criar outra fundação
paralela. Prepare o procedimento revisável que poderá consumir o oráculo quando
houver evidência e autorização suficientes. Não emitir consultas remotas ou SQL
para completar a matriz. Fixtures não provam contrato, identidade ou completude.

Aceite C: documento acionável com inputs mínimos e critérios originais mapeados,
sem caracterização/paridade real alegada. Falta de oráculo não impede concluir
A/B e a preparação C. Users SHADOW_UPSERT_ONLY e fim de paginação não provam
snapshot completo, exclusão na origem nem autorização de Sweep and Prune.

## D — validação, preservação e entrega

Antes da primeira edição, registre inventário, revisões e diretório privado
novo. Não executar `clean` no target canônico ou em diretório com evidências.
Prepare build isolado e comprove o caminho efetivo antes de limpá-lo. Use Java
17, testes dirigidos significativos, depois o `verify` offline completo com
formatação/lint/arquitetura/cobertura exigidos pelo projeto, sem perfis físicos.
Preserve os logs de falha e de correção. Não mascarar skip, warning ou erro.

Atualize STATES, trilha, RETOMADA, checkpoint e sucessão exata dos arquivos
alterados, preservando a revisão documental B61-preparação. Rode os validadores
de integridade/continuidade/trilha e scanner de segredos pertinentes, sem
afrouxar proteções históricas. A suíte 1397/0/0/4 era histórica da preparação;
registre os números e limites da execução realmente realizada neste bloco.

Entregue: mudanças A/B, matriz C, testes e limitações, diff revisável contra o
inventário inicial, relatório final e checkpoint curto com até três próximas
ações. A–D são critérios de manutenção deste bloco, não novos checkboxes do
roadmap. Não fechar pais, V2-012, V2-038, V2-050 por entidade ou cutover apenas
por concluir esta consolidação local.

Pare somente o efeito que ultrapassaria escopo ou cujo resultado seja
desconhecido; investigue/reconcilie antes de repetir e continue as frentes
independentes autorizadas. Caso uma ação externa seja indispensável, deixe a
proposta concreta com alvo, limites, recuperação e justificativa para decisão
posterior. Não invente aprovação ou fornecedor para declarar o bloco completo.
