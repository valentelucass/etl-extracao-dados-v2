# 0618 — Regressão completa aprovada e ausências no banco medidas

Continuação de0617 em26/10/2026. A validação local atual do JAR153 avançou:
as novas unidades624 eClock9 terminaram aprovadas, com Original0 e stage intacto.
A consulta real ao catálogo também terminou e confirmou uma pendência concreta:
oito estruturas previstas para P3 e campos originais de usuários não existem
nos nomes consultados. A gravação de todas as entidades e a conferência externa
por registro/campo continuam abertas; etapas3 e4 não estão concluídas.

## Novas execuções aprovadas

| Unidade | Resultado real | Limites e alcance |
| --- | --- | --- |
| wk56/full624-ful2 | 624 encontrados, iniciados e aprovados; zero skips/falhas/abortos | Original0/355.317ms/pico682.377.216bytes; 56 classes selecionadas, incluindo os26 testes de arquitetura |
| wk56/clock9-clk2 | Nove encontrados, iniciados e aprovados; zero skips/falhas/abortos | Original0/281.490ms/pico531.120.128bytes; duas classes selecionadas |
| R58 | 42 modelos próprios, quatro casos Windows e Capture6 reais aprovados | Originais0; seleção literal de seis então presentes; sem Source/SQL ou produto novo |
| Names8/11 físico | 11 SELECT concluídos e11 observações; uma conexão efetiva fechada, um provider | Original0/25.144ms/pico535.113.728bytes; zero HTTP, DML, DDL e leitura de dados de negócio |

As duas unidades de teste mantiveram Original1GiB/8min, compilador256MiB/90s,
runner192MiB/180s e dois guardas nativos por unidade. A cópia das1.778 fontes no
work novo deixou as medições sintéticas fora de g1. Todos os4.546 arquivos de g1
continuaram com o conjunto e os bytes exatos, sem exclusões na verificação.
O overlay ficou restrito ao helper de teste EFA1 e seu runner; Main e catálogo
vieram do JAR153, e os outros testes vieram das classes originais de g1.

Own200 e ColetasFinal8 permanecem aplicáveis ao mesmo produto inalterado:
`153BA720FB224D59B6EDB196AF5D0D0DC17F139B5EDED32FA0DDDC38E91663A4`.
Não foi criado outro JAR nem transferido PASS de uma rodada para outra.
A antiga unidade sk56/ful1, com624 aprovados e Original1 por cinco arquivos novos,
continua preservada e não qualificada. Sua restauração permanece documentada
em0617; a nova aprovação veio de execução própria, completa e encerrada.

## O que falta no banco

A consulta Names8/11 encontrou `object_visible=false`, `object_id=null` e
`database_view_definition=1` para cada um dos oito nomes. Portanto, a ausência
foi medida com visibilidade suficiente nesta observação; não foi inferida de
histórico, falta de cobertura ou erro de permissão.

| Nome consultado | Tipo previsto | Grupo |
| --- | --- | --- |
| `ctl.observed_export_capture` | Tabela | P3 |
| `stg.observed_export_page` | Tabela | P3 |
| `ctl.usp_begin_observed_export_capture` | Procedure | P3 |
| `stg.usp_append_observed_export_page` | Procedure | P3 |
| `ctl.usp_seal_observed_export_capture` | Procedure | P3 |
| `stg.usuario_observed_field_capture` | Tabela | Campos originais de usuários |
| `stg.usuario_observed_field_record` | Tabela | Campos originais de usuários |
| `stg.usp_stage_usuario_observed_fields` | Procedure | Campos originais de usuários |

O resultado permanece `CLOSED_UNKNOWN_DIAGNOSTIC_ONLY`, sem admissão física ou
fechamento semântico. São observações pontuais de metadados, não um BEFORE/AFTER
atômico e protegido para aplicar DDL. As46 consultas restantes de Fretes/Local
não foram executadas nesta continuação; não declarar catálogo57 completo.

