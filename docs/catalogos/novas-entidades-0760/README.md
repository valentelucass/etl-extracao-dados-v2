# Mapeamento de Ocorrências e Consolidação Detalhada

Base para implementar as duas entidades Data Export no ETL V2, conforme pedido do responsável em 09/11/2026. O escopo atual é documentar todos os campos e os contratos antes do código Java. Os 59 campos e 22 filtros estão relacionados individualmente, com evidência real e propostas de tipos separadas dos tipos comprovados.

| Relatório | Template | Campos | Filtros | Corte inclusivo | Linhas API e Excel | Células |
| --- | --- | --- | --- | --- | --- | --- |
| Relação de Consolidação Detalhada | 11067 | 39 | 9 | 06/11/2026 | 41 | 1599 |
| Relação de Ocorrências | 12072 | 20 | 13 | 08/11/2026 | 279 | 5580 |

As 7179 células pertencem a 320 linhas físicas de dois relatórios; essa soma não representa 320 transportes distintos. As colunas, tuplas completas e multiplicidades coincidiram nos dois recortes após aplicar somente as apresentações do Excel comprovadas. Os arquivos oficiais 1777587 e 1777562 têm cabeçalhos exatos, CRC completo, uma planilha e zero fórmulas. Datas com frações continuam preservadas na origem, embora o Excel descarte a fração em campos específicos.

## Documentos do catálogo

- [Consolidação 11067](CONSOLIDACAO-11067.md): 39 campos, 9 filtros e granularidade.
- [Ocorrências 12072](OCORRENCIAS-12072.md): 20 campos, 13 filtros e granularidade.
- [Integração futura em Java](INTEGRACAO-JAVA.md): organização existente, arquivos e adaptações por camada.
- [Plano de testes](PLANO-TESTES.md): provas executadas e critérios restantes.
- [Contrato estruturado](contratos.json): dicionário completo, tipos, perfis, parâmetros, apresentação e evidências.
- `evidencias/`: recibos sanitizados das capturas, leitura dos Excel e comparações.

## Decisões para a implementação

1. Criar módulos próprios `consolidacoes` e `ocorrencias`, preservando os nove existentes. Ocorrências 12072 é distinta de Sinistros 6392.
2. Usar emissão da consolidação para 11067 e data do evento para 12072. Data do frete relacionado, captura e atualização são conceitos diferentes. Não trocar a janela inteira por updated_at.
3. Preservar envelope, ordinal, NULL, listas, objetos, lexemas numéricos e frações. Rótulos Sim/Não, status traduzido, lista concatenada e JSON textual pertencem à apresentação.
4. Guardar identidade física por captura/ordinal. Minuta não é chave única de Ocorrências; a unicidade da consolidação e da combinação candidata de Ocorrências foi observada somente nesses cortes.
5. Qualificar parsers não nulos antes de fixar contrato: seis campos de Ocorrências e o número da fatura financeira de Consolidação vieram somente NULL. Não preencher zeros, datas ou estados inventados.
6. Separar captura de publicação. O recebimento produtivo 0759 tem restrições explícitas para Faturas 4924; cadastro de template não habilita essas duas entidades no SQL ou no supervisor.

## Estado operacional preservado

Nenhum Java, enum do produto, migration, schema, driver, runtime qualificado, agenda ou configuração do dashboard foi alterado nesta etapa. Foi acrescentada somente uma ferramenta de consulta de metadados com cURL e esta documentação. Os 26 sinônimos do dashboard continuam no V1 conforme o repasse; esta unidade não realizou nova consulta SQL para revalidá-los. A autorização de acesso 0758 já aplicada permanece registrada, sem nova solicitação.

SQL Server 2022 Standard 16.0.1000.6, TLS, credenciais e identidades foram preservados. Não houve conexão SQL nem leitura de RAW histórico. RAWs novos e Excel integrais ficam somente em `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/novas-entidades0760`, lidos pelo processo proprietário sob a proteção compartilhada. Dados de usuários, documentos e valores de negócio não entram no catálogo.

## Evidência e escopo

Foram 16 requisições HTTP via cURL, sem retry/redirect: duas primeiras consultas INFO, duas consultas INFO com complemento de parâmetros, uma INFO e uma DATA de amostra por entidade e as capturas completas com 5 e 3 chamadas. A navegação e as duas exportações oficiais ocorreram pela interface autenticada do ESL e são registradas separadamente. As comparações e diagnósticos posteriores foram locais, sem novo HTTP.

As primeiras comparações sem política específica identificaram frações e a composição JSON. Os diagnósticos e resultados anteriores foram preservados. A primeira inspeção do Excel de Ocorrências também encontrou formato de célula ausente (`None`) e foi corrigida no leitor local; nenhum arquivo oficial foi editado. O aceite é de mapeamento e conteúdo dos dois recortes. Integração Java, JDBC, publicação, supervisor e produção são trabalho posterior.
