# Bloco 57 — comparação local entre envelope, parser e mapper

Escopo adotado pelo usuário: A Cotações, B Localização, C Fretes e D investigação
documental dirigida. Evidência exclusivamente sintética; V2-012a e Q-COT-01,
Q-LOC-01, Q-FRE-01 permanecem abertas. Windows/SQL continua a direção técnica.
Nenhuma API, SQL, campanha física, instalação, credencial ou orçamento é utilizado.

O consumidor `MapperCharacterization` fica em `src/test`. Ele aplica o loader
UTF-8 e seus limites estruturais e depois relê os bytes originais com o mesmo
`DataExportStrictJsonParser` utilizado pelo gateway. `MapperProjection` chama o
mapper atual e mantém valores somente durante a comparação de uma linha.
Expectativas em `src/test/resources/contracts/bloco57` são literais
independentes do mapper, vinculadas por SHA-256 às decisões de cada vertical.

Cada caso exige resultado explícito de quarentena e número exato de linhas.
Quarentena inesperada diverge; recusa esperada é compatibilidade local, nunca
registro válido. O relatório diferencia MATCH, DIVERGED e INCOMPLETE, com
contagens de presença/tipo, comparações e motivos por campo/regra. Não contém
valores esperados/observados, payload, IDs, hashes de IDs ou mensagens de exceção.

Limites por envelope: 65.536 bytes, profundidade 16, 256 ocorrências de nomes de
campos e 4.096 nós. Limites por caso: 1.000 linhas e 100 páginas declaradas.
Uma página por vez; não acumula registros entre páginas. A conclusão de um caso
local significa apenas ter recebido todas as páginas declaradas da fixture.
Página curta/vazia não prova terminalidade ESL, snapshot, exclusão ou completude.

## Cotações: diferença de cobertura entre versões

Contrato histórico: `contratos-esl-6906/manifesto.json`, perfil Q-FND-01 de sete
paths. Decisão atual: `cotacoes-v2-027/decisao-v01.json`, nove campos. Os 43
arquivos do lock Q-FND-01 e todos os artefatos Q-FND-02 permanecem históricos.

| Path na linha | Q-FND-01 | Decisão/mapper atual | Saída observada localmente | Classificação |
| --- | --- | --- | --- | --- |
| `/sequence_code` | INTEGER | COT-01; inteiro positivo signed 64-bit | source key type-tagged | Identidade local escopada; sem unicidade global |
| `/requested_at` | STRING/VALUE | COT-01; presença tri-state | Instant UTC, frescor fallback | Sem garantia remota de bordas/fuso |
| `/qoe_qes_fit_nse_issued_at` | STRING | COT-01 | Instant UTC; primeira precedência | Decisão local |
| `/qoe_qes_fit_fhe_cte_issued_at` | STRING | COT-01 | Instant UTC; segunda precedência | Decisão local |
| `/qoe_qes_total` | STRING/INTEGER/DECIMAL | COT-01; DECIMAL(19,4) | BigDecimal, zero preservado | Contrato tipado local; moeda não inferida |
| `/qoe_crn_psn_nickname` | STRING | Presença/payload preservados | Sem propriedade tipada própria | Não inventar normalização/alias |
| `/qoe_uer_name` | Ausente | COT-01; DE-DATA-6906-017 | Texto trim/NFC para exibição | LOCAL_DTO_DATA_PATH_CANDIDATE |
| `/qoe_qes_ony_sae_code` | Ausente | COT-02; DE-DATA-6906-007 | Duas letras ASCII maiúsculas | LOCAL_DTO_DATA_PATH_CANDIDATE |
| `/qoe_qes_diy_sae_code` | Ausente | COT-02; DE-DATA-6906-009 | Duas letras ASCII maiúsculas | LOCAL_DTO_DATA_PATH_CANDIDATE |
| `/requester_name` | STRING | Não lido como usuário atual | Não substitui `/qoe_uer_name` | Diferença de versão; sem crosswalk/alias |

UF sintaticamente válida não é rota tarifária coberta; tarifa, vigência, moeda e
ratificação continuam no SQL/referência e não são simuladas como aceitas aqui.
Datas impossíveis devem ser recusadas. Fuso de data civil e gap/overlap COT ainda
carecem de garantia específica; comportamento da biblioteca não é contrato ESL.

## Localização e Fretes

Localização: os 17 paths de Q-FND-02 correspondem ao mapper V2-028. Faltava
produzir observações a partir do envelope, não um novo perfil remoto. O consumidor
recorta a linha original após validação estrita e usa a entrada String existente:
`-0`, expoentes e zeros de escala continuam disponíveis para a política LOC-04.
27 casos com a decisão LOC-01–LOC-07 passaram. Zero permanece valor local; o
fallback por Frete é apenas política diferida, sem relação. Desconhecido segue
a decisão de preservar bruto e não inferir terminalidade; publicação é outro gate.

