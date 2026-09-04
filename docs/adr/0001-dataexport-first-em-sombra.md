# ADR 0001 — Data Export-first em modo de sombra

- Status: Aceito e ratificado por V2-017
- Data: 2026-08-24
- Ratificação: 2026-08-30

## Contexto

O legado mistura GraphQL, Data Export e a API Raster. A migração precisa preservar identidade, alterações tardias, relações e contratos SQL sem criar dois escritores para a mesma responsabilidade. As nove entidades operacionais ESL possuem templates Data Export conhecidos; Usuários não possui template oficial comprovado e Raster é uma fonte distinta e opcional.

## Decisão

O V2 implementa como fonte primária as nove verticais Data Export `6908`, `6389`, `6399`, `6906`, `8656`, `8636`, `4924`, `10633` e `6392`. Cada vertical usa o cliente comum, mas mantém contrato, limites, partições, identidade e gates próprios.

- GraphQL é um adaptador transitório e somente leitura para Usuários, dual-run e campos sem equivalente Data Export comprovado. Cada campo sidecar registra finalidade, owner-papel, gate e plano de remoção.
- Usuários usa transitoriamente `individual(enabled=true)`; `9901` não é template Data Export comprovado e não pode ser chamado por inferência.
- Raster permanece condicional, desligado por default e inventariado mesmo se V2-034a decidir retirá-lo.
- Modo sombra é propriedade do ambiente/banco e dos gates, não um schema de domínio. A topologia de schemas é decidida no ADR 0006.
- Nenhuma vertical grava em `ETL_SISTEMA` nem substitui o writer legado antes do gate de cutover. Implementar e comparar por entidade não prova que roteamento ou write-fence permitam cortar por entidade: a menor unidade de corte é a menor unidade isolável comprovada em V2-048a.
- A unidade só publica quando contrato, identidade, staging, promoção, reconciliação e consumidores aplicáveis estiverem fechados. Incremental nunca prova ausência.

As nove requisições Data Export usam transporte fixo `GET_WITH_QUERY`; ordenação é atributo de travessia, não identidade nem prova de completude. Filtro de data de negócio e `scopes.by_updated_at` são irmãos; para `6908` e `6389`, `by_updated_at` é overlap complementar, não watermark.

## Consequências

- Evita duplicar clientes e torna explícita a dívida de cada sidecar GraphQL.
- O template `4924` é uma vertical financeira própria em V2-030; a sonda de contrato existente continua apenas auxiliar e não cria runtime antes dessa tarefa.
- O corte pode ser por entidade, onda ou banco inteiro, conforme roteamento e fences provados; nunca se presume granularidade.
- V2-041 continua impedindo qualquer nova sonda, uso externo de credencial, release ou deploy até evidência externa nova. Trabalho e testes sintéticos offline permanecem permitidos.
- Sweep, soft delete e cutover ficam bloqueados sem snapshot/cursor/contagem oficial ou oráculo independente de totais e chaves.
