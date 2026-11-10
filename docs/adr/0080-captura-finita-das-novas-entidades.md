# ADR0080 — Observações Java e captura finita de11067/12072

Data:09/11/2026. Estado: implementação da fronteira Java; adoção produtiva pendente.

O responsável autorizou implementar as entidades e extrair todas as onze fontes. O mapeamento0760 demonstrou39+20 campos e22 filtros, comparados integralmente com dois Excel oficiais. Faltam provas de tipos não nulos para sete campos, identidade estável e consistência entre páginas.

Decisão: módulos próprios com DTOs, mappers e observações, usando os tipos compartilhados existentes. Preservar sete campos opacos, objeto de cobranças dinâmico, frações temporais, notas em ordem e repetições. Registrar UUID/ordinal de captura sem inventar PK de negócio. Recusar coluna ausente/extra e transformação inválida, conservando os bytes privados.

A captura finita usa um pipeline com Wire/Sink e contrato INFO próprio. O caminho sintético do laboratório e o recebimento produtivo de Faturas não representam o contrato dessas fontes. A enumeração geral reconhece as duas entradas; os seletores antigos conservam recusa explícita. A composição de captura chama os nove mappers existentes e os dois extratores novos. Não modifica o default do Main ou scripts produtivos já aplicados.

Consequência: é possível extrair e validar os atributos em Java agora. Persistência, relações, publicação e agendamento permanente continuam dependentes de qualificação específica. Uma captura física completa até terminal não prova snapshot transacional da ESL. Comparação com Excel mantém suas regras de apresentação separadas do domínio.

Recuperação: nenhuma escrita SQL nesta unidade. Conservar package anterior, fonte, RAW e falhas; a execução não publica nem altera watermark. O recebimento0759 e o dashboardV1 seguem nos contratos anteriores.
