# Qualificação local P07–P33

**P07 está concluído na camada física local:** 2.263 testes unitários, quatro skips históricos preservados e 492 testes de integração em 105 classes, com cobertura, identidades, multiplicidades e rollback aprovados. As 77 regressões exigidas e os 2.096 arquivos de runtime conferidos estão vinculados às provas executadas. Os indicadores permanecem em **39/45 de construção e 67/115 de aceites históricos**, sem novo aceite nominal.

**P08 está concluído na camada local de pacote:** o [pipeline real](../../../target/qualificacao-p07-p33-20260922-01/physical/p08-result.json) terminou em `OBSERVED_PASS`, com 19 passos aprovados, dois ZIPs idênticos, smoke, oito guardas de controle, 21 de admissão, 25 de arquivo, sequências A/B, oito variantes e readback final. O [resultado consolidado](resultado.json) e a [matriz](matriz.json) vinculam essa qualificação às provas e à mesma revisão de P07. Integração pública e encerramento documental continuam sujeitos aos recibos finais próprios.

A matriz cobre **74 linhas**: 25 etapas P09–P33, 11 entidades, oito famílias de referências, seis dimensões, cinco fatos e 19 contratos SQL. Cada linha registra critério canônico, origem, prova existente, limite, requisito faltante, responsável por papel e ação de desbloqueio. Os **31 grupos externos** preservam os 16 requisitos históricos e 15 grupos adicionais; não são uma lista exaustiva nem novos checkboxes. A fotografia histórica de 0235 permanece intacta.

## Alterações e evidência executada

| Trabalho | Resultado comprovado | Limite |
| --- | --- | --- |
| Recursos JDBC e orçamento | Correções de liberação, falha primária, supressão de falhas de limpeza e fechamento concorrente único em quatro classes. | Testes locais não representam todos os drivers, falhas da JVM ou recuperação material. |
| Captura analítica | `LocalAnalyticQuotesRuntime` preserva a mesma exceção e sua causa na variável compartilhada e no lançamento. | Não se alegou injeção específica de falha em SQL begin. |
| Regressões dirigidas | 285 casos em 23 classes, sem falha, erro ou skip; 77 novos casos registrados. | A prova dirigida foi complementada pelo P07 completo. |
| P07 físico | 2.263 unitários, quatro skips históricos, 492 ITs em 105 classes; cobertura e rollback aprovados. | Qualificação sintética em SQL Server local; não comprova paridade real de fornecedor ou ambiente produtivo. |
| PMD e disposição específica | 653 fontes, dez regras e 36 achados; `PASS_LOCAL_REVIEWED_FINDINGS` conferido sobre os 251 XMLs/2.263 casos unitários do build P07. | Bruto preservado em `FINDINGS_OPEN`, saída 1, sem supressão. Aceites de Segurança e release continuam falsos. |
| Contraprovas do gate PMD | 40 cenários sintéticos aprovados, incluindo drift de fontes, relatório de outro build e falha/skip entre invocações repetidas. | Testam a validação da evidência; dez regras PMD não equivalem a SAST integral. |
| Recibo do scanner | Corrigida a contagem fixa de 17 para os 18 casos existentes. RED: casos corretos e agregado falso; GREEN: mesmos casos e agregado verdadeiro. | Consistência do recibo local; não é Gitleaks remoto ou aceite nominal de Segurança. |
| Pacotes P08 | Reprodução byte a byte: 730 membros, nove dependências e 7.943.947 bytes em cada ZIP. Smoke e 8 + 21 + 25 guardas aprovados; pipeline completo com 19 passos e readback final aprovado. | Qualificação local do pacote; não constitui release produtiva, publicação remota ou aceite operacional. |
| Sequências e variantes P08 | A e B: sete etapas, 133 comparações e 231 previews cada, com rollback. VALUE, PRECISION, KEY, MULTIPLICITY e OLD_REFERENCE recusaram com saída esperada 40; MISSING_USER, PIN_DRIFT e COMMAND com saída esperada 20. Todas as oito variantes passaram e confirmaram rollback. | Cenários sintéticos sobre o JAR distribuído; não substituem paridade real, escala representativa ou recuperação material. |

Os [recibos de P07](../../../target/qualificacao-p07-p33-20260922-01/physical/p07-regression.json), a [comparação exata com os predecessores](../../../target/qualificacao-p07-p33-20260922-01/physical/p07-exact-predecessor.json), a [reprodução dos ZIPs](../../../target/qualificacao-p07-p33-20260922-01/physical/reproduction.json) e a [disposição PMD sobre P07](../../../target/qualificacao-p07-p33-20260922-01/static-disposition-full-p07-01/result.json) sustentam essas contagens. O [ZIP privado](../../../target/macrobloco-qualificacao-pacote-20260913-01/pos0236-package-primary-01/qualification.zip) tem SHA-256 `ebe0021f2bf6fa016284ea09179dca38cd9b087ee5bf08026dc03eeafad4ff62`.

