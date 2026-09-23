# ADR 0039 — Manter Raster e formalizar o contrato local

Data: 08/09/2026. Decisão técnica adotada por delegação explícita do owner do
projeto: “vc que decide, preciso continuar”, em resposta à decisão manter/retirar
Raster. O alcance é preparação e trabalho local; os limites físicos anteriores
continuam vigentes. Não se atribui aceitante produtivo nominal à delegação.

## Decisão e motivo

Manter Raster no escopo V2. O legado declara viagens/paradas e a view
`vw_raster_sm_transit_time`; as listas de publicação DEV/PRD incluem essa view.
A responsabilidade de prazo e Transit Time deve ser preservada. Não existe
evidência de obsolescência que sustente retirar o módulo ou abandonar o consumidor.
Esses arquivos provam dependências declaradas, sem provar uso atual em produção.
O owner desta decisão de escopo é o solicitante do projeto; Operação/Logística
e o consumidor produtivo ainda precisam ratificar semântica e eventual corte.

V2-034a dispara V2-025c, formalizado como contrato local `TRANSITIONAL`, com
proveniência, classificações explícitas, aliases/tipos Java separados de wire
types, fixtures artificiais e validação offline. A conclusão do contrato não
equivale a ter provado a identidade V2-009c nem implementado V2-034b.

## Consequências

- Raster continua desabilitado por padrão no V2. Presença de credenciais não
  habilita o módulo. Não se copia o default produtivo ou o modo `auto` do legado.
- O fallback de `Ordem` pela posição da lista não pode identificar a parada.
  Reordenar duas paradas sem `Ordem` troca esse identificador artificial.
- `CodSolicitacao` é candidato, com escopo, wire type e estabilidade pendentes.
  PK SQL e DTO `Long` do legado não encerram essa prova.
- Cap de 500 é alerta local. Lote curto ou vazio não prova completude. Limite
  persistente na menor janela contratada bloqueia; não há cursor inventado.
- Datas sem offset, bordas civis, frescor e snapshot exigem contrato. Nenhum
  timezone do host, overwrite cego ou ausência como exclusão é portado.
- RAS-01–05 e PUB-08 permanecem integrais. A implementação depende de identidade
  aceita; aplicação SQL/runtime precisa de pacote próprio e adoção específica.

Ver [catálogo e pacote de desbloqueio](../catalogos/raster-contrato-local/README.md).
Recuperação: reverter somente deltas próprios após conferir hashes e edições
posteriores. Manter manifests, snapshots, ledgers e integração legada intactos.
