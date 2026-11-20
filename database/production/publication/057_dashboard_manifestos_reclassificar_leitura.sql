-- Atualiza a cópia do painel na mesma transação que instala a nova regra.
SET NOCOUNT ON;
IF @@TRANCOUNT = 0 THROW 52101, 'Instalar dentro da transacao de publicacao.', 1;

SELECT sequence_code INTO #contratos_pendentes
FROM core.dashboard_manifestos_leitura
WHERE TRIM(proprietario_nome) COLLATE Latin1_General_CI_AI = N'DALGA LOGISTICA E TRANSPORTES LTDA'
  AND tipo_contrato_veiculo = N'Agregado'
  AND COALESCE(TRIM(tipo_contrato_motorista), N'') IN (N'', N'Terceiro', N'Agregado')
  AND (tipo_contrato IS DISTINCT FROM N'Frota + PX' OR tipo_contrato_key IS DISTINCT FROM N'frota + px');
DECLARE @pendentes int = @@ROWCOUNT;
CREATE UNIQUE CLUSTERED INDEX ix_contratos_pendentes ON #contratos_pendentes(sequence_code);

IF @pendentes > 0
BEGIN
    UPDATE leitura
       SET tipo_contrato = origem.tipo_contrato,
           tipo_contrato_key = origem.tipo_contrato_key,
           hash_linha = origem.hash_linha
      FROM core.dashboard_manifestos_leitura AS leitura
      JOIN #contratos_pendentes AS p ON p.sequence_code = leitura.sequence_code
      JOIN core.dashboard_fato_manifestos AS origem ON origem.sequence_code = p.sequence_code
     WHERE origem.tipo_contrato = N'Frota + PX' AND origem.tipo_contrato_key = N'frota + px'
     OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 2);
    IF @@ROWCOUNT <> @pendentes THROW 52102, 'Classificacao divergente entre a origem e o painel.', 1;
END;
SELECT @pendentes AS reclassificados;
