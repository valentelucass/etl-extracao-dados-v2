# Manifestos 6399 — diagnóstico independente de 91 colunas

Esta rota do Main compara uma captura JSONL retida com o XLSX obtido pela
interface da ESL. Ela não consulta a API, não grava em SQL e não emite aceite
operacional. O perfil é `MANIFESTOS_6399_NATIVE91_V1`.

## Comandos e entrada

`independent-export plan --input <request.json> --sha256 <sha256>` valida o
planejamento. O request exige o schema `MANIFESTOS_INDEPENDENT_REQUEST_V1`,
entidade `MANIFESTOS`, relatório inteiro `6399`, UUID de tentativa, período
de até 32 dias, zona `America/Sao_Paulo` e os 18 filtros observados no INFO.
Somente `manifests[service_date]` pode ter valor. O plano descreve GET com
query; ele não concede autorização para realizar essa consulta.

`independent-export diagnose-manifestos --input <input.json> --sha256 <sha256>
--output <diagnostic.json>` executa a comparação offline. A entrada é um objeto
com exatamente `schema`, `requestPin`, `sourcePin` e `workbookPin`. Seu schema
é `MANIFESTOS_OFFLINE_DIAGNOSTIC_INPUT_V1`. Cada pin declara `path` absoluto,
`bytes` e `sha256` hexadecimal minúsculo de 64 caracteres.

O destino e seu irmão `<diagnostic.json>.receipt.json` precisam estar ausentes,
com diretório pai existente. O resultado integral fica no arquivo de diagnóstico;
a saída padrão contém apenas o recibo de execução e seus pins. Não imprimir os
arquivos privados de comparação, projeções ou totais em logs de supervisão.

O código de saída é 0 para coincidência sob a apresentação candidata, 3 para
diferenças e 2 para recusa. Mesmo com saída 0, `contentAccepted`, `entityQualified`
e `mainResumeAllowed` permanecem falsos. Uma saída 3 é resultado diagnóstico,
não falha técnica a ser escondida ou retentada automaticamente.

## Identidade, ocorrências e presença

O catálogo `contracts/dataexport/manifestos-native91-v1.json` está fixado por
hash na classe `ManifestosContentAdapter`. São 91 itens e coordenadas físicos
distintos; títulos repetidos e posição visual duplicada no template não servem
de chave. A leitura usa mapas de presença para alcançar também as colunas
64 a 91. A rota de 31 colunas de Coletas permanece separada.

`sequence_code` agrupa as ocorrências desta comparação. Isso não comprova
identidade global ou completude da origem. São comparados todos os valores,
tuplas completas, associações por chave e multiplicidades. Não há deduplicação.
As projeções integrais determinísticas permitem reprodução independente sem
substituir as comparações por amostras ou apenas somatórios.

Chegada corresponde a `closed_at` na coluna 12. Fechamento corresponde a
`finished_at` na coluna 13. O filtro de serviço não aparece como coluna de
saída: `created_at` não é substituto de `service_date`. O diagnóstico não
declara o filtro ou a identidade temporal dos snapshots comprovados.

## Apresentação candidata e originais

O resultado mantém dois eixos: apresentação e valores originais. NULL, omissão,
vazio, tipos, lexemas, estilos, época e estruturas originais continuam
distinguíveis no segundo eixo e nos arquivos de entrada fixados por hash.

As regras de apresentação são delimitadas por item: comentários vazios podem
aparecer omitidos no Excel; NULL no comprovante mobile pode aparecer como
“Não”. Essa última regra não altera o NULL de origem para false. Listas aninhadas
de nomes/regiões mantêm a ordem e as repetições e usam o separador observado
`: `. Tipos, formas ou vocabulários não declarados são recusados.

Datas exigem época 1900 e o estilo observado `DD/MM/YYYY HH:MM:SS`. A chave
visual usa segundos inteiros com aritmética racional. Frações e codificações
originais são preservadas; a contagem de codificações que diferem do inteiro
de segundo canônico aparece separadamente no diagnóstico. Ela não é descartada,
nem tratada como diferença de data/hora exibida. Não há epsilon numérico.

Há 50 colunas de totais: 31 decimais e 19 inteiras. A codificação binary64 dos
decimais é calculada por inteiros e arredondamento exato; somam-se as unidades
codificadas das células. Identificadores numéricos não viram medidas. Esse é um
modelo diagnóstico da apresentação do Excel, sem alterar os valores monetários
originais ou qualificar persistência e consumo operacional.

## Limites e fechamento

Cada entrada tem limite de 8 MiB, cada lado no máximo 12 mil ocorrências e cada
linha JSONL no máximo 262144 caracteres. A comparação compartilha o orçamento
monotônico de até 150 segundos e 1,2 milhão de visitas lógicas a células; esse
último limite pode recusar antes de alcançar o máximo nominal de linhas.

