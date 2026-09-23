# Blocos da etapa 2

Guia rápido para retomar P16–P29 em vários chats. O estado autoritativo é
[STATES.md](STATES.md): só marque uma caixa aqui depois de registrar a evidência,
limites e resultado nele.

## Situação atual

- Uma nova sonda de Coletas 6908 comparou Data Export em `per=50/100` com
  GraphQL numa janela fechada independente: ambas as travessias chegaram à
  página vazia, GraphQL terminou, e a única entidade coincidiu em chave
  natural e ID canônico. A prova de identidade **dessa janela** está concluída
  e registrada no cabeçalho de `STATES.md`/checkpoint 0285. P17/P20 e a
  matriz V2-025d continuam abertos por critérios mais amplos.
- Em 22/09/2026, após decisão explícita do usuário de usar a credencial
  existente apesar da exposição, sondas `curl` read-only de 6908 e 6389
  retornaram somente HTTP 200 e dados reais. Coletas observou 252 entidades
  em três páginas `per=100` e Fretes 400 em quatro; ambas pararam no teto sem
  página terminal. A metadata atual de 6389 tem 110 campos, declara `id` e
  não declara `finished_at`; a baseline sintética local não deve ser promovida
  como release real. Essa exceção não fecha V2-041 nem autoriza JAR/produção.
  Evidência sanitizada no cabeçalho de `STATES.md` e nos checkpoints 0283/0284.
- Por decisão do usuário em 22/09/2026, os insumos que dependem de dados reais
  serão cobrados na primeira campanha correspondente em que o V2 efetivamente
  ler dados reais em sombra. Isso não antecipa produção nem dispensa os gates
  antes de publicação/cutover. `ETL_SISTEMA` da V1 é apenas candidato a
  referência, sujeito a autorização específica de consulta read-only e
  validação de grão, tempo e escopo; ver `STATES.md`.
- O V2 já tem implementação local em sombra para verticais, relações, dimensões,
  fatos, contratos SQL, ausência, escala e recuperação.
- Em 22/09/2026, a validação local registrou 2.263 testes, zero falhas, zero
  erros e cinco ignorados. A nova rodada técnica corrigiu o validador SQL
  read-only de Manifestos para V022 e passou as verificações dirigidas de
  sombra. O `verify` físico fresco foi interrompido no teto após 91 classes de
  integração sem erro e rollback agregado; o JaCoCo final não rodou nessa
  tentativa. Comparação byte a byte provou que 1.281 arquivos de código e
  recursos e o POM são idênticos à cópia cujo `verify` integral passou com
  492 ITs e JaCoCo verde. Dos 172 arquivos SQL, só o validador Manifestos
  difere e ele passou após correção. O resultado e seus limites constam no
  cabeçalho de `STATES.md`. Isso não é
  aceite da fonte, do negócio, de consumidor ou de produção.
- A amostra read-only atual de 6906/8636 respondeu HTTP 200, mas não trouxe os
  contratos oficiais, referências, oráculos ou provas de grão requeridos.
  As onze entidades têm agora aceite B16 apenas interno de sombra; os gates
  externos permanecem abertos. Evidência sanitizada em
  `docs/continuidade/probes/2026-09-22-6906-8636-amostra-readonly.md` e estado
  autoritativo em `STATES.md`.
- A paginação posterior de 6906 caracterizou expansão física limitada: na
  página 2, 112 linhas físicas corresponderam a 100 `sequence_code` inteiros,
  escalares e não nulos distintos; a página 3 teve 49/48. A página 4 teve
  envelope inválido e encerrou a ordem com `DATA_ENVELOPE_INVALID`, sem
  terminal. Isso não prova grão/identidade oficial nem fecha outro item;
  recibos em `docs/continuidade/probes/2026-09-22-6906-paginacao-limite.md` e
  `docs/continuidade/probes/2026-09-22-6906-paginacao-expansao-envelope.md`.
- Uma janela fechada distinta de 6906 teve `/info` e página inicial HTTP 200:
  101 linhas físicas para 100 candidatos distintos; a página não era terminal
  e a sonda parou no teto de duas chamadas. Isso não fecha P17 nem autoriza
  nova consulta por continuidade automática; evidência no cabeçalho de
  `STATES.md`.