Fretes: Q-FND-02 declara sete paths de Data Export e dez de GraphQL sidecar. O
mapper Data Export preserva 18 campos: os sete históricos, mais status, os quatro
instantes de frescor, CT-e/finalizações e total sintéticos V09. Os 43 casos FRE
comparam presença, chave, frescor, performance oficial/fallback, status e limites
financeiros locais. O sidecar permanece ABSENT, sem seleção ou request GraphQL.
Financeiro é preservação tipada; moeda, cálculos, relação e crosswalk não são criados.

## Diferenças reproduzidas e correções

| Reprodução | Regra/contrato | Antes | Correção mínima | Evidência |
| --- | --- | --- | --- | --- |
| COT máximo DECIMAL(19,4) como número JSON; zero/escala | COT-01, DoD 2, precisão sem conversão silenciosa | Double perdia precisão/escala; valor válido podia ser quarentenado | Parser Data Export usa BigDecimal e preserva escala | test-A-red-01 / test-A-green-01 |
| Data impossível com espaço em COT | COT-01, INVALID_COTACAO_DATE | Formatter SMART ajustava dia inválido | uuuu e ResolverStyle.STRICT | Mesmo RED/GREEN A |
| Data impossível com espaço em frescor/performance FRE | FRE-02/FRE-07 | Dia inválido podia ser aceito | Mesmo ajuste restrito no formatter FRE | test-C-red-01 / test-C-green-01 |

Os logs citados estão em `target/bloco57-local`. O RED A também identificou uma
expectativa errada do teste para MissingNode; somente essa expectativa foi
corrigida e sua versão anterior foi preservada. O mapper de Localização não foi
alterado. Nenhum teste local valida comportamento real da ESL ou o JAR instalado.

## Execução reproduzível e fonte futura

Verify final offline: **1.150 testes, zero falhas/erros e quatro skips condicionais**,
em Java 17/heap 512 MiB, com Enforcer, Spotless, Checkstyle e JaCoCo aprovados.
São três testes de entidade com 109 casos e nove testes de limites/diagnósticos.
Os relatórios finais totalizam 58 linhas válidas e 51 recusas esperadas, sem
divergências; a classificação da prova permanece SYNTHETIC_LOCAL_PARSER_MAPPER.
[Resumo Java e hashes dos relatórios](../../../target/bloco57-local/java-result.json).
O recibo de fechamento vincula os logs finais estáticos; não confundir os logs
vermelhos preservados com o resultado do conjunto corrigido.
Dez checks finais estáticos passaram: fundações, sucessão, ESL, B55 privado,
continuidade, trilha, seis contraprovas, pacote bloqueado e scanner sem achados.
Os quatro skips Java são três provas de symlink indisponível e um recibo de
medição V2-050 que exige opt-in; esse perfil não foi ativado neste bloco.

```powershell
pwsh -NoProfile -File scripts/validation/Invoke-Bloco57OfflineVerify.ps1
pwsh -NoProfile -File scripts/validation/Test-Bloco57Local.ps1 -IncludePrivateEvidence
pwsh -NoProfile -File scripts/validation/Test-CotacoesCharacterizationPackage.ps1
```

O primeiro comando roda `verify` offline em Java 17, com POM temporário equivalente
e saída isolada, sem clean/perfis externos. O segundo confere sucessão e hashes;
o terceiro confere o pacote preparado e retorna BLOCKED_NOT_EXECUTED. Não há
comando remoto liberado. `ContractRemoteEvidenceRunner` só cobre os contratos
históricos 6908/6389 e auxiliar 4924; não foi ampliado por este bloco.

[Investigação dirigida](investigacao-dirigida.md) e
[entrada futura exata](cotacoes-fonte-futura.json): host lógico, source_instance,
tenant_scope, janela representativa, tradução temporal, atestação e adoção
adicional estão explicitamente ausentes. O adaptador futuro deverá ligar somente
a aquisição autorizada ao consumidor de comparação; não herdar orçamentos das
onze chamadas anteriores. O comando local não depende desses inputs.

## Preservação e recuperação

Base inicial: 1.434 arquivos em `target/bloco57-local/initial-inventory.json`,
copiados byte a byte em `target/bloco57-local/initial`. Os artefatos históricos
não são regenerados. A sucessão registra cada arquivo alterado com hash antes,
depois e cópia histórica. Logs de erro e relatórios intermediários permanecem.
Restaurar somente deltas próprios depois de comparar os hashes e verificar
edições posteriores; nunca limpar target, restaurar banco ou substituir JAR
protegido. O build final usa POM equivalente e heap de 512 MiB, com saída em
`target/bloco57-local/build-final`. O POM canônico permanece idêntico; os POMs
temporários são removidos após conferir seus hashes e guardados nas evidências.

[Diff próprio](../../../target/bloco57-local/final/own.patch),
[recuperação](../../../target/bloco57-local/final/RECUPERACAO.md) e
[recibo final](../../../target/bloco57-local/final/receipt.json) com inventário e
hashes: a ausência/falha desse recibo significa fechamento pendente. O manifesto
versionável registra sete deltas exatos; seus snapshots preservam as revisões
anteriores, sem exceções por diretório nem alteração de checkbox histórico.