O Root aceitou pessoalmente a Own51 encerrada como fronteira atual do auxiliar,
com as unidades R58 intervenientes também fechadas e sem Source/SQL. O descritor
legado de45 casos/Users15340 ficou preservado como histórico. Nenhum fechamento
de EOF ou cleanup gracioso de Usuários foi fabricado. O auxiliar de catálogo
continua no JAR histórico E05B; sua execução não qualifica o JAR153 do ETL.

Foi delegado o levantamento dos scripts aditivos existentes, dependências,
footprint, ordem e verificação das oito estruturas. A proposta precisa ser
concreta e revisada, com a autorização aplicável identificada, antes de qualquer
aplicação. Nenhum objeto, dado, permissão, versão do SQL ou serviço foi alterado.

## Processos e execução

R58 qualificou a seleção literal com34928. Esse processo terminou naturalmente
antes da nova suíte. O Root comparou pessoalmente as cinco tuplas originais
com a seleção imutável0601 e escolheu explicitamente o preparo wk56 já revisado.
Não houve fallback automático, identidade opcional, encerramento ou alteração
de PM2. O contexto de seis permanece prova histórica daquele momento;
não representa os processos atuais nem concede autoridade sobre o produto.

## Próximas unidades

1. Fechar a revisão dos dez patches/inversos de R55/R38/R39/R42 e ligar os
   metadados finais às provas624/9 atuais. Executar os bindings reais7/111/4/Main7
   pelos executores existentes, sem converter os recibos Maven/CI antigos em
   qualificações atuais. Nenhum binding atual foi executado ainda.
2. Só então aplicar a proposta canônica de68 caminhos já revisada, após rehash
   das16 imagens anteriores e52 ausências; commit/push e índice de arquitetura.
   A proposta parcial de sete caminhos não cobre a composição qualificada.
3. Concluir a retenção privada dos valores reais do ResultSet, seus testes e a
   unidade de Coletas com payload completo,23 binds separados e rollback/close.
   Os históricos MFT315/COT287 não preservaram as linhas para confronto externo.
4. Resolver a estrutura do banco e concluir as demais entidades/Usuários até EOF.
   Comparar os valores reais relidos com ESL/Excel no mesmo recorte, por registro
   e campo, com faltantes/extras/divergências e colunas não verificadas explícitas.

## Evidências privadas

- `B\RootScopeR56full624Qualification0617w.json`:
  `810E5A0001BF882FA9A6F6978433C85763D42528B10960E795F45CA1D0D766F2`.
- `B\RootScopeR56clock9Qualification0617w.json`:
  `7AF724AB4CCD935BE57397BB954A0722D6A1105351A8A8EBFFECB177CBA9B494`.
- `B\scope-r56-workdir-review-files-only-0617\review.json`:
  `9A62DE2C57E52D012E1E375A84EC7F9D507F8EA5616D0D247B977DBF94D44F84`.
- `B\RootR58CaptureQualification0617.json`:
  `3709F36419E5FE657735E17DFABAFD4DB69C199D7CA0518B17862945DAADE8ED`;
  modelos42 e casos Windows4 têm recibos novos próprios.
- `B\RootPersonalFiveAfter34928Ended0617.json`: comparação pessoal das cinco
  identidades antes de escolher wk56; nenhum processo encerrado pelo Root.
- `B\RootNames8MetadataQualification0617.json`:
  `323B7F056BEF2D83430294C30A0552468E4D83E8CA65C8BDCE99BA9B5AED96DD`.
- `B\RootNames8MetadataOriginalClosure0617.json`,
  `B\RootNames8MetadataReadonlyReview0617.json` e
  `B\RootNames8CurrentClosureReview0617.json`: liberação exata de metadados,
  vínculo à Own51 real e fechamento atual; não concedem Source/DDL/EOF/paridade.

Nesta continuação não houve chamada à Source; houve uma conexão SQL somente
de catálogo, fechada. A última Source continua INFO Coletas0607/delta1, e os
contadores globais continuam não finais. Root permanece único executor pesado
e responsável por Source/SQL/browser/Git. ESL exclusivamente consulta/filtro/
download. Sem DDL, adoção de código, retenção de negócio, promoção ou implantação.
