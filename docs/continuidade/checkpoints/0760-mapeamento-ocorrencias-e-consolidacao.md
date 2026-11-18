# Checkpoint 0760 — Mapeamento das duas novas entidades

Data: 09/11/2026, America/Sao_Paulo. O responsável identificou os templates 11067, Relação de Consolidação Detalhada, e 12072, Relação de Ocorrências. Depois delimitou esta etapa a mapear todos os campos e documentar a base para implementar Java posteriormente.

## Entrega verificada

Catálogo: [novas entidades](../../catalogos/novas-entidades-0760/README.md). Dicionário estruturado de 59 campos e 22 filtros, com parâmetros completos, tipos observados, NULL, tamanhos, listas/objetos, datas, propostas Java/SQL, identidade, vínculos candidatos e plano de testes. Organização das nove entidades existentes rastreada até extratores, catálogo, expansão, composição, persistência e recebimento produtivo.

cURL real com Bearer do próprio V2 por stdin, sem retry/redirect, STOP429 e mutex compartilhados, timeout 30s, intervalo mínimo 3s, respostas limitadas e executor compartilhado de 2GiB. Dezesseis HTTP via cURL no total, contados separadamente da interface. Recortes completos: Consolidação 06/11/2026, 41 linhas/39 campos/1.599 células, INFO+DATA+terminal; Ocorrências 08/11/2026, 279 linhas/20 campos/5.580 células, INFO+3 DATA+terminal. Nenhum SQL.

Excel oficiais independentes gerados pela interface: 1777587 e 1777562. CRC/EOF, cabeçalhos exatos, uma planilha, zero fórmulas e contagens aprovados. Comparações de todas as colunas e das tuplas completas com multiplicidades passaram nos dois recortes, totalizando 7.179 células de 320 linhas físicas. Os dados integrais são privados, somente lidos pelo processo proprietário; não foram publicados ou abertos RAWs históricos.

Particularidades comprovadas: Excel omite frações de segundo em quatro campos específicos entre os dois relatórios; RAW conserva a fração. Consolidação tem lista de NF com apresentação vírgula/espaço e composição XML como objeto JSON com valores decimais/chaves dinâmicas. Ocorrências traduz failure/pending/delivered para Insucesso/Pendente/Entregue. Uma minuta não é chave: 246 minutas em 279 linhas, até cinco repetições. Combinação candidata de quatro campos foi única somente nesse corte. Consolidação teve 41 números distintos nesse dia, sem prova de identidade estável global.

Seis campos de Ocorrências e ctd_ant_document de Consolidação vieram apenas NULL. Estão mapeados, com tipo não nulo pendente e sem defaults inventados. Parsers, largura/precisão, filtros auxiliares não vazios, ordenação, snapshot e identidade exigem qualificação posterior. A comparação de apresentação não é paridade subsegundo nem aceite de Java/SQL.

## Preservação e limitações

Nenhum Java, enum de produto, migration, banco, driver, runtime qualificado, agenda, configuração do dashboard ou V1 foi alterado. Acrescentado somente `scripts/probes/Invoke-NewEntitiesContractProbe.ps1`, ferramenta limitada a INFO dos dois IDs, além da documentação. As nove entidades e seus aceites anteriores permanecem preservados. Recebimento0759 continua específico de Faturas4924; não generalizar seu CHECK/SQL/manifesto. Os 26 sinônimos no V1 são o estado do repasse, sem nova conexão SQL nesta etapa.

As tentativas de comparação e o diagnóstico temporal/JSON anteriores foram conservados. A primeira inspeção local de Ocorrências recusou formato de célula None; o leitor foi corrigido sem editar o Excel. Quarenta e nove verificações locais de cobertura, políticas, multiplicidades, decimal exato, documentos e hashes passaram, sem HTTP/SQL e sem alegar testes Java.

Evidências sanitizadas em `docs/catalogos/novas-entidades-0760/evidencias`. Entrega humana e contrato JSON em `C:/Users/suporte/Documents/Codex/2026-11-09/etl-v2-novas-entidades/outputs`. RAW/Excel novos em `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/novas-entidades0760`, sob fluxo proprietário.

## Próximas ações

1. Qualificar os valores não nulos pendentes, filtros, identidade e paginação conforme o plano do catálogo, sem repetir capturas encerradas sem causa nova.
2. Na etapa Java posterior, implementar os módulos próprios, contratos e comparadores, testando contra os arquivos independentes e preservando as nove entidades.
3. Planejar recebimento/publicação e migrations aditivas com readback e recuperação próprios antes de supervisor ou consumidor. Produção integral, demais entidades e corte continuam com as pendências da0759.

## Índice e conferência final

A atualização geral do Graphify atingiu o prazo de6min (exit124, sem aceite nem nova tentativa ampla). Uma atualização local aditiva, extraída dos contratos documentados e AST PowerShell, acrescentou90nós/89arestas, preservando55.092nós/138.734arestas anteriores; grafo atual55.182nós. Processo limitado a2GiB concluiu exit0/pico513MiB, sem HTTP/SQL/LLM. Consulta MCP posterior localizou charged_value_details no documento e linha corretos. Tokens externos de extração:0. Comunidades/HTML não foram reconstruídos.

Conferência final dos documentos:30verificações de campos/filtros/links/hashes aprovadas, complementando as49verificações anteriores sem reexecutar testes Java. Inventário final preservou713arquivos preexistentes byte a byte e os históricos integrais de quatro documentos por prefixo aditivo; zero drift inesperado. Recibo de preservação e manifesto disponíveis nos outputs desta conversa.
