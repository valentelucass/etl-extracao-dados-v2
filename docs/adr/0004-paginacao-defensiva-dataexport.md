# ADR 0004 — Paginação defensiva Data Export em modo de sombra

- Status: Aceito e ratificado por V2-017
- Data: 2026-08-24
- Ratificação: 2026-08-30

## Contexto

Os templates não possuem cursor, ordenação total, total oficial ou snapshot global formalmente comprovados. Página curta não é terminal seguro; página vazia mostra apenas que o cliente chegou a uma resposta vazia. Expansão de raiz×filhos também faz `physical_rows` divergir de entidades distintas.

As janelas fechadas já sondadas demonstraram repetibilidade e paridade empírica limitada para 6908/6389. As sondas de uma página dos outros contratos caracterizaram formato e expansão, mas não terminalidade ou completude global.

## Decisão

`DataExportPageStreamer` percorre serialmente e entrega cada página imediatamente, sem acumular a execução.

- Limites positivos de páginas, entidades/linhas, bytes, chamadas e duração são obrigatórios. Cap ou repetição termina como incompletude, nunca sucesso.
- Página vazia produz somente `LOCAL_TERMINAL_UNVERIFIED`; página curta exige a chamada seguinte.
- A promoção exige o gate de metadata/resposta de ADR 0012 em todas as páginas e fecha sua prova
  antes do evento de conclusão. Raiz objeto não é promovível enquanto não existir terminalidade
  própria; normalizá-la como um registro não fabrica uma página vazia.
- `order_by` é obrigatório, mas não prova identidade, estabilidade, cursor ou cobertura.
- Envelope 2xx com `error`/`errors` falha; erro parcial não vira terminal vazio.
- Eventos são sanitizados e correlacionados por execução/template/partição/página.
- Circuit breaker permanece isolado por template e compartilhado entre `/info` e `/data`. V2-043 implementa limitador ESL global, orçamento compartilhado e tratamento governado de `Retry-After` para impedir tempestades entre clientes, conforme ADR 0011.
- Para as nove verticais operacionais, o transporte-alvo é fixo `GET_WITH_QUERY`. Não há fallback mutável GET-corpo/query/POST em runtime. O suporte genérico a transportes alternativos existente na fundação fica limitado a teste/caracterização e deve ser recusado pela configuração operacional de V2-018.
- `422` só aciona reparticionamento limitado quando a categoria sanitizada comprova janela grande; outro `422` é terminal. A fundação de V2-043 faz `429`, timeout de request e `5xx` seguirem retry limitado; sua composição operacional pertence a V2-022 e falha de contrato não é mascarada. Probes/harnesses remotos continuam parando no primeiro `429`, sem retry, conforme ADR 0005 e a allowlist vigente.
- URL externa exige HTTPS, host explícito e ausência de userinfo/query/fragmento. Fuso é IANA injetado; `America/Sao_Paulo` é a direção atual dos contratos ESL, ainda sujeita à caracterização de bordas.

## Consequências

- Uma fonte que repete página, mantém cap ou não oferece terminal comprovável permanece incompleta.
- Duas travessias com `per` diferentes são evidência complementar, não prova isolada de completude.
- Sweep/cutover exige garantia do fornecedor ou oráculo independente de totais e conjuntos de chaves.
- V2-041 impede nova execução remota; testes locais/sintéticos continuam válidos.