- A continuação cURL dessa janela (páginas 2–6, teto de seis chamadas)
  consumiu quatro chamadas: página 2 com 100 linhas/100 candidatos distintos,
  página 3 com 62/60 e página 4 HTTP 200 com envelope inválido. A sonda parou
  sem retry; páginas 5–6 não foram chamadas. O fornecedor ainda precisa
  esclarecer o contrato de paginação/terminal; nenhuma caixa B17–B20 foi
  marcada. Recibo em
  `docs/continuidade/probes/2026-09-22-6906-continuacao-0309.md`.
- A pendência financeira afeta Faturas e MAT-03/MAT-04, mas não bloqueia outro
  bloco independente que tenha os inputs necessários.
- V2 permanece em sombra: sem escrita produtiva, deploy, cutover, alteração de
  credencial, controle do ETL legado ou nova chamada ESL fora do que está
  autorizado e registrado.

## Regra de uso

1. Escolha a menor rota por entidade que tenha recebido o input exigido. B16 não
   é uma barreira global: uma entidade independente pode avançar sem esperar as
   outras.
2. Cada prompt deve concluir uma unidade verificável da rota: execute todas as
   partes independentes, corrija defeitos demonstrados e valide
   proporcionalmente. Não abra rodada só para plano, documentação, checkpoint ou
   repetição de teste sem avanço material.
3. Se houver alteração, atualize primeiro `STATES.md`, depois trilha/validadores,
   crie checkpoint e por último `docs/continuidade/RETOMADA.md`.
4. Marque a caixa no mesmo trabalho e somente após o registro da evidência no
   `STATES.md`; esforço parcial, fixture ou planejamento não fecham uma linha.

Não abrir bloco apenas para repetir Maven, auditoria, prova sintética ou
documentação. Sem input novo ou defeito concreto, registre o bloqueio e procure
outro bloco independente.

## Caminho prioritário para destravar

### Primeira leitura real em sombra — Coletas 6908

P17–P29 que exigem dados reais são qualificados durante a campanha da entidade,
não antes da primeira amostra. A sonda read-only de 6908 foi executada sob a
exceção explícita do usuário, via `Invoke-DataExportContractProbe.ps1`, com
perfil sanitizado e teto finito. Como a página terminal não foi alcançada,
P17, paridade e publicação continuam abertos. Segurança/Operações ainda deve
comprovar V2-041/G01 para o aceite original de rotação/invalidação e
continuidade do writer legado. Uma leitura ESL pelo JAR necessita de
autorização própria do runtime; a autorização da sonda não se transfere.

Esta é a rota inicial dentro da allowlist atual. As sondas históricas de 6906
e 8636 não ampliam essa allowlist.

### Rota 1 — Cotações (aceite posterior, sob autorização própria)

Esta rota de **aceite P17 posterior** é curta porque não depende das relações
Manifesto→Coleta ou Coleta→Frete, nem da investigação financeira. O trabalho segue:

`Cotações: P16 → P17 → P18 se aplicável → P20 → contratos/saídas que a consumirem`.

Na primeira campanha real de Cotações em sombra, o fornecedor, owner de
tarifas e Negócio precisam fornecer as provas abaixo para o aceite P17/P20.
Elas não são pré-requisito para concluir código local independente. Antes da
primeira leitura, V2-041/G01, canal/runtime, entidade, janela, limites e alvo
de sombra ainda precisam de autorização e conferência explícitas:

- [ ] Release/contrato oficial do template 6906, com identidade, campos e filtros.
- [ ] Referência tarifária autorizada: rota, vigência, versão e owner.
- [ ] Janela real fechada de comparação e oráculo independente: origem, schema,
  grão, tolerâncias e resultado esperado.
- [ ] Autorização explícita do canal/fonte e do alvo sombra para a caracterização.

Quando esses quatro itens forem obtidos na campanha real, executar a rota
até o primeiro aceite ou divergência demonstrada. Não precisa aguardar Faturas, Fretes,
Inventário, Sinistros, MC, CF ou Raster.

### Rota 2 — Contas a Pagar (alternativa independente)

Também não espera Faturas ou Fretes. Ela precisa do contrato/identidade 8636,
da prova de raiz→parcela→rateio, da referência financeira/carteira, de uma
janela real e de oráculo do Financeiro.

### Rota financeira — Faturas

Continua separada: só reabre após condição externa nova registrada para a
sonda. Não deve ser usada para bloquear as rotas 1 e 2.

## Primeiro pedido fora do chat

Copie para o fornecedor/owner de Cotações, sem incluir token, URL sensível ou
dados de negócio no chat:

```text
Precisamos qualificar Cotações no ETL V2 em ambiente de sombra. Por favor,
forneça o release/contrato oficial do template 6906; a identidade e os campos
utilizados; a referência tarifária aprovada (rota, vigência, versão e owner); e
uma janela fechada com oráculo independente, schema, grão e tolerâncias.
Também confirme a autorização do canal de leitura e do alvo sombra para a
caracterização. Não precisamos de credenciais no material enviado.
```

## Checklist

### B16 — Verticais em sombra

#### Cotações — concluída por aceite interno de sombra em 22/09/2026

- [x] Aceitar contrato interno de sombra, identidade candidata e referências
  técnicas observadas, sem alegar contrato oficial do fornecedor.
- [x] Comparar com o código existente e corrigir somente diferença demonstrada.
- [x] Validar a vertical afetada, deixando claro o limite entre teste local e
  fonte real.

Contrato/release do fornecedor, tarifa e oráculo seguem pendências de P17 e não
autorizam paridade, publicação ou produção. Decisão em
`docs/continuidade/decisoes/2026-09-22-b16-cotacoes-aceite-interno-sombra.md`.

#### Demais entidades

- [x] Dispensar contrato, identidade e referências oficiais do fornecedor como
  pré-requisito genérico do aceite interno de sombra.
- [x] Concluir o aceite interno de sombra de cada entidade somente após sua
  evidência técnica própria estar registrada e validada.
  - [x] Contas a Pagar — aceite interno de sombra registrado em `STATES.md` em
    22/09/2026; `ExpansionLaboratoryContractTest` passou 15/15 offline. Não
    conclui identidade de raiz/linha, referência financeira, P17 ou paridade.
  - [x] Usuários — aceite interno de sombra registrado em `STATES.md` em
    22/09/2026; testes dirigidos de extração GraphQL e runtime passaram 45/45
    offline. Não conclui completude do snapshot, P17 ou paridade.
  - [x] Manifestos — mapper local passou 3/3; crosswalk, MDF-e, frota e
    paridade reais seguem pendentes.
  - [x] Coletas — mapper local passou 27/27; snapshot completo, COL-07 e
    relações reais seguem pendentes.
  - [x] Fretes — mapper local passou 10/10; relações CF e semântica financeira
    e fiscal reais seguem pendentes.
  - [x] Localização de Cargas — mapper local passou 10/10; frescor e vínculo
    Frete reais seguem pendentes.
  - [x] Faturas por Cliente — invariantes sintéticos dirigidos passaram no
    `ExpansionLaboratoryContractTest`; identidade e fiscal reais pendentes.
  - [x] Inventário — invariantes sintéticos dirigidos passaram no
    `ExpansionLaboratoryContractTest`; raiz/componentes reais pendentes.
  - [x] Sinistros — invariantes sintéticos dirigidos passaram no
    `ExpansionLaboratoryContractTest`; identidade e tempo reais pendentes.
  - [x] Raster condicional — parser, transporte e artefato passaram 29/29;
    canal, identidade e completude reais pendentes.

Entidades: Usuários, Manifestos, Coletas, Fretes, Localização, Contas a Pagar,
Faturas, Inventário, Sinistros e Raster condicional.

A dispensa não conclui nenhuma vertical automaticamente e não autoriza P17,
paridade, publicação ou produção. Política em
`docs/continuidade/decisoes/2026-09-22-b16-demais-entidades-aceite-interno-sombra.md`.

### B17–B20 — Dados reais e paridade core

- [ ] Receber janela real fechada e oráculo independente da entidade (P17).
- [ ] Receber/autorizar T0, Tcut, partições, fonte histórica e orçamento, quando
  histórico for aplicável (P18).
- [ ] Receber crosswalk, cardinalidade, política de órfãos e SLA para relações
  Manifesto→Coleta e/ou Coleta→Frete usadas (P19).
- [ ] Executar comparação real set-based e obter classificação/aceite das
  divergências (P20).

### B21 — Dimensões

- [ ] Receber cadastros, bindings, grão, vigência, rekey e regras de conflito.
- [ ] Receber IDs estáveis e regras aprovadas de frota para Veículos/Motoristas.
- [ ] Qualificar somente após a paridade core das fontes necessárias.

### B22–B23 — Ausência e aplicação

- [ ] Receber política nominal de ausência: completude, raiz/filhos, confirmação,
  limites e reativação (P22).
- [ ] Obter autorização específica, snapshot completo e ambiente qualificado
  antes de qualquer apply de exclusão lógica (P23).