O [diff para revisão](../../../target/qualificacao-p07-p33-20260922-01/code-review-02.patch) e seu [inventário](../../../target/qualificacao-p07-p33-20260922-01/code-review-diff-02.json) preservam 24 arquivos de código, testes, scripts e ADR. A revisão independente de agente examinou cinco classes Java e dez arquivos de teste, sem demonstrar defeito novo nesse delta. Não houve revisão ou aprovação humana.

## Critérios P09–P33

| Etapa | Parcela local existente | Critério ainda aberto |
| --- | --- | --- |
| P09 | Intake e contraprovas de evidência de rotação. | Segurança/Operações: atestado G01 autêntico de rotação, invalidação e continuidade do único writer. |
| P10 | Workflows, política e validações locais. | Responsável pelo repositório: destino e conjunto aprovados, proteções e recibos reais de CI/Gitleaks no mesmo SHA. |
| P11 | Baseline corrigida de 13 dependências, zero achado ou erro. | Segurança: aceite nominal da baseline existente. |
| P12 | Runtime, retenção e recuperação locais. | DBA/Operações/Segurança/Compliance: ambiente, ACL/TLS, capacidade, retenção, backup/restore e RTO/RPO medidos. |
| P13 | Contratos e observações de origem preservados. | Fornecedor e responsável pelos dados: contrato autenticado válido, completude e tradução temporal; sondagem somente se necessária e autorizada. |
| P14 | Identidade da primeira onda e recusas dos casos sem garantia. | Fornecedor/Negócio: raiz e filhos, escopo, estabilidade, cardinalidade e semântica das fatias abertas. |
| P15 | Manifestos, importadores e provas sintéticas das oito referências. | Responsável por cada família e aprovador independente: conteúdo oficial, proveniência, vigência, paridade e ativação autorizada. |
| P16 | Onze verticais implementadas e provas locais rastreadas. | Responsável pela entidade: confronto com contrato, identidade e referências efetivos aceitos. |
| P17 | Harness e provas locais de caracterização. | Responsável pelos dados/Negócio: oráculo independente aprovado e janela representativa autorizada. |
| P18 | Bootstrap, namespaces e replay locais. | Responsável pelos dados/DBA/Operações: horizonte, T0/Tcut, partições, fonte consistente, orçamento e reconciliação reais. |
| P19 | Relações MC/CF e validações locais. | Responsáveis por Manifestos/Coletas/Fretes: correspondências, cardinalidade, órfãos, vigência e SLA ratificados. |
| P20 | Comparadores e provas locais de paridade. | Responsável pelos dados/Negócio: janelas reais fechadas e repetidas; divergências corrigidas ou nominalmente aceitas. |
| P21 | Seis dimensões construídas e consumidas localmente. | Responsáveis pelas dimensões/fontes: bindings, grão, vigência, ciclo de vida e paridade; IDs estáveis de frota. |
| P22 | 33 responsabilidades: 19 BLOCKED, cinco DISABLED, nove NOT_APPLICABLE e zero ENABLED. | Negócio e responsável pelos dados: aplicabilidade nominal, snapshot completo, presença de raiz/filhos, confirmações e limites. |
| P23 | Preview/apply e contraprovas locais. | Responsável pelos dados/DBA/Operações: P22 aceito, snapshot completo e autorização específica de apply. |
| P24 | Cinco fatos e testes locais. | Responsáveis pelos fatos/Negócio: paridade das entradas e regras ratificadas das dependências efetivamente usadas. |
| P25 | 19 contratos SQL locais: 18 externos e SQL-10 interno. | Responsável por cada consumidor: manifesto aprovado; responsável técnico de SQL-10: aceite interno próprio. |
| P26 | Comparadores analíticos e provas locais. | Negócio/consumidores/dados: oráculo aprovado por saída e paridade de grão, valores, relações, filtros e rótulos. |
| P27 | Instrumentação e harness de escala local. | Responsável pelos dados/DBA/Operações: volume e distribuição representativos, SLO, orçamento e campanha autorizada. |
| P28 | Cenários E2E e rollback locais. | DBA/Operações: agenda, carga e durabilidade aceitas; COMMIT/crash/restore e recuperação materiais requeridos. |
| P29 | P07, P08 e disposição técnica PMD concluídos, com pacote reproduzido e provas executadas. | Segurança/release: RC exato, CI, SAST integral, licenças, SOs, configuração e aceites nominais. |
| P30 | Preparação documental da unidade de corte. | Responsáveis pela operação/corte/consumidores: cobertura nominal integral, Tcut, writer único, fences, rota, PNR e recuperação. |
| P31 | Preparação documental do ensaio. | Responsáveis pela operação/corte: G06 e ensaio isolado executado, duas recuperações e RTO/RPO medidos. |
| P32 | Preparação documental do corte. | Responsável nominal pelo corte/consumidores: G07 datado, DoD integral, ensaio aceito e corte autorizado. |
| P33 | Preparação documental da retirada. | Responsáveis pelo legado/consumidores/Operações: G08, cortes aceitos, observação, retenção, destinos e retirada autorizada. |

