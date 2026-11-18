# Faturas por Cliente — conferência histórica ESL4924 / V2

## Resultado concluído

Três períodos completos aprovados, 21 dias e 13.325 ocorrências físicas. A referência é o Excel oficial da ESL com Data do frete (service_at) e os demais filtros vazios, conforme provas de interface fixadas nos controles. Não há deduplicação: a quantidade representa linhas, inclusive repetições.

| Período | Linhas V2 | Linhas ESL | Conferência |
| --- | ---: | ---: | --- |
| 2026-10-12 a 2026-10-18 | 3717 | 3717 | Aprovado |
| 2026-10-19 a 2026-10-25 | 4541 | 4541 | Aprovado |
| 2026-10-26 a 2026-11-01 | 5067 | 5067 | Aprovado |

Os 52 campos foram comparados por conteúdo integral, multiconjuntos de tuplas e multiplicidades, quatro projeções e quatro totais declarados. O código Java atual e a referência Python independente concordaram nos 21 dias. O aceite histórico é historicalContentComparisonApproved=true; não constitui qualificação de identidade lógica ou autorização de operação em produção.

## Correções incorporadas ao código

O contrato observado P2 de4924 acrescenta somente os oito tipos realmente demonstrados nos dados: NULL de comments, fit_dyn_drt_nickname, fit_pyr_cor_billing_cycle, fit_pyr_cor_billing_due_in_days, fit_sps_slr_psn_name e reference_number; STRING de fit_ant_ils_atn_transaction_date; INTEGER de fit_nse_number. Campos não observados continuam recusados. O contrato predecessor é preservado.

As listas invoices_mapping e fit_fte_invoices_order_number usam limites específicos de110itens, com ordem e repetições preservadas; o limite genérico32 não foi ampliado. Parser, objetos de domínio, mapper e persistência tipada possuem testes correspondentes. As alterações de precisão/tamanho físico propostas para SQL permanecem em templates pendentes; esta conferência não aplicou migrações.

O perfil de exportação conserva RAW, trata paginação/INFO/terminal literal e rejeita incompletude. Apresentação observada: invoices_mapping tem junção literal com vírgula/espaço; a outra lista tem representação PostgreSQL demonstrada, inclusive array vazio. Valores monetários seguem a política direta demonstrada, sem tolerância nem arredondamento introduzido para ajustar o resultado. Datas seguem as políticas civis observadas; não se acrescentou conversão UTC para eliminar diferenças.

FORMAT e compilação atuais, 1.147 testes em123classes passaram. Os1.109 nomes de métodos anteriores foram mantidos. A unidade atual contém419Java e50controles. Nenhum Java mudou após Full123 e o grafo AST130 correspondente permanece válido.

## Diferença temporal preservada

A captura anterior de27/10 continha um horário diferente do Excel mais recente da ESL. Uma única reobservação desse dia produziu923registros, com os outros51campos idênticos aos anteriores e somente uma ocorrência de fit_d_t_created_at alterada. O novo valor coincide com o Native134 em923/923alvos;829timestamps têm diferença zero e94sãoNULL. A causa da alteração upstream não foi demonstrada. Não se afirma uso de NOW, defeito UTC, ordem SQL ou seleção de filhos como causa.

SourceSet148 seleciona apenas essa nova captura e preserva a anterior como histórico; outros20objetos de captura permanecem integrais. Native132 conserva as duas transferências das semanas12/19 e seleciona o novo arquivo1774330 para26/10–01/11. Nenhum dado, filtro, valor ou multiplicidade foi alterado para forçar igualdade.

O catálogo final150 reutiliza14observações fechadas das semanas12/19 somente após igualdade integral dos respectivos objetos Source e Native. Sete observações novas da última semana usam as aquisições atuais. Os42campos não discretos e as tuplas são iguais; os dez campos controlados possuem associações observadas sem ambiguidades ou conflitos. Todos52bindings e políticas de base foram conservados.

Depois dos3qualificadores atuais,21comparações Java e42execuções Python passarem, Gate133 recusou por KeyError: exigia workbookPin no diagnóstico Java, que por contrato contém workbookSha256; o pino completo está no input e no recibo executado. Diagnóstico152 localizou a linha323 sem publicar valores privados. O sucessor153 vincula o pino completo de input/recibo ao arquivo Native e os hashes do diagnóstico ao mesmo arquivo/catálogo. Preserva os outros quatro pinos diretos e todos os critérios matemáticos, com testes de recusa específicos. Só o gate de fechamento foi executado novamente; as63comparações diárias aprovadas não foram repetidas.

