# Bloco 58 — comparação local de Coletas e Usuários

Escopo adotado pelo usuário em 09/09/2026, conforme
`target/preparacao-bloco58/PROMPT-BLOCO-58.md`. Evidência exclusivamente sintética.
O estado final depende do recibo `target/bloco58-local/final/receipt.json`;
ausência ou falha desse recibo significa fechamento pendente.

Consumidores em `src/test/.../contratos/bloco58`, com pontes mínimas nos pacotes
dos parsers. Expectativas literais em `src/test/resources/contracts/bloco58`,
vinculadas ao hash da decisão. Nenhum perfil Q-FND anterior é alterado.

## Coletas: matriz de diferenças e alcance

| Campo/regra | Perfil histórico Q-FND | Decisão atual / saída esperada | Contrato e camada da prova |
| --- | --- | --- | --- |
| COL-01 id | INTEGER canônico | Zero/negativo permitidos, tag INTEGER; não usar alias como chave | Parser estrito → codec → registro tipado; contrato canônico exige id integral não nulo |
| sequence_code | VALUE obrigatório | ABSENT/NULL/VALUE preservados; alias separado | Canônico exige VALUE; diferença explícita antes do staging |
| updated_at | VALUE obrigatório; path temporal | Não participa do frescor COL-02 | Obrigatório no contrato canônico; ausência não invalida o mapper |
| pck_mik_mft_sequence_code | NULL/VALUE | Candidato com ABSENT/NULL/VALUE e proveniência | Canônico exige presença, embora nullable; sem vínculo inferido |
| status | Catálogo fechado, desconhecido recusado | Bruto desconhecido preservado, código/label nulos, sem terminalidade inferida | Mapper e simulação da decisão; path ausente no release canônico |
| done / finished | Terminais conhecidos | Coletada / Finalizada, ação Coleta Realizada e uma tentativa | Comparação literal do registro; não prova reducer persistido |
| canceled / cancelled | Terminais | Cancelada; motivo não vazio precede ação genérica | Comparação literal incluindo prioridade de done/finished |
| COL-02 frescor | Ordem de quatro paths | status_updated_at → finish_date → service_date → request_date; só valor válido | Parser/mapper e pipeline da fixture local; canônico recusa paths adicionais |
| Datas com espaço | Sem garantia externa nova | Calendário estrito; bruto inválido intacto; fallback válido | RED de três formatos preservado e correção mínima no mapper |
| Zona / offsets | America/Sao_Paulo; bordas não provadas | ISO/offset/local nos formatos existentes, ano bissexto válido | Não decide DST, precisão/bordas inclusivas ou watermark remoto |
| COL-07 candidatos | Presença/proveniência | Zero, nulo, ausência e objeto preservados | Staging simulado; nenhuma relação, FK ou dedupe na JVM |
| Expansão / per | IDs distintos, sem short-page terminal | Linhas físicas repetidas permanecem até staging; per por id distinto | Normalizer/limit validator/guard/streamer/caso de uso atuais |
| Cap / erro / cancelamento | Fail-closed | Tentativa incompleta não recebe evidência de promoção | Falhas antes/depois de staging; páginas curtas continuam |

Três observações são nomeadas separadamente nos relatórios:

1. **parserMapper** usa os bytes do envelope original e o mapper atual.
2. **firstWaveContractTraversal** usa o release produtivo de
   DataExportFirstWaveContractCatalog, sem allowance adicional. Suas recusas
   permanecem visíveis como DIVERGED_BEFORE_STAGING quando o domínio aceita.
3. **decisionFixtureTraversal** usa um schema fixo, exclusivamente de teste,
   declarando os paths/tipos/presenças do perfil e ADR0023. O profiler deriva a
   observação do envelope original, e o guard real a valida. Essa fixture não é
   contrato ESL, não substitui o release canônico nem habilita o runtime.

O terceiro caminho isola a ligação entre domínio e staging para testar os campos
que o release canônico ainda não admite. A primeira recusa, contagens de mapeados/
stageados e comparações executadas impedem atribuir sucesso ao mapper não atingido.
Uma contraprova exige divergência quando se espera entrada válida e o contrato a
recusa. Diferenças contratuais não foram corrigidas sem evidência/decisão da fonte.