### B24 — Fatos analíticos

- [ ] MAT-01: Fretes + Localização + regras de pagador/documentos de filial.
- [ ] MAT-02: Fretes + Manifestos + Inventário + aliases/filiais.
- [ ] MAT-03: Fretes + Localização + Faturas + calendário/filial.
- [ ] MAT-04: Faturas + grão de título + precedência fiscal.
- [ ] MAT-05: Manifestos + Coletas + Fretes + frota + relações MC/CF usadas.

### B25–B26 — Consumo e paridade analítica

- [ ] Receber manifesto aprovado, versão e responsável para as 18 saídas externas;
  definir aceite interno de SQL-10 (P25).
- [ ] Receber oráculo nominal por saída e aceitar/corrigir divergências de somas,
  fiscal, relações, labels e filtros (P26).

### B27 — Escala

- [ ] Receber volume/distribuição realistas, budgets, SLO e autorização de
  campanha finita.
- [ ] Medir heap, lotes, SQL/IO e duração; investigar apenas gargalo observado.

### B28 — E2E e recuperação

- [ ] Receber ambiente, workload e autorização para durabilidade, COMMIT,
  crash/restore e recuperação.
- [ ] Qualificar E2E, replay, cancelamento, concorrência e recuperação.

### B29 — Release candidate

- [ ] Receber RC exato, proveniência, SBOM, licenças, SOs suportados, política
  SAST completa e aceite de Segurança/Release.
- [ ] Executar gates do RC na mesma revisão; PMD não é SAST integral.

## Pendência financeira

- [ ] Registrar condição externa nova e identificável antes de nova consulta.
- [ ] Somente sob ordem própria, usar a sonda allowlisted nos limites de
  `AGENTS.md`.
- [ ] Obter evidência para relação Frete–Coleta, receita, CT-e e Fatura.

A última tentativa parou corretamente em `HTTP_NON_2XX`; Coletas terminou,
Fretes não terminou e 4924/GraphQL não foram chamados. Não repetir por rotina,
nem usar outro IP, FTP, fallback ou retry.

## Arquivos principais

1. `AGENTS.md`
2. `STATES.md` — cabeçalho vigente e fatia do bloco
3. `../CONTEXTO_GLOBAL.md`
4. `TRILHA_CONCLUSAO_POR_MODELO.md` — seções 6, 9, 11 e 13
5. `docs/continuidade/RETOMADA.md`
6. `docs/runbooks/continuidade-agentes.md`
7. `docs/continuidade/qualificacao-p07-p33/matriz.json` — somente fatia afetada
8. Este arquivo: `BLOCOS_ETAPA_2.md`

## Prompt para copiar no novo chat

```text
Execute um único bloco da etapa 2 do ETL Data Export V2.

Workspace: C:\Users\suporte\Documents\projetos\etl-dash\etl-extracao-dados-v2

Leia integralmente AGENTS.md, STATES.md (cabeçalho e fatia afetada),
../CONTEXTO_GLOBAL.md, TRILHA_CONCLUSAO_POR_MODELO.md (seções 6, 9, 11 e 13),
docs/continuidade/RETOMADA.md, docs/runbooks/continuidade-agentes.md,
docs/continuidade/qualificacao-p07-p33/matriz.json somente na fatia afetada e
BLOCOS_ETAPA_2.md.

Escolha e execute a menor rota por entidade de BLOCOS_ETAPA_2.md que tenha
input novo, concreto e autorizado; priorize a rota de Cotações se o pacote
solicitado estiver disponível. Não invente contrato, identidade, oráculo, relação,
referência, regra de negócio, aprovação ou autorização externa. Execute todas
as partes independentes do bloco, sem pedir "continue".

O V2 continua em sombra: não faça banco produtivo, DDL/DML produtivo, deploy,
agenda, cutover, mudança de credencial, controle do ETL de produção ou acesso
externo fora das autorizações registradas. Não repita sonda financeira sem
condição externa nova registrada antes do efeito.

Preserve mudanças preexistentes. Antes de editar, identifique o delta real e
suas dependências. Se houver alteração, atualize STATES.md primeiro, depois
trilha/validadores aplicáveis, crie checkpoint e por último RETOMADA.md. Marque
em BLOCOS_ETAPA_2.md somente o que estiver comprovado no STATES.md.

Entregue alterações, testes realmente executados, resultado por camada,
pendências com input/dono exatos, situação financeira e checkpoint seguinte.
Não execute P30–P33.
```
