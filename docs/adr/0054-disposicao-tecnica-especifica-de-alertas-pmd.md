# 0054 — Disposição técnica específica de alertas PMD

Data: 22/09/2026. Estado: decisão técnica local; aceite nominal de Segurança e
gate de release V2-015c permanecem pendentes.

## Contexto

O pedido efetivo desta rodada exige tratar os alertas existentes, testar falsos
positivos e requalificar P07/P08, preservando sanitização e sem criar baseline
automática. O PMD local executa dez regras e conserva seus alertas brutos. Algumas
regras não reconhecem transferência de ownership, aliases de causas e tradução
deliberada de exceções sensíveis. O relatório0236 contém37 alertas remanescentes,
individualmente triados; a triagem também levou a defeitos concretos adjacentes.

Trocar uma exceção sanitizada pela causa original somente para calar o PMD
reintroduziria dados sensíveis. Excluir classes, diminuir regras, adotar um número
máximo de alertas ou aceitar automaticamente todos os alertas presentes esconderia
regressões. Essas alternativas foram rejeitadas.

## Decisão

Preservar o executor PMD e sua saída `FINDINGS_OPEN`/exit1 quando houver alertas.
Não adicionar supressões ao código, ao XML ou ao scanner. Acrescentar uma
verificação separada de disposição técnica local, sobre um catálogo autorado
alerta por alerta. Seu resultado não altera nem reescreve o relatório bruto.

Cada disposição identifica arquivo, linha, regra e método; fixa os bytes do
fonte, a classificação, a justificativa específica e o limite da prova. Vincula
testes por arquivo/hash/classe/método. A verificação confronta esses hashes com
o workspace e com a cópia que produziu os XMLs, exige casos executados sem falha,
erro ou skip, e verifica todas as invocações parametrizadas correspondentes.
Ausência de teste não pode ser compensada por texto de justificativa.

O catálogo deve corresponder exatamente ao conjunto observado. Alertas novos,
ausentes, duplicados ou alterados; drift de fonte/teste; relatório incompleto;
supressão; erro do analisador; ou evidência inválida impedem a aprovação. Não
existe modo de gerar/aceitar baseline a partir de qualquer relatório recebido.
Uma disposição desatualizada exige nova revisão do caso e nova prova pertinente.

`PASS_LOCAL_REVIEWED_FINDINGS` significa apenas que as disposições técnicas
específicas conferem. Ele pode compor a revisão local P06/P07 junto do relatório
bruto, dos demais gates e da qualificação física. O resultado registra sempre
`nominalSecurityAcceptance=false` e `releaseAcceptance=false`.

## Alcance e limites

As dez regras não constituem SAST integral. Não demonstram análise abrangente de
fluxos de entrada em SQL/shell/URL, SSRF/TLS, serialização/XML, caminhos de arquivo,
PowerShell ou SQL. A ferramenta não ratifica uma política corporativa de SAST,
suas exceções nem identidade/aprovação humana. V2-015c/P29 continua exigindo o
escopo integral, SBOM/licenças/proveniência, smoke nos SOs suportados e demais
aceites da mesma revisão de release. G01 e os gates externos continuam vigentes.

Responsável técnico: repositório ETL V2. Aceitante nominal de Segurança: não
fornecido; nenhum nome ou aceite inferido. A decisão não muda regra de negócio,
dependência, schema, credencial, ambiente ou autorização de produção.

## Validação e recuperação

O autoteste do novo verificador deve provar aprovação de disposição específica e
recusa de deriva, achado extra/omitido, evidência de teste falha/ausente/skip,
supressão e relatório adulterado. A execução real precisa ligar fontes finais,
relatório PMD e XMLs efetivos da mesma revisão; a existência desta ADR não prova
que esses testes passaram. Resultados executados ficam no catálogo de
continuidade `qualificacao-p07-p33` e na rodada privada correspondente.

Recuperação: retirar a composição nova e voltar ao resultado bruto bloqueante,
preservando relatórios, catálogo e snapshots. Não alterar manifests históricos
nem apagar falhas para obter aprovação.
