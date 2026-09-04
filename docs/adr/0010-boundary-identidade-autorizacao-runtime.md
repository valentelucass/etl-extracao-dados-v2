# ADR 0010 — Boundary de identidade e autorização do runtime

- Status: Aceito para o subgate offline V2-042a; integração externa pendente
- Data: 2026-08-30

## Contexto

O runtime V2 será executado como CLI one-shot por scheduler/supervisor externo. Proteger apenas o
wrapper permitiria contorno por invocação direta do JAR pelo entry point oficial.
Também seria inseguro aceitar principal ou papel fornecido pelo próprio processo por argumento,
arquivo, propriedade de sistema ou variável de ambiente.

V2-042 ainda não recebeu a escolha organizacional do provedor, da authority, dos principals nem
dos mapeamentos de papéis. Essas definições dependem de owners externos e não podem ser inferidas
no código. É necessário, porém, fechar offline a arquitetura, a matriz de autorização e o estado
seguro anterior à integração.

## Decisão

Há uma fronteira provider-neutral dentro do composition root oficial do runtime. Todo futuro
handler de ação protegida deverá ser composto e executado somente depois de receber uma identidade verificada,
validar vigência/bloqueio/revogação, autorizar a ação e registrar a decisão sanitizada. Wrapper,
scheduler e invocação direta do JAR percorrem exatamente a mesma fronteira.

O default é um verifier não configurado que nega todas as ações protegidas. Erro, timeout,
resposta ambígua e indisponibilidade do provider ou da auditoria também negam. A comparação de
fingerprint/mapping depende do futuro adapter externo e ainda não está implementada. O
runtime nunca aceita papéis autoafirmados em CLI, `-D`, configuração ou ambiente.

A política RBAC é aditiva, sem `ADMIN`, wildcard ou hierarquia implícita:

- `status`: `OBSERVER`;
- `run`: `EXECUTOR`;
- `replay`: `EXECUTOR` + `REPLAY`;
- `sweep preview`: `OBSERVER` + `SWEEP_REVIEW`;
- `sweep apply`: `EXECUTOR` + `SWEEP_APPLY`;
- `force-run`: `EXECUTOR` + `FORCE_RUN`.

Identidades verificadas são classificadas como serviço ou operador e expõem ao runtime somente
uma referência de auditoria opaca, papéis efetivos, vigência e fingerprint declarado pela
authority. O formato/redaction da referência é validado aqui; sua pseudonimização e o vínculo do
fingerprint com o mapping aprovado são obrigações de conformidade do adapter externo. Credenciais e
claims brutos não integram objetos de domínio, eventos, logs ou exceções.

Ajuda, versão, `config validate` e `dry-run` podem ser anônimos enquanto permanecerem estritamente
locais e sem segredo, rede, conexão, DDL ou DML. Ações novas são protegidas por default.

`migrate` e `cutover` ficam fora do runtime JAR e usam processos, principals e permissões SQL
separados. A identidade de runtime não recebe DDL, ownership, alteração de grants ou capacidade de
cutover. A autorização Java complementa, e não substitui, least privilege e fences do control
plane.

A seleção do provider/mecanismo, authority/audience, principals, mappings, revogação e sink de
auditoria é `EXTERNAL_INPUT_REQUIRED`. O adapter concreto só poderá substituir o deny-all depois
dessas aprovações e de testes de conformidade positivos e negativos.

O modelo de ameaças, razões de rejeição e requisitos de auditoria estão em
[`docs/seguranca/modelo-ameacas-autorizacao-runtime.md`](../seguranca/modelo-ameacas-autorizacao-runtime.md).

## Consequências

- O entry point oficial e seu composition root não possuem caminho operacional positivo enquanto a
  integração externa estiver ausente.
- Nenhuma ação operacional funciona enquanto a integração externa estiver ausente ou inválida.
- `force-run`, replay e sweep exigem capacidades adicionais e continuam sujeitos aos demais gates.
- A trilha usa reason code fechado e, quando existe identidade verificada, referência opaca; falha
  de auditoria bloqueia a ação. O formato opaco, por si só, não comprova pseudonimização, que
  permanece obrigação do adapter futuro.
- Migração/cutover permanecem operacional e tecnicamente separados do runtime.
- Testes offline podem provar deny-all, matriz e sanitização, mas não provam integração com uma
  authority real.
- Reflection, código arbitrário no classpath, consumo único da capacidade e proteção de futuros
  handlers dependem do empacotamento/dispatcher e não são alegações deste subgate.
- Este ADR não conclui V2-042 globalmente nem autoriza credenciais, rede, release ou deploy.

## Alternativas rejeitadas

- **Autorizar no wrapper ou scheduler:** pode ser contornado por invocação direta do artefato.
- **Papéis em configuração/ambiente:** permite autoatribuição pelo próprio chamador.
- **Papel `ADMIN`:** oculta capacidades, amplia blast radius e dificulta evidência negativa.
- **Fail-open quando provider/auditoria falha:** transforma indisponibilidade em escalada de
  privilégio.
- **Compartilhar principal com migrations/cutover:** entrega ao runtime poderes incompatíveis com
  sua função e quebra segregação de deveres.
