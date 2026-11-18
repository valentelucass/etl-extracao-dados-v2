# 0747 — Manifestos: três períodos completos concluídos

Resultado: **3/3**, com igualdade exata entre V2 e ESL nos pares registrados. Todas as 21 datas exigidas foram comparadas integralmente.

| Período | Linhas V2 | Linhas ESL | Resultado |
| --- | ---: | ---: | --- |
| 2026-10-12 a 2026-10-18 | 10938 | 10938 | Aprovado |
| 2026-10-19 a 2026-10-25 | 13681 | 13681 | Aprovado |
| 2026-10-26 a 2026-11-01 | 13578 | 13578 | Aprovado |

Total: **38.197 linhas**. As 91 colunas, tuplas completas, associações por sequence_code, repetições e multiplicidades coincidiram. Foram verificadas as 50 comparações de totais aplicáveis, usando aritmética inteira/racional e codificação binary64 exata, sem epsilon. Os valores monetários e demais originais não foram alterados para obter igualdade.

## Código e testes

O V2 passou a reconhecer a apresentação observada de closed_at NULL na coluna 12: somente BLANK de timestamp n/style2. RAW NULL e vazio nativo continuam distintos nos originais. Os rótulos exatos CIOT close_error = “Erro no encerramento” e mdfe_status issued = “Emitido” foram acrescentados ao catálogo e às regras delimitadas. Valores e tipos desconhecidos continuam recusados. As regras existentes de CIOT, contrato do motorista e ausência nas colunas 84/86 foram preservadas; ausência não é zero numérico.

FORMAT17, COMPILE18 e TEST18 fecharam fisicamente, com restauração. Foram **487 testes em 35 classes, sem falhas, erros ou skips**, sobre 99 fontes Java e 16 controles (115 inputs). O COMPILE17 recusado por cinco erros de estilo permanece preservado; TEST17 não iniciou. O Root corrigiu o estilo e usou uma compilação nova antes do TEST18.

TEST18: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\independent-export-manifestos0747-test18\root-executed-binding.json, SHA256 A79343C8559B21EBC56AED755F462481B948C98E6D95667CE9B5F913093A2925.
Catálogo: D4FE97C3E8AB474130C52A6B67FA2BD93FFA89093EC415F13DB9A49DD437003C.
ConsumerUnit: C320824DB99D5EBB0D8BD8EFD5457503567A0A5ED0A8784A967E56A848D534CA.

## Captura e comparação independentes

A coleção imutável ProducerTEST16 contém 55 páginas fechadas em 21 dias: 34 não vazias e 21 terminais [] reais. Todas as ocorrências foram mantidas. Vinte e um vínculos explícitos fixam o produtor, seu TEST fechado, cada captureSummary, RAW, pedido, grant e closure, além do ConsumerTEST18 e de seu runtime/catálogo exatos. O fingerprint da fonte permanece igual; identidade de código e catálogo são eixos separados. Não se conferem os antigos inputPins do produtor contra arquivos vivos já alterados.

PACK03 contém 1.569 entradas verificadas contra os bytes do build fechado e oito dependências qualificadas. O replay final registrado no relatório gerou novas provas de página, dia e semana no ConsumerTEST18, sem repetir Source ou HTTP. O consumidor executou com seus inputs e runtime mantidos dentro do Original protegido. A referência Python verificou também a proveniência, além de valores, projeções, cobertura e totais.

Cada dia exige páginas contíguas, terminal [] real e cobertura de todas as linhas do Native. A semana exige sete datas exatas e disjuntas, sem somar IDs distintos globais nem eliminar ocorrências entre datas. O dia 25/10 tem captura [] na página 1 e Native de 91 cabeçalhos, EOF e zero linhas; seus totais são não aplicáveis, sem 50 zeros artificiais.

As novas exportações ESL de 13, 14, 16 e 26/10–01/11 preservaram os mesmos 18 filtros (apenas service_date preenchido), 91 colunas e definição do relatório. Os arquivos anteriores e suas diferenças continuam preservados. Os mesmos RAW retidos foram comparados com os Native atualizados, sem ajustar valores, filtros ou tolerâncias. O mapa e a auditoria fixados no relatório final identificam cada download independente.

## Recibos

- 2026-10-12 a 2026-10-18: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\m747-replay18-06-week-20261012\week-verification.closed.json; SHA256 6A3E9F09965E6D6348D0EE63F7495ADE986042EE366DF805F2D93BB6A985923E.
- 2026-10-19 a 2026-10-25: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\m747-replay18-06-week-20261019\week-verification.closed.json; SHA256 C796A823180964F8184A0EFD41DCE6CAD51AC9CAFE50CD45066B8468CA706055.
- 2026-10-26 a 2026-11-01: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\m747-replay18-06-week-20261026\week-verification.closed.json; SHA256 93016EA108E663E4416A6C0A09CCCD07E072119537035DD596D5647D8768FB6A.

Relatório final: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\manifestos-3periodos0747\manifestos-3-de-3-concluido.json, SHA256 B9A74FC758E5E03E038CA424C1741CA5347D5EE5C1015F5DFE1C2795A817202F.
Prova conjunta: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\m747-replay18-06\three-complete-periods.closed.json, SHA256 3F2B0E242FCF4411702B29E9CE69855DCC51C1AD1B230678D51BAC827BA51832.
Coleção RAW: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\m747-raw-all03\raw-collection-closed.json, SHA256 14AEF18753B9828DFA939C38E275F347659F68768795167D0B7E8B3C1AA3CF4B.
PACK03: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\manifestos0747-runtimepack03\packed-runtime.json, SHA256 825C94AA7023F0E39507BB481668CEC9AD9C6AA6788D4CAF1F697F5C674C0BDA.
Vínculos: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\manifestos-raw-revalidation18-01\bindings-prepared.json, SHA256 C99EA8EDADD25B272858DB18CBA26D5FD45439E96A1394D69070B2981CFC2C4C.
Grafo final: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\manifestos0747-graph18-01\graph-executed.json, SHA256 0C62B5EFDB576260C32188B48AB9F45AB9CF353513F259792CE115A8E54BB854.

## Limites e alcance

Um Original por vez, 1 GiB/300 segundos, worker270 segundos, heap512m, 8 MiB por arquivo de dados, 100 raízes por página, até 16 páginas por dia e 1,2 milhão de visitas lógicas. Nenhum limite foi ampliado. Os Originals próprios fecharam e restauraram o estado. Canônico FB923CC5, ACL B4E214EB e AUX28 49A25567 permanecem os esperados. Falhas e UNKNOWN históricos foram mantidos.

service_date é filtro de escopo; Data exportada é created_at, Chegada é closed_at e Fechamento é finished_at. Não se afirma a data oculta verificada nas linhas, snapshot simultâneo ou causalidade temporal. Este fechamento comprova o conteúdo dos pares registrados. Não há aceite operacional, retomada do Main, SQL, deploy, alteração de ACL ou do V1. Coletas e Fretes 0744 conservam seu fechamento anterior.