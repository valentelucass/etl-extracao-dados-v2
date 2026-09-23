# Segunda revisão pelo mesmo agente

Estado: segunda revisão local concluída, com regressão, JAR extraído e recibos vinculados
no catálogo. Não houve revisão humana, subagente ou alegação de aprovação externa.

| Ponto revisto | Constatação e tratamento |
| --- | --- |
| Seleção de contrato | LocalArtifactScenario seleciona o contrato v2 antes de compor as fontes. O ramo antigo exige explicitamente v1 e PACKAGED_ANALYTIC_SUPPORT_V1. Versão desconhecida não alcança esse modo por fallback válido. |
| Ausência e hardcodes | No caminho integral, as onze entradas, referências, suplementos, relações, datas, revisão, escopo e relógio vêm dos arquivos declarados. START/END e factories históricas estão no ramo antigo ou nos autores de teste. Flags de falha que exigiam fixture foram recusadas antes de SQL. |
| Bytes e paths | Manifestos e páginas têm limites e caminhos confinados. PinnedLocalJson verifica novamente os bytes consumidos. A mutação posterior ao preflight é uma falha observada entre etapas, além da validação unitária. |
| Identidade | Bindings usam chaves tipadas recebidas. Lookup fiscal cruza raiz/parcela/componente e FAT do run; ordinal só correlaciona a requisição ao resultado do batch. Não cria identidade por posição. |
| Escopo SQL/Java | Os namespaces sintéticos têm prefixo e alfabeto fechados, até40 caracteres. V100 usa comparação binária e rejeita padding; o alias LOCAL_V2 permanece na associação histórica restrita. O modo integral exige origem/tenant sintéticos explícitos e confere o contexto do run. |
| Massa e duplicação | Suplementos usam lotes limitados, OPENJSON parametrizado e gateways tipados/TVPs existentes. Não foi criado outro engine de captura ou outra implementação das materializações. As factories de dados ficam no autor dos exemplos e no modo histórico declarado. |
| Independência do esperado | O autor de expected não abre SQL nem usa mapper de produção para produzir valores. Usa regras/literais separados e metadados de colunas. Cinco mutantes físicos e um mutante do JAR exercitam a recusa do comparador. |
| Frescor e precisão | Revisão da operação e da fonte são distintas no replay. V099 não foi reescrita. A precisão datetime2(7) do CT-e é refletida no expected, preservando a evidência wire de maior precisão. Valores financeiros não passam por double. |
| Estado parcial | Falhas de dependência, suporte e materialização não produzem cenário completo. O teste observa capturas independentes e ausência de fatos/preview indevidos; retry materializado conserva o estado anterior. |
| Sweep | O caminho integral fornece quatro capturas. V102 confronta páginas, auditoria, owner e contagem por raiz, inclusive a mutação que mantém o total global. Java/SQL recusam apply integral. |
| Responsabilidades | As33 linhas mantêm a aplicabilidade nominal e a razão de cada item. O JAR deve retornar IDs únicos e BLOCKED para todas; NOT_APPLICABLE não recebe aceite de exclusão. O owner nominal ausente continua externo. |
| Classes | Nove tipos novos têm consumidores de produção. Três suportes Raster foram movidos byte-idênticos para testes; nove remoções anteriores foram preservadas. Bibliotecas puras existentes não foram conectadas artificialmente nem removidas por quota. |
| Seleção dos testes | verify-integral-01 revelou que o perfil não incluía Integral*IT e falhou a cobertura. O perfil foi corrigido e a suíte completa foi repetida em02. A tentativa01 não qualifica o pacote. |
| Recibos | O gate estático passou, mas seu primeiro log tinha bytes OEM. O runner passou a fixar e conferir UTF-8; progressive-gate-02 passou. O raw anterior permanece preservado. |
| Identidade dos testes | Duas fábricas dinâmicas reportam o mesmo classname#name para48 e53 casos. A reconciliação conserva o ID reportado e sua multiplicidade; todos os casos anteriores permanecem. Nenhum nome de teste foi inventado para contornar duplicatas do XML. |
| Encoding de fonte | Test-DataExport6399ContractCatalog.ps1 contém U+FFFD literalmente em duas expressões de detecção. UTF-8 estrito é válido e seus bytes coincidem com a base. A revisão registra esse uso; não reescreve o validador histórico como se fosse erro de decodificação. |
| Readback V099 | O primeiro enumerador ignorou a função precedida por comentários e contou apenas dois módulos. A enumeração foi corrigida; três módulos V099 e os13 módulos V100–V102 coincidiram com as migrations, sem DDL ou reaplicação. |
| Scanner histórico | O snapshot byte-idêntico do suporte Raster exige a mesma exceção sintética por caminho+literal. RED, GREEN e literal alterado recusado foram preservados. Não há exceção de diretório. |
| Sucessão e índice | O sucessor explícito compõe snapshots históricos; os manifests anteriores permanecem imutáveis. O ensaio aplicou/reverteu/reaplicou o patch e aplicou overlay em cópias próprias. O índice real manteve seu hash. |

As correções de autoria de expected, de seleção de testes e de recibos foram
registradas como tais. Elas não foram apresentadas como mudanças de regra de
negócio nem como prova de aceite nominal. Os casos anteriores falhos permanecem
na rodada privada e no índice de tentativas da entrega.

Os recibos de cobertura, IDs de teste, JAR extraído, matriz33 e bytecode correspondem
ao build entregue. O selo externo vincula a revisão canônica final, scanner integral,
sucessão, diff/overlay e readback do ZIP, sem hash circular no catálogo.