## Limite de captura em30/10

20capturas diárias fecharam normalmente. Em30/10, os1.179registros retidos foram recuperados e sua integridade/completude foi conferida pela prova69 e pelos qualificadores atuais. A falha do produtor ao cumprir o prazo foi preservada: Source/deadline=false para esse dia. Aprovação de conteúdo histórico não transforma essa captura em sucesso operacional.

## Evidências verificáveis

Workspace: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade

| Evidência pública | SHA-256 |
| --- | --- |
| Gate final / três períodos | 224C478BC368CD7695E2C33ADFE7B5FD2EF28EDA4831D4CFFF49A54CFD9DF217 |
| Relatório final de Faturas | 8116DDFC487DFDA41C52ED566388F510B695757357AD57A13559159039D8511C |
| TEST Full123 | 600661513D9B6F6B1D8870E7436513E3D941D446C8E2496F4194803B32FE19C5 |
| Unidade de código Full123 | 43EF393414412A8F01120970A5AC7460808F3FFE34448B7E787C23B7ABBA83F8 |
| Merge150 | 98D0D270D87ABD18EE1B3E272F49D7E5C3107C16B78202AC9FCFC93C03155F47 |
| Diagnóstico atual146 | FAAAC8B0CA8A3BEEF79689A4BFFC840EBE7CE04620BB101B6949402341D9D657 |
| Grafo130 executado | DB5DF66B39C72B65A6030F8938512E0A0DCB8E44415971F0748C6A9ED2B29630 |
| Excel de provas Faturas | 1becade367ec1b15de20564e6bbdf9eafaf885d1e779746e182811d2223e02fd |
| Recibo do Excel | 108FC3A447B0E218045515886221AFBAA4C14F56BF5AD406F304176A22FEFCF4 |
| Revisão visual Root | 841DAA7892BFB4993EB4FCECDD1A852E0C2EDE622A1CC36460E76BDE249B617B |
| ZIP com cinco provas | b78b1c97c0a3e967216778821cd9dd27052fb5ad0c01a2d8a0693c627c2de49d |

O Excel de Faturas contém3resumos de quantidade e9pares reais para análise do gestor,54campos exibidos lado a lado, com arquivo oficial/linha ESL. Foi recalculado, reaberto e verificado sem erros de fórmula; as duas abas foram inspecionadas visualmente pelo Root. Não é exportação completa dos dados.

O ZIP reúne exatamente cinco Excel de provas originais, reabertos comCRC/EOF/SHA conferidos dentroOriginal, sem reautoria dos quatro anteriores e sem outros arquivos:

| Entidade | Arquivo | SHA-256 |
| --- | --- | --- |
| Inventário | Provas_Inventario_ETL_V2.xlsx | b8ba87e5c2fcbe0bc41f0af18fd61cc9d0c9bae191bb02a3184f8af2a3d15be2 |
| Sinistros | Provas_Sinistros_ETL_V2.xlsx | 84249b9f0c3d5748607e7750af543f1967a25765de940904eb1bd707c670c57d |
| Contas a Pagar | Provas_Contas_a_Pagar_ETL_V2.xlsx | 12287745c342e2d7e6d3f91686260fe1c1770760b947e4cb7b1887eeaa04594a |
| Localizador de Cargas | Provas_Localizador_ETL_V2.xlsx | 6880be5dbf8d054c1cb70d48653f9a171ae82c574a13097134640151fa388e3c |
| Faturas por Cliente | Provas_Faturas_por_Cliente_ETL_V2.xlsx | 1becade367ec1b15de20564e6bbdf9eafaf885d1e779746e182811d2223e02fd |

Fretes, Manifestos, Coletas e Cotações foram excluídos conforme solicitação. Não há RAW, exportações completas ESL, diagnósticos privados ou links assinados no pacote. A soma entre entidades não representa cargas distintas.

## Escopo operacional

Os testes demonstram a correspondência dos períodos e dos tipos observados. Mudanças futuras da ESL, atualização de dados entre exportações, falha de rede ou prazo continuam sendo condições a detectar; não há garantia de zero falhas futuras. Main, acionador, dashboard, deploy, SQL, V1, ACL e decisões de identidade não foram ativados por este fechamento. Templates de migração pendentes continuam pendentes. Originals próprios fecharam com estado restaurado e sem retenção de slot; o navegador mantém apenas a aba útil da ESL.