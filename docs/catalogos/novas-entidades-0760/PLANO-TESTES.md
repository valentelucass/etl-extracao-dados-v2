# Provas executadas e testes necessários

## Resultado executado nesta etapa

| Camada | Consolidação 11067 | Ocorrências 12072 |
| --- | --- | --- |
| INFO real | 39 campos/9 filtros | 20 campos/13 filtros |
| Amostra cURL | 3 linhas, sem declaração de completude | 3 linhas, sem declaração de completude |
| Captura completa | 41 linhas de 06/11, INFO+DATA+terminal | 279 linhas de 08/11, INFO + 3 DATA+terminal |
| Data de negócio em cada linha | Dentro do dia solicitado | Dentro do dia solicitado |
| Excel oficial independente | arquivo 1777587,41 linhas/39 colunas | arquivo 1777562,279 linhas/20 colunas |
| Leitura do arquivo | CRC/EOF, cabeçalhos exatos, 1 aba/0 fórmulas | CRC/EOF, cabeçalhos exatos, 1 aba/0 fórmulas |
| Comparação completa | 1599 células e 41 tuplas/multiplicidades iguais | 5580 células e 279 tuplas/multiplicidades iguais |
| Horários do Excel | Fração descartada em previsão/criação de comprovante | Fração descartada no evento/previsão |
| Conteúdo estruturado | Lista de NF com 2 itens observada; objeto XML vazio/não vazio | NF escalar e repetição por evento/frete/NF |
| SQL/Java/agendamento | Não executados/implementados | Não executados/implementados |

As comparações não toleram diferença de dinheiro, quantidade, documento ou tupla. A conversão de apresentação foi observada por coluna; frações no RAW não foram alteradas. Colunas sempre NULL continuam mapeadas, mas sem aceite do tipo não nulo. As proporções, larguras e domínios são fotografias desses dias.

## Testes de origem antes da adoção Java

- INFO alterado, campo extra/ausente, tipo divergente, erro envelopado e parâmetro inesperado devem recusar com causa sanitizada, conservando RAW privado.
- Vazio legítimo deve ter INFO e terminal atuais. Ensaiar primeira página vazia, página curta seguida de mais dados e ausência de terminal. Nenhuma contagem isolada prova completude.
- Qualificar per, ordenação, fronteiras de página e expansão física; repetir o mesmo corte em execuções distintas, identificando variações upstream sem deduplicar para obter igualdade.
- Provar início/fim inclusivos, meia-noite, segundo fracionário, fronteira de mês e ano, fuso America/Sao_Paulo e diferença entre emissão/evento/frete/updated_at. Não impor filtro de atualização adicional na extração por negócio.
- Ensaiar filtros auxiliares não vazios em escopo limitado e comparar com a interface. Em 12072, testar updated_at isolado e combinação com occurrence_at antes de optar por incremental.
- Buscar recortes não vazios para os sete campos sempre NULL atuais, comentários longos, Unicode/quebras de linha, documentos com zeros, enum novo, lista vazia/repetida e objeto com novas chaves/nulos/escala maior. Limite máximo observado não é contrato de fornecedor.
- Conservar STOP429 e mutex compartilhado. Simular timeout/429/5 xx/redirect/resposta truncada offline; nenhuma repetição automática ou aumento de orçamento para passar.

## Testes do código Java futuro

1. DTO/mapper cobre 59 campos: presença ausente/NULL/vazio distintos, wire exato, texto preservado, inteiro grande, BigDecimal sem arredondar, OffsetDateTime/Instant com fração e listas/objetos íntegros. Fields desconhecidos não podem sumir silenciosamente.
2. Testar as apresentações específicas sem contaminar os modelos: booleano Sim/Não; status de entrega; data civil; fração somente omitida no comparador Excel; NF com vírgula/espaço; JSON compacto de cobrança. Usar fixtures negativas que alterem um campo ou uma multiplicidade.
3. Captura real Java com INFO, terminal e orçamento próprio. Readback do envelope e comparação Java/implementação independente/Excel, com contagens, tuplas completas, multiplicidades, colunas e eixos de negócio. O resultado Python atual não aprova o mapper ainda inexistente.
4. Compilação, estilo, testes de contrato e regressões apropriadas das nove entidades. As duas falhas arquiteturais antigas registradas na 0759 não devem ser apresentadas como novas nem como suíte geral aprovada.

## Persistência e operação posteriores

Staging deve conservar envelope/ordinal/hash e permitir repetição legítima. Tabelas filhas preservam posição da lista e chaves do objeto. Validar JDBC/precisão/larguras/NULL, rollback, contagem/data divergentes, timeout após escrita, commit incerto, morte abrupta e concorrência. Consultar a mesma run_id antes de repetir efeito incerto. Não rebaixar STORED para FAILED nem duplicar inserção.

Recuperação mantém cobertura por entidade/origem/tenant/contrato/filtros, com lacunas antigas além de D-7. Rotina normal D-7..D0 inclui oito datas; próximo ciclo 60 min após terminar. Recebimento e publicação precisam de fronteiras separadas. Supervisor sem sessão, alertas, retenção, backup/restauração e capacidade das onze fontes ainda requerem prova integrada. Publicação, API/tela/exportação e corte/retorno do dashboard têm seus próprios critérios.

## Referências das evidências

Todos os recibos desta etapa em `evidencias/` são sanitizados. Os arquivos integrais de origem e Excel estão no armazenamento privado da 0760 e só foram lidos pelo processo proprietário. A rodada não abriu RAW histórico de outras qualificações. O manifesto registra hashes das evidências e dos documentos, sem valores de negócio ou credenciais.