## Usuários e regressão

Usuários usa seu parser e gate GraphQL existentes, sem passar pelo parser Data
Export. O release atual declara name opcional/nullable, ao contrário do perfil
histórico que só admite NULL/VALUE. Ausência é comparada ao registro efetivamente
entregue pelo caso de uso, preservando INTEGER versus STRING.
Current/history, conflito/dedupe e preservação do valor persistido são SQL e
continuam fora da prova em memória. Operação individual(enabled=true), id/name,
pageInfo, máximo 20; observed_at é técnico, sem updatedAt/template/executor novo.

| Campo/regra Usuários | Perfil histórico / contrato | Expectativa independente e camada |
| --- | --- | --- |
| id INTEGER / STRING | Ambos permitidos | Tag e valor exato distintos, inclusive zero/negativo e texto com zero inicial; mapper e staging |
| Identidade limitada | Codec até 256 unidades UTF-16 com tag | Fronteiras 248/249 dígitos e 249/250 caracteres; excesso vai à quarentena do mapper |
| Identidade inválida | Envelope exige integral ou texto não vazio | Ausente/nulo/decimal/boolean/objeto/array recusados no parser; teste isolado do mapper mostra o motivo de quarentena |
| name ABSENT | Perfil só NULL/VALUE; release opcional | ABSENT é aceito no mapper e no staging; não é prova de conservar estado SQL anterior |
| name NULL / VALUE | Nullable STRING | Nulo, vazio, espaços, acentos, combinantes e astral preservados literalmente |
| name comprimento / Unicode | Domínio V2-033 | Até 255 unidades UTF-16; 256, controles ou surrogate isolado em quarentena; sem truncamento |
| name tipo inválido | Contrato permite apenas STRING ou nulo | Recusa CONTRACT antes de staging; mapper isolado produz INVALID_NAME_TYPE |
| errors / edges / node / pageInfo | Envelope Relay exato | Qualquer errors, shape inválido, cursor requerido ausente ou mais de 20 nodes recusa PARSER |
| Campos não solicitados | Somente id/name | Campo temporal adicional recusa CONTRACT; não vira frescor da fonte |
| Cursor / término / falha | Cursor só na travessia | Repetição/ciclo/cap, cancelamento e staging falho deixam evidência incompleta; false encerra só localmente |
| USR-01 / USR-02 | Current/history e relações set-based | Somente identidade/presença e bloqueio da execução incompleta exercitados aqui; efeito SQL não alegado |
| USR-03 / USR-04 | GraphQL transitório, sem fonte temporal | Operação existente preservada e manifests estáticos conferidos; sem executor ou fonte inventados |

Os relatórios usam firstRefusal para a primeira interrupção do fluxo. Quarentena
é uma saída tipada do mapper: aparece no contador e em quarantineLayer=MAPPER,
inclusive quando o staging aceita esse registro em quarentena. Nenhum valor de
nome, identidade ou cursor é emitido. Contagens são apenas da fixture limitada.

B57 continua regressão: 109 casos COT/LOC/FRE, com entradas String/JsonNode e
contrato LOC distintos. UNVERIFIED_NUMERIC_WIRE_LEXEME é preservado. Manifestos
participa só da regressão dos componentes usados; Q-MAN-01 permanece EXTERNAL_HOLD.

## Dependências externas preservadas

