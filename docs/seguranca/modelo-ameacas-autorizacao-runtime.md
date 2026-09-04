# Modelo de ameaças — identidade e autorização do runtime

- Escopo: subgate offline V2-042a
- Data: 2026-08-30
- Estado: modelo e política provider-neutral definidos; V2-042 permanece aberto

## Estado de segurança atual

O composition root oficial permanece em `deny-all` para ações operacionais protegidas. A escolha do provedor de
identidade, da authority confiável, dos identificadores de principals e dos mapeamentos entre
principals e papéis é `EXTERNAL_INPUT_REQUIRED`. Nenhum valor provisório, identidade local ou
mapeamento inventado por este repositório pode liberar execução.

A fronteira foi ligada ao composition root usado pelo entry point oficial, portanto vale para
chamada pelo scheduler, wrapper, script ou invocação direta do JAR por esse entry point. O launcher
externo não é uma fronteira de segurança. Nenhuma combinação de argumento CLI,
propriedade de sistema, arquivo de configuração ou variável de ambiente pode atribuir identidade
ou papel ao próprio chamador.

O subgate não afirma resistência a código arbitrário no classpath, reflection ou composição direta
de classes internas. Os handlers operacionais ainda não existem; cada um deverá receber e consumir
uma capacidade válida no dispatcher antes de V2-042 global poder ser concluído.

Comandos locais sem efeito operacional — ajuda, versão, validação de configuração e `dry-run` —
podem permanecer disponíveis sem principal verificado, desde que não resolvam segredo, não abram
rede, não criem conexão e não executem DDL/DML. Qualquer novo comando é protegido por padrão até
ser classificado explicitamente.

## Ativos e objetivos

Esta fronteira protege:

- credenciais e material de autenticação, que nunca devem aparecer em argumentos, configuração,
  logs, eventos ou exceções;
- dados de sombra, ledgers, leases, checkpoints, publication pointers e trilha de auditoria;
- integridade de `run`, `replay`, `sweep`, `force-run` e consultas de estado;
- separação entre identidade de serviço, operador humano e identidade de migração/cutover;
- disponibilidade controlada: falha de identidade ou auditoria não pode virar autorização.

O objetivo é garantir autenticação por uma authority aprovada, autorização mínima por ação,
credenciais temporalmente válidas e auditoria sanitizada de toda decisão. A autorização não
substitui os fences de lease, estado, reconciliação, publicação ou qualidade de dados.

## Fronteiras de confiança

1. O scheduler ou operador inicia o processo, mas não é confiável para declarar papéis.
2. Um adaptador de identidade futuro valida a credencial perante a authority escolhida e produz
   apenas uma identidade verificada, temporalmente limitada e com referência opaca.
3. A fronteira de autorização valida estado, vigência e política antes da futura composição de
   qualquer handler operacional ou resolução de seus segredos.
4. O control plane e os adapters persistentes aplicam ainda permissões SQL mínimas e fences
   transacionais; autorização no Java não transforma o runtime em owner do banco.
5. O sink de auditoria registra a decisão sanitizada. Indisponibilidade ou recusa do sink para uma
   ação protegida causa negação; não existe modo silencioso ou fail-open.

O provider, o mecanismo de credencial, a authority, os audiences, os tempos de validade, os
principals e suas atribuições não estão definidos aqui. Todos são entradas externas obrigatórias,
com owner e evidência de aprovação fora do Git.

## Principals e política RBAC

Há duas classes de principal verificável:

- `SERVICE`: identidade não humana do scheduler/supervisor;
- `OPERATOR`: identidade humana usada em ação interativa autorizada.

Os papéis são capacidades independentes. Não existe papel `ADMIN`, wildcard, hierarquia implícita
ou herança que agregue todas as ações. A decisão exige todos os papéis da linha:

| Ação | Papéis mínimos | Observação |
| --- | --- | --- |
| `status` | `OBSERVER` | Consulta somente o estado operacional sanitizado. |
| `run` | `EXECUTOR` | Executa uma partição/modo já permitido pelos demais gates. |
| `replay` | `EXECUTOR` + `REPLAY` | Não ignora idempotência, escopo nem fences. |
| `sweep preview` | `OBSERVER` + `SWEEP_REVIEW` | Produz somente avaliação, sem aplicar ausência. |
| `sweep apply` | `EXECUTOR` + `SWEEP_APPLY` | Exige evidência de completude e protocolo próprio. |
| `force-run` | `EXECUTOR` + `FORCE_RUN` | Continua sujeito a lease, contrato, DQ e write-fence. |

`migrate` e `cutover` não pertencem ao conjunto de ações do runtime JAR. Devem usar processos,
identidades e concessões separados, com janela, aprovação e rollback próprios. A identidade do
runtime não recebe DDL, ownership, alteração de grants nem permissão de cutover/publicação fora do
protocolo definido no control plane.

## Fluxo de decisão fail-closed

Para cada ação protegida, a implementação deve seguir uma única ordem:

1. obter uma credencial pelo mecanismo externo aprovado, sem materializá-la em argumento ou log;
2. validar assinatura/prova, authority, audience e vínculo com o principal;
3. rejeitar credencial ausente, inválida, ainda não válida, expirada, bloqueada ou revogada;
4. resolver o mapeamento aprovado de principal para papéis, sem aceitar papéis autoafirmados;
5. exigir todos os papéis da ação na política interna versionada;
6. registrar decisão sanitizada na auditoria obrigatória;
7. somente após decisão positiva compor o handler, resolver segredos operacionais e iniciar I/O.

Timeout, erro de parsing, resposta ambígua, authority indisponível, mapeamento inexistente e falha
da auditoria terminam em negação. A comparação entre o fingerprint informado pela authority e o
mapping aprovado depende do adapter externo de V2-042b/c e ainda não libera caminho positivo.
Cache, quando futuramente aprovado, não pode ultrapassar expiração/revogação nem aceitar política
de versão diferente.

