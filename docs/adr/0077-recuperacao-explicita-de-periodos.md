# ADR 0077 — recuperação explícita de períodos pendentes

Data: 08/11/2026. Escopo: implementação local solicitada pelo usuário («consegue colocar essa funcao de recuperar periodo?»). Evidência executada: checkpoint 0757 e recibos nele relacionados. Esta decisão não habilita operação produtiva.

## Problema

A janela normal D-7..D0 não alcança todos os dias de uma parada longa. O V1 mantém essa janela mesmo com watermark antigo; sua fila diária não demonstra recuperação de todos os dias perdidos. O V2 já possui planejamento civil, resumo durável e recuperação de ocorrências, mas faltava uma operação que percorresse um período declarado, criasse as partições ausentes e despachasse a extração existente.

## Decisão

`RuntimePeriodRecovery` recebe o namespace, a política, a primeira data necessária e um corte de observação. Usa `RuntimeTemporalPlanner` para planejar dias ou meses fechados e vencidos, dentro dos limites existentes. Para cada partição, consulta o estado durável, persiste somente um plano ausente e despacha o executor existente. Só a confirmação `PUBLISHED` permite avançar a fronteira contínua. Para na primeira pendência; sucesso posterior não cobre lacuna anterior.

O caminho de recuperação usa `BACKFILL` e lookback de atualização zero. São novas extrações pelo filtro de data de negócio original de cada entidade. A janela normal D-7..D0 e o uso de filtros por atualização nas modalidades que já os possuem continuam com seus contratos próprios. Não modificar os templates nem generalizar `updated_at` para todas as entidades.

Identidade da execução: namespace + início + fim exclusivo, conforme o adaptador SQL existente. Identidade do plano: esse mesmo material + fingerprint da política. Uma requisição de recuperação é congelada por partição, com observação canônica no vencimento dessa partição. Assim, mudança do lote, ordinal, data de observação ou invocação não altera o contrato da mesma ocorrência; invocações continuam distintas e exigem recibos novos.

O comando explícito usa a composição operacional existente. Leituras SQL exigem STATUS; persistência e despacho exigem RUN por ocorrência, sem nova concessão, bypass, retry automático ou ampliação dos perfis. Ocorrências terminais exigem replay explícito conforme os controles atuais. Recuperar uma ocorrência já iniciada usa a recuperação atual, que não recria indiscriminadamente o cliente da origem.

## Consequências e limites

- Lotes finitos: limite geral do planner até 64; a entrada temporal de laboratório continua limitada a quatro partições. Execução serial, sem sobreposição introduzida por este componente.
- O resultado informa a fronteira confirmada, os despachos, a pendência e o backlog. O chamador deve conservar essa fronteira por namespace e utilizá-la no próximo lote; repetir o mesmo começo apenas confere/ignora o prefixo já publicado daquele lote.
- Nenhuma escrita em dados de negócio, expurgo ou alteração de publicação foi acrescentada. Persistência de plano usa o procedimento já existente. Reexecução e concorrência continuam sujeitas aos controles SQL atuais; conflito ou recibo incerto interrompe a operação.
- O comando continua aceitando apenas os workloads/perfis de laboratório já suportados pela entrada temporal. Não habilita automaticamente Inventário, Sinistros, CAP, Faturas ou Users nessa entrada. A regra de domínio é independente de template; adaptar as entradas qualificadas de outras entidades e seu supervisor produtivo permanece no plano P5.
- Não há daemon, temporizador, checkpoint local automático nem substituição do watermark operacional. Integrar o acionamento a cada 60 minutos e o armazenamento durável da fronteira por entidade/escopo será uma etapa produtiva própria.
- Publicações do período antigo não provam atualizações antigas cuja data de negócio esteja fora das janelas cobertas. Essa detecção exige política de varredura ou suporte da origem já discutidos no plano.
- Testes com portas em memória provam a lógica de recuperação; não provam paridade com a origem nem recuperação física no SQL Server. O ensaio real controlado permanece obrigatório antes de P5/P6.

## Alternativas

Rejeitadas: ampliar apenas a janela normal para um número arbitrário; avançar pelo maior sucesso; tratar retorno do executor como publicação; refazer automaticamente execução terminal; alterar filtros dos templates; instalar scheduler nesta mudança. Cada alternativa pode ocultar lacunas ou modificar contratos já qualificados.

Rollback de código: retirar somente as alterações próprias do 0757, com base no inventário anterior do chat. Não executar reset do repositório, remoção de registros duráveis ou reversão de alterações preexistentes. Esta unidade não aplicou schema ou operação física.