| Gate | Artefato que falta | Papel que pode fornecer / desbloqueio |
| --- | --- | --- |
| Q-COL-01 / Q-USR-01 e V2-012a | Oráculo independente por entidade, autorização do owner, janela representativa e bindings/scopes reais; schema/receipt definidos no [catálogo Q-FND-01](../caracterizacao-v2-012/README.md). Arquivos reais ainda não fornecidos | Owner ESL e do domínio, Segurança/Operações; libera caracterização real específica, incluindo tipos/nullability e garantias de origem |
| Q-LOC-01 / Q-FRE-01 | Oráculos representativos e canais vinculados aos [perfis Q-FND-02](../caracterizacao-v2-012/q-fnd-02/manifesto.json); não substituir os arquivos reais ausentes por fixtures | Owner da fonte e do domínio, Plataforma de Dados; libera caracterização real específica |
| Q-COT-01 | Cópia privada adotada do [input pendente](../bloco57-complemento/cotacoes-input.pendente.json): host/sourceInstance/tenantScope, janela/oráculo, garantias temporais, atestado V2-041 e adoção específica | Papéis do [pacote B57](../bloco57-complemento/README.md); completar a entrada real sem mudar o público ou renovar tetos |
| V2-012b / V2-047 / V2-046 | Plano por entidade conforme [matriz de bootstrap](../bootstrap-v2-047/matriz-bootstrap-v01.csv), histórico autorizado, conjuntos/totais independentes e relações comprovadas | Owner da fonte e Plataforma de Dados; bootstrap e comparação set-based no SQL |
| V2-012c / DoD | Evidências reais por saída e aceite nominal das divergências nos critérios [V2-012c/DoD do STATES](../../../STATES.md) | Owner consumidor e Plataforma de Dados; paridade das saídas após qualificação core |
| V2-041 | Atestado real sanitizado conforme [contrato de intake](../evidencia-rotacao-v2-041/README.md), acompanhado de validação de autenticidade pelo owner | Segurança/Operações e owner; remove somente a precondição de rotação, sem autorizar execução |
| P08–P11, FAT-02, V2-041 | Identidade/grão/estabilidade/crosswalks, decisão fiscal e prova de rotação/invalidação | Papéis já registrados nos respectivos gates; nenhuma investigação reaberta |

Não há responsável nominal novo inventado. O executor COT, pasta de uso único,
vigência e tetos B57 permanecem intactos e não foram executados.
67/115 aceites, 48 pendentes, 191 rotas abertas; testes locais não elevam percentual.

## Resultados locais e revisão

Verify final offline: **1.303 testes, zero falhas/erros, cinco skips**. Enforcer,
Spotless, Checkstyle e JaCoCo passaram em Java 17, heap máximo 512 MiB. Skips:
três condições de symlink, um recibo V2-050 opt-in e o comando COT desabilitado.
Prova: [java-result.json](../../../target/bloco58-local/java-result.json), com
800 bindings de arquivos de entrada, 206 artefatos e motivos completos dos skips.
Testes focados A/B/C: 98/94/124, respectivamente; as contagens se sobrepõem e não
devem ser somadas à suíte. Oito gates estáticos de contrato/identidade/verticais
e Q-FND passaram. Houve nove contraprovas atuais e 15 históricas de sucessão.
Scanner offline: zero findings; autoteste: 11 casos.

Dos 48 casos Coletas, 38 atingem staging na fixture da decisão; seis são recusados
pelo contrato e quatro no limite verificável de entidades. Em 35 casos o release
canônico diverge da decisão local antes do staging, sem correção por hipótese.
Dos 42 Usuários, 30 atingem staging (incluindo quarentenas tipadas), quatro são
recusados pelo contrato e oito pelo parser Relay. Duas contraprovas independentes
produzem DIVERGED como esperado. Contagens e hashes estão no
[resumo dos relatórios](../../../target/bloco58-local/report-summary.json).

Único defeito produtivo confirmado: três formatters de Coletas normalizavam datas
impossíveis. RED de três testes preservado; calendário estrito agora mantém o
bruto inválido e permite fallback válido. Nenhum defeito produtivo adicional foi
confirmado em Usuários. Fixtures e perfis históricos seguem intactos.

O [manifesto B58](manifesto.json) sucede exatamente sete arquivos existentes,
preservando seus bytes iniciais em snapshots e os outros 1.481 arquivos. O
[diff próprio](../../../target/bloco58-local/final/own.patch), o
[diff de código](../../../target/bloco58-local/final/code.patch) e a
[recuperação](../../../target/bloco58-local/final/RECUPERACAO.md) usam a base inicial
da rodada, preservando alterações preexistentes em relação ao Git HEAD.
O estado final só está comprovado quando o
[recibo final](../../../target/bloco58-local/final/receipt.json) estiver passed=true,
com hashes íntegros do último delta e reverse --check aprovado, sem aplicação.
