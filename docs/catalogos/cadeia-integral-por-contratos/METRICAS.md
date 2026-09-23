# Medidas da cadeia integral local

Camada: IntegralArtifactScenarioIT no build completo qualificado. São medições reais de laboratório com JVM limitada a512 MiB; heap antes/depois não mede pico nem demonstra plateau. Os processos adicionais do JAR extraído têm duração própria em verificacao-local.json.

| Conjunto | Raízes | Páginas | Bytes | Registros físicos | Batches | Maior batch | Statements preparados | Tempo ms | Heap antes/depois bytes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | 2 | 55 | 130571 | 92 | 41 | 12 | 456 | 24213 | 94850832/28768016 |
| B | 24 | 251 | 1565871 | 1104 | 245 | 20 | 1182 | 77494 | 117354544/169944960 |

A soma inclui as quatro observações de Coletas e os dois caminhos de Fretes; não é contagem de raízes distintas. Os11 inputs tiveram páginas, zero in-flight ao término e retenção de página encerrada. USER usa20 por página; fontes Data Export usam2/4 entidades distintas por página. Suplementos resolvem conjuntos em lotes de até16 e usam TVPs/batches existentes. As medidas não ratificam tamanho de página de fornecedor, SLO, custo de produção ou capacidade nominal.