O boundary amostra o relógio confiável antes e depois da verificação. Avanço durante a verificação
é considerado ao validar a expiração; regressão entre as amostras é indisponibilidade temporal e
termina em negação, sem usar o instante retrocedido para emitir capacidade.

## Auditoria e minimização

O contrato de evento para uma decisão à qual uma fonte de tempo confiável pôde ser estabelecida
contém, no mínimo:

- timestamp UTC gerado por fonte confiável;
- correlation/decision ID opaco;
- ação; o escopo operacional sanitizado será incluído quando os contratos dos handlers existirem;
- resultado `ALLOW` ou `DENY`, reason code fechado e fingerprint da política interna.

Somente quando o verifier produziu uma identidade válida, o evento também contém referência opaca,
classe (`SERVICE` ou `OPERATOR`) e fingerprint declarado pelo adapter de autoridade. Recusas que
ocorrem antes de uma identidade ser verificada deixam esses três campos ausentes por desenho. O
formato opaco não é evidência de pseudonimização, e a conformidade do fingerprint externo com o
mapping aprovado permanece pendente de V2-042b/c.

O evento não contém credencial, token, claim bruto, username, e-mail, SID, subject externo, header,
URL sensível, payload, tenant nominal, documento ou stack trace com entrada não confiável. Mensagem
de erro pública usa apenas reason code e correlation ID. O value object prova somente formato e
redaction; o futuro adapter deve provar que a referência é produzida com pseudonimização opaca,
estável apenas no domínio de auditoria aprovado e não reversível pelo runtime.

Se nem o instante confiável puder ser obtido, a tentativa é negada com reason code temporal, mas o
contrato atual não fabrica um evento com timestamp. A integração durável deve definir como registrar
essa falha sem aceitar o relógio não confiável.

O composition root atual ainda não possui sink durável: seu sink indisponível transforma qualquer
tentativa operacional em `AUDIT_UNAVAILABLE`. Os testes com sinks sintéticos em memória provam o
schema/minimização e o comportamento fail-closed, não persistência operacional de auditoria.

A auditoria de autorização não prova sucesso da operação. O control plane registra separadamente
estado, tentativa, lease e resultado da execução.

## Ameaças e controles

| Ameaça | Controle obrigatório | Evidência esperada |
| --- | --- | --- |
| Invocação direta do JAR contorna wrapper | Boundary no composition root oficial; futuros handlers só podem ser ligados ao dispatcher depois da decisão | Teste chama o composition root oficial diretamente e recebe negação |
| Chamador autoatribui principal ou papel | Nenhuma identidade/papel em CLI, `-D`, arquivo ou ambiente; somente resultado verificado do adapter | Testes negativos para cada canal de entrada |
| Credencial roubada, repetida ou fora da vigência | Validação de authority/audience, janela temporal e revogação/bloqueio; capacidade ligada à ação/UUID/expiração | Casos ausente, inválido, futuro, expirado, bloqueado e revogado; consumo único ainda pendente do dispatcher |
| Relógio avança ou retrocede durante a verificação | Reamostrar antes da decisão, aplicar a amostra avançada e negar regressão como indisponibilidade temporal | Testes de expiração durante a verificação e de relógio regressivo |
| Confused deputy entre serviço e operador | Classe é auditada; mappings externos explícitos definem quais papéis cada classe pode receber | Conformance do mapping por classe em V2-042b/c |
| Papel amplo permite ação lateral | Capacidades aditivas, sem `ADMIN`/wildcard; todos os papéis mínimos exigidos | Teste de cada combinação incompleta |
| Falha do provider ou da auditoria libera carga | `deny-all` e auditoria obrigatória fail-closed | Testes de timeout, exceção e indisponibilidade |
| Injeção ou vazamento em log | Boundary único V2-023, evento estruturado, reason codes fechados, referências opacas, redaction e budget | Testes com entrada maliciosa, caps e inspeção de saída; não substitui a auditoria de identidade V2-042b/c |
| Runtime eleva privilégio no SQL Server | Principal SQL mínimo, sem DDL/cutover/ownership e procedures/fences dedicados | Gates negativos de grants e migrations |
| Decisão antiga usada após troca de política | Fingerprint/versionamento e validade limitada; dispatcher revalida expiração e consumo | Mismatch externo e consumo único pendentes de V2-042b/c |
| Alteração de classpath/binary troca o boundary | Artefato reproduzível, checksum/proveniência e release gate futuro | Evidência de empacotamento/release, ainda pendente |

## Entradas externas e condições de desbloqueio

Permanecem `EXTERNAL_INPUT_REQUIRED`:

- provedor e mecanismo de autenticação aprovados;
- authority/issuer, audiences e parâmetros de validação;
- identificadores opacos dos principals de serviço e operadores;
- owner e matriz aprovada de mapeamento principal → papéis;
- SLA/comportamento de revogação, bloqueio e indisponibilidade;
- sink durável de auditoria e domínio aprovado de pseudonimização;
- identidades SQL separadas de runtime, migração e cutover.

Após essas decisões, um adapter específico deve passar testes de conformidade positivos e
negativos, incluindo falhas do provider e do sink. Até lá, o único adapter de runtime aceitável é
o não configurado, que nega todas as ações protegidas.

## Critério do subgate offline

V2-042a pode registrar como evidência apenas o modelo de ameaças, a política provider-neutral, o
default deny-all, os testes unitários/offline e a sanitização da auditoria. Isso não conclui
V2-042 globalmente e não autoriza rede, credenciais reais, release, deploy, operação externa,
migração nem cutover.
