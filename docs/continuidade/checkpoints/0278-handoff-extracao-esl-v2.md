# Checkpoint 0278 — handoff para extração ESL futura no V2

- Data: 22/09/2026 (America/Sao_Paulo).
- Anterior: `0277-codigo-sombra-equivalencia-build.md`, SHA-256 `d9976f2b4d88eeb77257e4c029db4f78d4a40e44dfe1ff8af92593827a82e5f2`.
- Objetivo do usuário: atualizar `STATES.md` para prosseguir em outro chat
  rumo a uma extração ESL pelo V2.
- Estado: `BLOQUEADO_POR_INPUT` para novo uso externo; código local em sombra
  qualificado no alcance do checkpoint 0277; B17–B29 abertos.

## Autorização e limites

O pedido autoriza o handoff documental. Não houve chamada ESL/Raster/GraphQL,
acesso SQL, leitura ou alteração de segredo, execução Java operacional,
deploy, agenda, banco produtivo ou cutover. A presença de `.env` e a
autorização histórica de sondas `curl` não habilitam o runtime Java externo.
Nenhum budget ou vigência anterior foi renovado.

## Alterações e evidência

- `STATES.md`: cabeçalho novo com o bloqueio V2-041/G01, as dependências de
  V2-025d e a menor sequência para leitura única em sombra.
- `TRILHA_CONCLUSAO_POR_MODELO.md`: índice da mesma rota, sem novo aceite.
- Provas anteriores preservadas: checkpoint 0277, build histórico e logs da
  tentativa física interrompida; nenhum teste novo nesta unidade.
- `BLOCOS_ETAPA_2.md` e a matriz canônica não receberam checkbox: nenhum
  contrato/oráculo/aprovação externa nova foi fornecido.

## Retomada imediata

1. Segurança/Operações: fornecer atestado autenticado V2-041/G01 de
   rotação/invalidação e continuidade do writer legado. Validar autenticidade
   e conteúdo, sem expor valores.
2. Responsável da fonte/janela: após G01, confirmar entidade/template,
   janela, canal, limites e alvo; executar somente a rodada `curl` V2-025d
   autorizada. Para leitura pelo JAR, exigir autorização explícita própria.
3. Fornecedor, owner de referência e Negócio: entregar contrato e oráculo
   independente da entidade; Cotações 6906 exige ainda semântica de terminal
   e tarifa aprovada. Só então reavaliar P17/P20 e dependentes.

Condição de parada: sem atestado G01 ou autoridade exata para o próximo
efeito, não usar as credenciais nem repetir sonda. 4924 continua sem
condição externa nova. Esta nota é índice de continuidade, não autorização.