ZIP/XML, cabeçalhos, coordenadas, fórmulas, estilos, tipos e fim completo dos
arquivos são verificados. Os pins são conferidos antes e depois. As execuções
de validação do projeto usam o Original protegido com 1 GiB/300 segundos,
heap de 512 MiB e um único executor pesado. Source0 não libera captura nova,
produção, SQL, serviços, contas, ACL ou alteração do V1.

## Cobertura de períodos completos — etapa 0747 concluída

As semanas de 12–18/10, 19–25/10 e 26/10–01/11/2026 passaram integralmente: **3/3**, 21 dias e 38.197 linhas. Os recibos e o alcance estão em ../continuidade/checkpoints/0747-manifestos-tres-periodos-concluidos.md. O TEST18 passou 487 casos/35 classes, sem falhas, erros ou skips, após FORMAT17 e compilação nova18. Esse resultado de testes é separado das três provas reais de conteúdo.

O fluxo atual usa captura RAW de uma página finita, comparação de todas as suas ocorrências e raízes contra o Native até EOF, composição diária com páginas contíguas e terminal [] real, e união dos sete dias exatos. A política diária é DAILY_SEVEN_DISJOINT_SERVICE_DATES. O verificador exige o mesmo catálogo, configuração, consumidor e runtime qualificados, com 91 colunas, tuplas completas, associações, multiplicidades e 50 totais aplicáveis iguais. IDs distintos globais não são somados.

Um dia vazio exige prova real: [] na página 1, Native com 91 cabeçalhos, EOF e zero linhas, nenhum par de página não vazia e totais não aplicáveis. Essa rota passou no dia 25/10/2026. Não criar linhas nem 50 somas zero para representar vazio.

### Revalidação explícita de RAW fechado

Os dados desta etapa foram capturados pelo ProducerTEST16. O comparador final é o ConsumerTEST18, unidade C320824DB99D5EBB0D8BD8EFD5457503567A0A5ED0A8784A967E56A848D534CA e catálogo D4FE97C3E8AB474130C52A6B67FA2BD93FFA89093EC415F13DB9A49DD437003C. Uma mudança de código não promove automaticamente uma prova antiga.

As entradas administrativas de ManifestosPageDiagnosticMain, modos page e day, aceitam o pin opcional rawRevalidationBindingPin. Seu schema é ROOT_MANIFESTOS_CLOSED_RAW_REVALIDATION_BINDING_V1. O vínculo aprovado fixa produtor/TEST fechado, consumidor/TEST fechado, runtime e closure do consumidor, catálogo, fingerprint da fonte, escopo semântico do request e 1 a 16 trios exatos capturePin/captureClosurePin/captureSummaryPin. Sem esse vínculo, a regra anterior de mesma unidade permanece estrita. Os 18 filtros são conservados; somente attemptId fica fora do digest semântico canônico.

Os metadados históricos do produtor são imutáveis. Seus inputPins antigos não são conferidos contra o código vivo já alterado. O runtime e os inputs do consumidor atual precisam corresponder ao TEST fechado e ser mantidos dentro do Original. Fonte, identidade de código e catálogo continuam separados. Não inventar Source/HTTP na closure histórica: os contadores efetivos Source1/HTTP2 vêm do RAW/captureSummary final; a closure declara os limites e sourceCountersNotInferred. A revalidação em si é Source0/HTTP0/SQL0.

Os controles Root revisados ficam em C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\manifestos-revalidation0747-v1. Página recebe CaptureSummaryPath, RawRevalidationBindingPath, TEST/runtime e mapa/auditoria Native exatos. Dia recebe todos os captureSummary contíguos e os pares não vazios desse mesmo consumidor/Native. Semana recebe sete resumos diários fechados em datas exatas. As saídas e tags devem ser inéditas. O request de união semanal Source0 não se apresenta como captura nova; os pedidos reais permanecem fixados nas partes.

### Apresentações qualificadas

closed_at NULL, coluna12, corresponde somente ao BLANK de timestamp observado n/style2. NULL nativo, ABSENT ou carrier diferente não recebem essa regra. CIOT close_error tem rótulo “Erro no encerramento”; MDF-e issued, “Emitido”. RAW, tipos, presenças, lexemas e carriers continuam preservados. As políticas já qualificadas de CIOT, contrato do motorista e ausências84/86 permanecem delimitadas; ausência não é zero numérico. Outros valores ou grafias continuam recusados.

O relatório final está em C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\manifestos-3periodos0747\manifestos-3-de-3-concluido.json. Sua igualdade cobre os pares registrados. service_date é filtro de escopo e não coluna exportada; não se afirma data oculta conferida por linha nem snapshot simultâneo. Os limites de Original, Source, páginas, memória, tempo e visitas permanecem os já qualificados, sem retry ou redirect. Nova captura continua exigindo sua própria admissão; esta comparação não autoriza produção ou mudanças no V1.