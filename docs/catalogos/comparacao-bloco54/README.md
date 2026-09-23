# Primeira comparação de Coletas e Fretes — pacote de revisão

Estado: PREPARED_NOT_AUTHORIZED_FOR_REAL_EXPORT. A prova sintética SQL de seis
cenários passou em B54. Nenhuma conexão ao ETL_SISTEMA foi realizada.

O pacote reutiliza Q-FND-01, Q-FND-02, o planejamento V2-047 e a medição V2-050.
Os manifests originais continuam válidos como fotografias das suas entregas;
seus hashes não são recalculados para apresentar mudanças posteriores como antigas.

O grão proposto é uma raiz por origem, tenant e `/id` INTEGER. As expansões físicas
continuam separadas das raízes. `sequence_code`, minuta e `reference_number` são
aliases, não substitutos da identidade. A equivalência nominal do `id` do legado,
o namespace real e a semântica de presença precisam integrar o oráculo aprovado.

| Comparação | Legado estático conferido | Contrato V2 | Condição de aprovação |
| --- | --- | --- | --- |
| Raízes e duplicidade de Coletas | dbo.coletas.id NVARCHAR(50); unique sequence_code | `/id` INTEGER; alias sequence_code separado | Confirmar correspondência nominal; não converter alias em chave |
| Data de Coletas | request_date DATE; status_updated_at_em separado | request_date; precedência status_updated_at → finish_date → service_date → request_date | Confirmar janela civil e fonte de freshness |
| Status de Coletas | ColetaStatusPolicy: done=Coletada; finished=Finalizada | Catálogo local preserva esses códigos | Responsável pelos indicadores decide o rótulo de done; comparação de código separada do rótulo |
| Raízes de Fretes | dbo.fretes.id BIGINT | `/id` INTEGER, com namespace | Confirmar equivalência e expansão física |
| Data/status de Fretes | servico_em DATETIMEOFFSET; finished/done=finalizado; canceled/cancelled=cancelada | servico_em e decisão terminal; updated_at não é freshness | Aprovar tradução temporal e precedência nominal |
| Valores monetários | coletas.total_value e fretes.valor_total DECIMAL(18,2) | Caminho nominal aprovado ainda pendente; preservar ABSENT/NULL/VALUE | Não inferir zero, moeda, total ou equivalência de GraphQL sidecar |
| Ausência/exclusão | excluido_na_origem e regras próprias do legado | Observação de presença, sem completude/sweep | Ausência não autoriza expurgo nem é automaticamente divergência de exclusão |

`export-legado.sql.template` especifica uma leitura agregada de um dia civil por
vez, com parâmetros, MAXDOP 1 e retorno limitado. Não é um script de conexão.
O runner do pacote recusa execução real enquanto os inputs estiverem incompletos
e mantém a proibição de acesso real do Bloco 54. Nenhuma view cross-database,
synonym ou grant de legado integra este pacote.

A primeira rodada futura precisa de: período de um dia aprovado pela operação;
namespace e tradução de identidade confirmados pela evidência da fonte;
oráculos nominais de campos financeiros, moeda e status; destino protegido dos
recibos; autorização de leitura e teto próprios, após os gates V2-041/V2-012.
O agente prepara os objetos e consultas; o owner aprova o período, a finalidade
dos indicadores e a rodada. Não é necessário escolher novamente banco ou contas
do laboratório local.

Teto proposto para revisão futura, ainda não concedido: um dia, duas entidades,
uma leitura agregada por entidade/lado, query timeout de 30 s, até 64 resumos,
até 16 KiB de saída sanitizada e nenhuma coleção de chaves na JVM. Plano estimado
e índices reais devem ser revisados antes da execução; a leitura estática não
comprova custo, escala, consistência entre extrações ou completude do fornecedor.

Em B54, `054_exercise_synthetic_comparison.sql` usa dois conjuntos literais
independentes e um oráculo agregado escrito separadamente. SQL calcula igualdade,
valor/status divergentes, duplicidade, ausência, fronteira temporal e incompletude.
Os casos não usam o mesmo reducer para construir o resultado e o esperado.
Não fecham V2-012a/b/c, V2-047, V2-050, V2-038, paridade, bootstrap ou cutover.
