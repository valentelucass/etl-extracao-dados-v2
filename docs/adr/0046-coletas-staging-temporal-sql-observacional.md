# ADR0046 — staging e qualificação temporal de Coletas no SQL

10/09/2026. Decisão técnica do pedido “conclua todo o bloco”, sucedendo
ADR0045 e checkpoint0067. Responsável técnico: manutenção de Coletas;
aceitante nominal de negócio não informado. Não há aceite presumido.

## Decisão e fundamento

**COL-TIME-08 — cruzamento SQL separado e escopado.** A captura existente
tem um consumidor JDBC explícito. PersistirReferenciasTemporaisColetas entrega
uma observação por vez e sela somente uma travessia retornada sem falha.
O SQL confere contagens, páginas e ordinais contíguos; selo TERMINAL_LOCAL
significa somente travessia local. Falha parcial deixa staging OPEN, inelegível.
O contrato GraphQL continua OBSERVATION_ONLY e bloqueado para promoção.

Fonte/tenant, execução, janela, seleção versionada e campos brutos/presença
ficam em três tabelas próprias. Os instantes usam epoch_second/nano; nenhum
arredondamento a DATETIME2(3) participa da comparação da referência. observedAt
é preservado separadamente. O adaptador valida consistência entre texto, JSON
bruto e instante antes de conectar. Não há reconstrução de payload6908.

Bindings explícitos conservam tags INTEGER/STRING e evidência versionada.
O banco verifica a execução Data Export efetiva, entidade, protocolo, ambiente
LOCAL_SHADOW, source_instance, tenant_scope e data. A bijeção por execução
recusa correspondências um-para-muitos ou muitos-para-um. A mesma raiz pode
aparecer em várias linhas físicas6908; isso não constitui várias identidades.
Hash identifica um artefato de correspondência, não aprova uma identidade real.

**COL-TIME-09 — qualificação conservadora, sem vencedor arbitrário.** A view
e o procedimento fazem agrupamentos e cruzamentos no SQL. Repetições idênticas
da referência são equivalentes para esse cálculo e conservam suas observações.
Variantes de status, data, instante ou representação temporal são conflito;
nenhum ordinal, chegada, MIN temporal ou hash escolhe vencedor. Divergência dos
atributos da mesma raiz6908 bloqueia todas as suas linhas. Quarentena genérica
participa da contagem bloqueada, mesmo sem sidecar tipado.

O estado Data Export deve ser STAGED; referência parcial, binding ausente,
janela/status divergentes, timestamp inválido ou desconhecido não emitem instante.
done e finished continuam distintos; canceled/cancelled conservam o catálogo.
Quando já há status_updated_at nativo, preservar a origem. Seu texto bruto
exatamente igual à referência permite conservar o instante exato; outra
representação retorna NATIVE_PRECISION_UNVERIFIED. Isso é uma recusa conservadora,
não afirma que offsets equivalentes são eventos diferentes. A coluna histórica
DATETIME2(3), sozinha, não permite recuperar nanos perdidos. O teste Java de par
continua comparando Instant; esta limitação pertence à projeção SQL histórica.

**COL-TIME-10 — isolamento e nenhum efeito promocional.** Todas as operações
do complemento usam exclusão por execução, com sp_getapplock transacional e
timeout1500ms; binding/qualificação bloqueiam primeiro a execução Data Export,
depois a referência. Retry idêntico é idempotente; divergente é recusado. Após
selo, entrada nova é recusada. O teste físico de duas sessões verifica que a
trava adquirida pelo staging impede um segundo dono e é liberada no rollback.
Essa prova é de exclusão transacional, não de corrida de uma promoção produtiva.

O resultado tem promotion_authorized=0 para todas as linhas. Não chamar os
procedimentos antigos com frescor substituído; não alterar V004/V010/erro51428,
core.coleta, seu antirregresso ou o fallback6908. A referência pode identificar
um candidato com nanos sem torná-lo representável no consumidor antigo.

## Entrega, alternativas e recuperação

O SQL está em database/proposals/coletas-temporal/001_coletas_temporal_reference.sql,
fora do Flyway/baseline, como determina o B63 para integração estrutural proposta.
JDBC/captura/qualificação são injetáveis, sem composição Main nem configuração
operacional. A adoção do restante permite concluir essa integração revisável e
sua validação sintética na sombra local já autorizada pelo AGENTS; não autoriza
cutover, uma nova campanha B60/B62 ou aceitar representatividade por inferência.

Rejeitados: sobrescrever staging6908, inferir tempo de evento por data civil,
forçar equivalência da fonte, mudar desempate para aceitar fixtures, promover
por fingerprint de evidência ou aplicar DDL permanente para simular ativação.
As tabelas/procedimentos propostos foram criados dentro de cada transação de
teste e revertidos. Sem reset, alterações em migrations/baseline, grants ou UAC.
Provas: Java JdbcSqlServerColetaTemporalGatewayTest e
PersistirReferenciasTemporaisColetasTest; SQL Test-ColetasTemporalSqlPhysical.ps1.
O relatório identifica tentativas falhas, resultados finais e a camada da prova.

Rollback local: retirar apenas os novos consumidores/proposta do código conforme
diff; restaurar os quatro documentos/validador pelos snapshots desta fase,
preservando artefatos anteriores. Nenhum schema novo permaneceu no banco.
Uma futura ativação exige integração à promoção sob bindings qualificados,
migration/baseline próprios e os aceites externos já exigidos. Não integra B63.