O código atual foi qualificado por P07. As provas históricas conservam seu próprio alcance, sem serem reapresentadas como novas execuções. P30–P33 continuam preparação documental; procedimentos, inventários e simulações não comprovam ensaio material, corte ou retirada.

## Conteúdo externo e independência das fatias

A [pesquisa dirigida já executada](../../../target/qualificacao-p07-p33-20260922-01/input-search-current.json) examinou 37 documentos e não localizou G01 nem novo aceite externo nesse conjunto; os pins anteriores permaneceram estáveis. Essa conclusão está limitada ao conjunto consultado. A ausência foi registrada como dependência concreta, sem repetir a busca ou tratá-la como entrega.

As oito famílias de referências têm requisitos próprios: `CALENDAR`, `PICK_STATUS`, `BRANCH_OPERATIONS`, `OWNED_FLEET`, `BRANCH_ATTRIBUTION`, `CUBAGE_EXCLUSION`, `LOGISTICS_REGION` e `QUOTE_TARIFF`. Exigem conteúdo oficial ou política aprovada, release, escopo, proveniência, vigência e ratificação independente. Atribuição de filial usa a release de filiais operacionais no mesmo escopo; referência vazia exige aprovação própria e tarifa ausente não significa zero.

As garantias de identidade restantes são específicas: 8636 raiz/parcela/rateio; 4924 linha/título/documento/Frete/filhos; 10633 raiz/componentes/Frete; 6392 raiz/minuta/ocorrência; Raster solicitação/parada. Os formatos já observados, inclusive arrays de texto, não são pedidos novamente. Veículos e motoristas exigem ID estável escopado; placa e nome não bastam.

As dependências preservam frentes independentes: Cotações não espera MC/CF; Contas a Pagar não espera Faturas/Fretes; Localização não espera CAP/FAT/INV/SIN; Fretes base não espera MC. A fonte não espera sua dimensão derivada. Fatos e saídas dependem apenas das entradas que consomem; SQL-10 tem contrato interno próprio. Recebido o artefato concreto da matriz, a ação é confrontar a fatia, corrigir somente o delta demonstrado e executar a prova autorizada correspondente.

## Efeitos e condição de encerramento

A rodada física **usou SQL local** exclusivamente em `localhost/ETL_SISTEMA_V2_SHADOW`, com dados sintéticos, autenticação integrada e rollback. O total de chamadas SQL não foi contabilizado: `databaseUsed=true` e `databaseCalls=null`. A auditoria documental histórica e a preparação deste relatório não executaram SQL; seus campos `databaseCalls=0` descrevem somente essas camadas. Não houve chamada de fonte externa, DDL, commit de domínio, uso produtivo, deploy ou cutover nesta qualificação.

As falhas anteriores, os recibos RED/GREEN e os snapshots permanecem preservados. P07 e P08 completos, incluindo o [readback físico final](../../../target/qualificacao-p07-p33-20260922-01/physical/final-readback-01/result.json), já estão comprovados. O fechamento documental ainda exige sincronização de STATES, integração dos artefatos e seal, [validação inicial da sucessão e cadeia histórica](../../../target/qualificacao-p07-p33-20260922-01/physical/closure-validation-result.json), [verificação dos bytes finais](../../../target/qualificacao-p07-p33-20260922-01/physical/final-integrity.json) e [recibo de encerramento](../../../target/qualificacao-p07-p33-20260922-01/physical/closed-receipt.json). Esses últimos links identificam os recibos de fechamento exigidos; sua conclusão não é antecipada por este texto.

A [validação estrutural da matriz](../../../target/qualificacao-p07-p33-20260922-01/matrix-materializer-structure.json) comprova somente estrutura e preservação histórica. O resultado completo de publicação deve ligar os recibos executados à mesma revisão do código, pacote, matriz e checkpoint. Os critérios externos de P09–P33 permanecem nos seus próprios gates, com responsáveis e ações identificados, sem novo aceite nominal.

O consolidador documental também foi corrigido: leitura do nome do elemento XML sem colisão com o atributo e cálculo da revisão sobre a mesma ordem autenticada gravada pelo empacotador. As 15 [contraprovas da recuperação](../../../target/qualificacao-p07-p33-20260922-01/composer-readonly-recovery-contract-01.json) passaram, incluindo adulteração de ordem, hash e item ausente. As falhas anteriores e os pacotes originais foram preservados; nenhum teste físico foi substituído por essa correção.

Validação documental executada: sucessão atual (52 contraprovas), cadeia histórica e trilha PASS. [Validações](validacoes.json). O resultado do scanner, o readback dos bytes finais e o encerramento são autoritativos em target/qualificacao-p07-p33-20260922-01/physical/final-integrity.json e closed-receipt.json, após existirem. Esta fotografia registra somente provas já observadas; não antecipa essas execuções.
