# Uma campanha suplementar — autorizada em 08/09/2026

Em 08/09/2026 o owner autorizou o delta de três grants após revisão documental e
qualificação do schema. O UAC normal funcionou e a revisão final foi copiada para
a área protegida. A qualificação foi interrompida pela comparação de hashes antes
de qualquer commit ou grant. O catálogo e os 22 grants originais foram confirmados
depois do rollback. A quarta campanha ficou encerrada com uma reserva adicional.

O cálculo anterior ignorava a representação que o SQL Server dá a CREATE OR ALTER
e o espaço inicial do batch. A correção foi conferida por leitura dos bytes de
quatro módulos já instalados. V020 ainda precisa passar a qualificação em rollback;
a leitura desses módulos não é qualificação física da migration pendente.

O owner respondeu “pode” ao pedido explícito de uma quinta rodada local de até 15 minutos, usando somente o saldo já aprovado. Essa autorização cobre uma única campanha suplementar; não autoriza uma sexta nem aumenta o orçamento.

## Delta autorizado

- Uma única quinta campanha, de até 15 minutos. Nenhuma reabertura ou edição das
  quatro campanhas encerradas.
- Mesmo teto cumulativo de 128 unidades B54: 30 já reservadas, no máximo 98 restantes.
  B53 conserva suas 112 reservas e o mesmo hash. Não há devolução de reserva.
- Mesmo localhost/ETL_SISTEMA_V2_SHADOW, duas contas, três grants já autorizados,
  fonte loopback e limites de volume, filhos, conexões e saída da seção 3.
- Qualificar V019/V020, instalar se os hashes reais conferirem, conceder os três
  EXECUTEs e executar a revisão final, matriz de autorização, temporal e operação
  manual. REPLAY/FORCE_RUN usam somente o escopo temporário já autorizado.
- Persistir evidências em target/bloco54/resume-approved-supplemental. Recusar
  diretório existente, sixth campaign, saldo insuficiente ou deadline vencido.

## Aplicação e recuperação

Invoke-Bloco54ApprovedContinuation.ps1 recebe os hashes atuais do manifest e do
harness, mais SupplementalApproval=OWNER_APPROVED_B54_SINGLE_SUPPLEMENTAL.
Esse parâmetro exige autorização posterior explícita; sua presença no código
não a concede. O caminho normal de Bloco54Budget.psm1 continua limitado a quatro
campanhas. A função suplementar só aceita exatamente quatro campanhas encerradas
e abre uma quinta, mantendo o limite de 128 e o deadline de 15 minutos.

O controlador preserva o ledger e fecha a campanha no finally. Os scripts de
schema só confirmam depois dos guards; grants têm apply/verify/recover. Depois
dos dois scopes REPLAY retidos, usar verify-retained-replay.sql e, se necessário,
recover-retained-replay.sql: revogar somente os três grants, manter schema/dados
e restaurar direitos originais sem reduzir versões nem renovar vencimentos.

Autorização registrada antes da abertura. A quinta campanha foi executada e encerrada com 69 reservas novas, total 99/128. V019/V020 e os três grants foram instalados/verificados; JAR final e 31 casos da matriz passaram antes da falha do gravador SQL. A compensação exata do caso de revogação restaurou SERVICE v4 e validade original. Nenhum replay/force ou scope extra foi concedido. Restam 29 unidades, sem sexta campanha autorizada. Os detalhes estão no relatório B54; os limites acima descrevem o pacote autorizado, não uma nova janela de execução.
