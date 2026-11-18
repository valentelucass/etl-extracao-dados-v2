# Recebimento produtivo0759 — operação do ensaio

Ler primeiro STATES, [ensaios0759](../catalogos/integracao-etl-v2-dashboard/ENSAIOS-0759.md), [ADR0079](../adr/0079-qualificacao-produtiva-do-recebimento.md) e o checkpoint. Este ensaio qualifica Faturas4924 no banco segregado. Não é autorização para ligar todas as entidades ou trocar o dashboard.

## Caminho dos dados

1. O disparo externo chama uma CLI com término, sob o executor compartilhado de2GiB.
2. O cliente valida artefatos, credenciais próprias e destino exato, depois reserva as travas de origem e de escrita SQL.
3. Para cada data, registra uma tentativa e consulta INFO/páginas atuais da origem. Só aceita captura completa, terminal observado,52campos e mapper qualificados. Não completa um resultado apenas pela contagem de uma página.
4. Uma transação guarda cada envelope original em stg.production_export_occurrence, com ordinal e SHA256UTF16; atualiza tentativa/cobertura e faz commit. Não arredonda dinheiro, elimina ocorrências repetidas ou substitui NULL por valores inventados.
5. Uma nova leitura JDBC verifica estado, quantidade, ordinal, conteúdo e hash de todas as ocorrências. Apenas o recibo de captura não é prova de armazenamento.
6. O status operacional fica em pub.v2_ingestion_status. STORED é recebimento; publicação para fatos/telas precisa de core/mart/pub, referências, reconciliação e aceite próprios.

## Executar um intervalo escolhido

Usar scripts/operations/Invoke-ProductionReceiving.ps1, Mode=RANGE, PeriodStart/PeriodEnd emISO(YYYY-MM-DD), ReceiptPath novo e absoluto. Datas inclusivas, particionadas por dia, até31por chamada, sem datas futuras. Cada partição usa service_at civil do contrato Faturas e deixa o filtro updated_at vazio. Não reutilizar arquivo de recibo, diretório de tentativa ou orçamento encerrado.

O launcher seleciona WindowsPowerShell5.1 e Java17/JDBC13.4com TLS validado. Senhas passam exclusivamente por stdin; não adicionar credenciais a argumentos, ambiente persistente, URL, Git, log ou chat. A identidade de execução é etl_v2_runtime_prod, nunca sa. A conta administrativa compartilhada pertence apenas aos instaladores/diagnósticos delimitados.

O runtime atual está preso a runtime-manifest-rev2.json e ao JAR isolado da revisão2. O manifesto antigo e o JAR anterior documentam a tentativa recusada e não devem ser reativados. As16assinaturas precisam permanecer iguais durante a campanha. Um novo release exige build/testes e manifesto novos; não atualizar hashes para aceitar uma mudança não qualificada.

## Janela normal e recuperação

ROLLING calcula D-7..D0 no fuso America/Sao_Paulo: oito datas, extraídas novamente da origem. RecoveryAnchor é explícita e delimita o histórico cuja continuidade será examinada. A âncora29/10/2026é do ensaio, não decisão definitiva de histórico produtivo.

Cada processo lê cobertura SQL durável. Lacunas anteriores aD-7 entram antes da janela normal; até4lacunas podem ser somadas aos oito dias. Acima disso, recebe até8datas antigas e adia a janela normal naquele disparo, mantendo carga limitada. Para na primeira falha; não usa MAX(data) como prova de continuidade. Esse dimensionamento inicial precisa ser medido por entidade/conjunto antes de ativar produção permanente.

STOREDnão é uma fronteira de publicação. O futuro promotor deve conservar separadamente o último estado publicado e o último recebido, incluindo anomalias de esvaziamento, exclusões e dependências. Alterações em dados mais antigos queD-7já cobertos requerem política de reconciliação histórica; a cobertura de uma extração passada não comprova que eles nunca mudarão.

## Campanha de60min

Task Scheduler ETL-V2-Receiving-Qualification-0759 chama Invoke-ReceivingQualificationCycle.ps1. Cada execução termina; só depois do sucesso é marcado o próximo disparo,60min após o fim, conforme o V1. IgnoreNew e o applock SQL impedem sobreposição. A campanha tem exatamente2ciclos; não é produção indefinida.

Antes de observar o horário seguinte, manter o slot de batch livre. Não abreviar o relógio nem usar apenas a definição da tarefa como prova da cadência. Exigir os dois recibos, horários reais, SourceHTTP novo, identidade/escopo corretos, readback completo e progresso lido em processo novo.

O usuário Windows existente usa Interactive/Limited; isso não prova funcionamento após logoff ou reboot. Antes de operação permanente, qualificar o supervisor/identidade disponíveis sem criar contas, mudar ACLs ou assumir que uma sessão interativa estará sempre presente.

## Falha e recuperação segura

No primeiro não200,429, contrato/tipo inválido, limite ou falha SQL, parar a unidade; não aumentar orçamento ou fazer retry/fallback para obter sucesso.429cria/respeita STOP429compartilhado; não remover esse bloqueio para sondar liberação. Não inferir liberação de Coletas6908a partir do sucesso de Faturas4924.

Conservar metadados/recibos/RAW privados e registrar a causa. Em resultado SQL incerto, observar a mesma run_idantes de qualquer repetição; uma tentativa STOREDnão pode ser rebaixada paraFAILEDnem inserida novamente. O recebimento atual não resolve automaticamente todos os casos de morte abrupta/resultado incerto: qualificá-los antes de produção permanente.

Falha não justifica DELETE/TRUNCATE, clean/repair, histórico Flyway inventado, alteração de SQLServer16.0.1000.6, certificado, serviço, senha, principal ou banco antigo. Não parar V1/portal. Os dois SQL de recebimento pertencem somente ao novo banco e têm diário manual/hash próprios; não alterar retroativamente o001aplicado.

## Encerramento do ensaio

Após os dois ciclos e as leituras finais, desabilitar somente a tarefa criada por esta unidade; preservar definição e recibos para revisão. Conferir rotas26,958nomes, atributos/login dos bancos antigos e integridade dos arquivos anteriores. Registrar resultados e limites no STATES/RETOMADA/ENSAIOS e relatório do chat. Sem corte, deploy ou reinício do dashboard.

Os arquivos privados ficam em Armazenamento-Projetos/etl-extracao-dados-v2/production-receiving0759, com finalidade/ACL herdada observada. Não abrir ou publicar RAW fora do fluxo proprietário de qualificação. Definir retenção, backup/restauração, alertas e histórico inicial antes da liberação permanente.
