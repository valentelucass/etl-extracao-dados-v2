-- Cotações 6906 em sombra; COT-01/COT-02. Sem pub, sweep, defaults tarifários ou rede.
SET XACT_ABORT ON;

CREATE TABLE stg.cotacao_record (
    stage_record_id BIGINT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key_wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payload_json NVARCHAR(MAX) NOT NULL,
    field_presence_json NVARCHAR(MAX) NOT NULL,
    user_name_normalized NVARCHAR(MAX) NULL,
    nfse_issued_at_utc DATETIME2(3) NULL,
    cte_issued_at_utc DATETIME2(3) NULL,
    requested_at_utc DATETIME2(3) NULL,
    freshness_at_utc DATETIME2(3) NOT NULL,
    freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    freshness_business_date DATE NOT NULL,
    total_amount DECIMAL(19,4) NULL,
    currency_code CHAR(3) COLLATE Latin1_General_100_BIN2 NULL,
    origin_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL,
    destination_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_stg_cotacao_record PRIMARY KEY (stage_record_id),
    CONSTRAINT FK_stg_cotacao_record_stage FOREIGN KEY (stage_record_id, execution_id, source_key)
      REFERENCES stg.execution_record (stage_record_id, execution_id, source_key),
    CONSTRAINT CK_stg_cotacao_identity CHECK (source_key_wire_type=N'INTEGER'
      AND LEFT(source_key,8)=N'INTEGER:'
      AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256)) IS NOT NULL
      AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))>CONVERT(BIGINT,0)
      AND DATALENGTH(source_key)=DATALENGTH(CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256)))))
      AND source_key=CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))))),
    CONSTRAINT CK_stg_cotacao_json CHECK (ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1),
    CONSTRAINT CK_stg_cotacao_presence CHECK (
      COALESCE(JSON_VALUE(field_presence_json,N'$.sequence_code'),N'INVALID') COLLATE Latin1_General_100_BIN2=N'VALUE'
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.requested_at'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_nse_issued_at'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_fhe_cte_issued_at'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_total'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_crn_psn_nickname'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_uer_name'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_ony_sae_code'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_diy_sae_code'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND ((JSON_VALUE(field_presence_json,N'$.requested_at')=N'VALUE' AND requested_at_utc IS NOT NULL)
        OR (JSON_VALUE(field_presence_json,N'$.requested_at') IN(N'ABSENT',N'NULL') AND requested_at_utc IS NULL))
      AND ((JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_nse_issued_at')=N'VALUE' AND nfse_issued_at_utc IS NOT NULL)
        OR (JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_nse_issued_at') IN(N'ABSENT',N'NULL') AND nfse_issued_at_utc IS NULL))
      AND ((JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_fhe_cte_issued_at')=N'VALUE' AND cte_issued_at_utc IS NOT NULL)
        OR (JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_fhe_cte_issued_at') IN(N'ABSENT',N'NULL') AND cte_issued_at_utc IS NULL))
      AND (JSON_VALUE(field_presence_json,N'$.qoe_uer_name')=N'VALUE' OR user_name_normalized IS NULL)
      AND ((JSON_VALUE(field_presence_json,N'$.qoe_qes_total')=N'VALUE' AND total_amount IS NOT NULL)
        OR (JSON_VALUE(field_presence_json,N'$.qoe_qes_total') IN(N'ABSENT',N'NULL') AND total_amount IS NULL))
      AND ((JSON_VALUE(field_presence_json,N'$.qoe_qes_ony_sae_code')=N'VALUE' AND origin_uf IS NOT NULL)
        OR (JSON_VALUE(field_presence_json,N'$.qoe_qes_ony_sae_code') IN(N'ABSENT',N'NULL') AND origin_uf IS NULL))
      AND ((JSON_VALUE(field_presence_json,N'$.qoe_qes_diy_sae_code')=N'VALUE' AND destination_uf IS NOT NULL)
        OR (JSON_VALUE(field_presence_json,N'$.qoe_qes_diy_sae_code') IN(N'ABSENT',N'NULL') AND destination_uf IS NULL))
    ),
    CONSTRAINT CK_stg_cotacao_source_currency CHECK (currency_code IS NULL),
    CONSTRAINT CK_stg_cotacao_freshness CHECK (
      freshness_origin IN (N'NFSE_ISSUED_AT',N'CTE_ISSUED_AT',N'REQUESTED_AT') AND
      ((freshness_origin=N'NFSE_ISSUED_AT' AND nfse_issued_at_utc=freshness_at_utc) OR
       (freshness_origin=N'CTE_ISSUED_AT' AND nfse_issued_at_utc IS NULL AND cte_issued_at_utc=freshness_at_utc) OR
       (freshness_origin=N'REQUESTED_AT' AND nfse_issued_at_utc IS NULL AND cte_issued_at_utc IS NULL AND requested_at_utc=freshness_at_utc))
    )
);
GO
CREATE INDEX IX_stg_cotacao_execution_source ON stg.cotacao_record(execution_id,source_key,freshness_at_utc,attribute_hash);
GO
CREATE TABLE ctl.cotacao_promotion_result (
    execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    candidate_rows BIGINT NOT NULL, typed_candidate_rows BIGINT NOT NULL,
    conflicting_root_keys BIGINT NOT NULL, validation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT FK_ctl_cotacao_promotion_generic FOREIGN KEY(execution_id) REFERENCES ctl.execution_promotion_result(execution_id),
    CONSTRAINT CK_ctl_cotacao_promotion CHECK (candidate_rows>=0 AND typed_candidate_rows>=0 AND conflicting_root_keys>=0
      AND validation_state IN(N'PASSED',N'BLOCKED'))
);
GO
CREATE TABLE core.cotacao (
    cotacao_id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    record_state_id BIGINT NOT NULL UNIQUE,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payload_json NVARCHAR(MAX) NOT NULL, field_presence_json NVARCHAR(MAX) NOT NULL,
    user_name_normalized NVARCHAR(MAX) NULL,
    freshness_at_utc DATETIME2(3) NOT NULL, freshness_origin NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    freshness_business_date DATE NOT NULL,
    total_amount DECIMAL(19,4) NULL, currency_code CHAR(3) COLLATE Latin1_General_100_BIN2 NULL,
    origin_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL, destination_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL,
    tariff_reference_release_id BIGINT NOT NULL,
    tariff_effective_date DATE NOT NULL,
    tariff_minimum_amount DECIMAL(19,4) NOT NULL,
    tariff_currency_code CHAR(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tariff_unit_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tariff_rounding_mode NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
    attribute_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL, active BIT NOT NULL,
    first_seen_execution_id UNIQUEIDENTIFIER NOT NULL, last_seen_execution_id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT UQ_core_cotacao_source UNIQUE(environment_name,source_instance,tenant_scope,entity_name,source_key),
    CONSTRAINT FK_core_cotacao_state FOREIGN KEY(record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key)
      REFERENCES core.entity_record_state(record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key),
    CONSTRAINT FK_core_cotacao_tariff_release FOREIGN KEY(tariff_reference_release_id)
      REFERENCES ref.reference_release(reference_release_id),
    CONSTRAINT CK_core_cotacao_identity CHECK(entity_name=N'cotacoes' AND LEFT(source_key,8)=N'INTEGER:'
      AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256)) IS NOT NULL
      AND TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256))>CONVERT(BIGINT,0)
      AND DATALENGTH(source_key)=DATALENGTH(CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256)))))
      AND source_key=CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(source_key,9,256)))) AND active=1),
    CONSTRAINT CK_core_cotacao_json CHECK(ISJSON(payload_json)=1 AND ISJSON(field_presence_json)=1),
    CONSTRAINT CK_core_cotacao_presence CHECK (
      COALESCE(JSON_VALUE(field_presence_json,N'$.sequence_code'),N'INVALID') COLLATE Latin1_General_100_BIN2=N'VALUE'
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.requested_at'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_nse_issued_at'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_fit_fhe_cte_issued_at'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_total'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_crn_psn_nickname'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_uer_name'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_ony_sae_code'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
      AND COALESCE(JSON_VALUE(field_presence_json,N'$.qoe_qes_diy_sae_code'),N'INVALID') COLLATE Latin1_General_100_BIN2 IN(N'ABSENT',N'NULL',N'VALUE')
    ),
    CONSTRAINT CK_core_cotacao_source_currency CHECK (currency_code IS NULL),
    CONSTRAINT CK_core_cotacao_tariff CHECK (
      tariff_minimum_amount > CONVERT(DECIMAL(19,4),0)
      AND tariff_currency_code COLLATE Latin1_General_100_BIN2 LIKE '[A-Z][A-Z][A-Z]'
      AND tariff_unit_code IN(N'PER_SHIPMENT',N'PER_WEIGHT',N'PER_VOLUME')
      AND tariff_rounding_mode IN(N'HALF_UP',N'HALF_EVEN',N'DOWN',N'UP')
    )
);
GO
CREATE TABLE recon.cotacao_root_presence_observation (
    execution_id UNIQUEIDENTIFIER NOT NULL, cotacao_id BIGINT NOT NULL, observed_at_utc DATETIME2(3) NOT NULL,
    snapshot_completeness NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    absence_evaluation NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_recon_cotacao_presence PRIMARY KEY(execution_id,cotacao_id),
    CONSTRAINT FK_recon_cotacao_presence_execution FOREIGN KEY(execution_id) REFERENCES ctl.execution_attempt(execution_id),
    CONSTRAINT FK_recon_cotacao_presence_cotacao FOREIGN KEY(cotacao_id) REFERENCES core.cotacao(cotacao_id),
    CONSTRAINT CK_recon_cotacao_presence CHECK(snapshot_completeness=N'BLOCKED_NO_COMPLETENESS_PROOF' AND absence_evaluation=N'NOT_EVALUATED')
);
GO
CREATE OR ALTER PROCEDURE stg.usp_stage_cotacao_record
  @execution_id UNIQUEIDENTIFIER, @input_batch_number INT, @input_record_ordinal INT,
  @source_key NVARCHAR(MAX), @payload_json NVARCHAR(MAX), @field_presence_json NVARCHAR(MAX),
  @user_name_normalized NVARCHAR(MAX),
  @nfse_issued_at_utc DATETIME2(3), @cte_issued_at_utc DATETIME2(3), @requested_at_utc DATETIME2(3),
  @freshness_business_date DATE,
  @total_amount DECIMAL(19,4), @currency_code CHAR(3), @origin_uf CHAR(2), @destination_uf CHAR(2),
  @validation_disposition NVARCHAR(16), @quarantine_reason_code NVARCHAR(128), @observed_at_utc DATETIME2(3)
AS
BEGIN
  SET NOCOUNT ON;
  IF @input_record_ordinal NOT BETWEEN 1 AND 1000 THROW 51900,N'Ordinal vertical de Cotações deve estar entre 1 e 1000.',1;
  IF @validation_disposition NOT IN(N'VALID',N'QUARANTINE') OR @observed_at_utc IS NULL
     OR (@validation_disposition=N'QUARANTINE' AND @quarantine_reason_code IS NULL) THROW 51900,N'Registro de Cotações inválido.',1;
  IF @validation_disposition=N'VALID' AND (
     @source_key IS NULL OR LEFT(@source_key,8) COLLATE Latin1_General_100_BIN2<>N'INTEGER:'
     OR TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256)) IS NULL
     OR TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256))<=CONVERT(BIGINT,0)
     OR DATALENGTH(@source_key)<>DATALENGTH(CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256)))))
     OR @source_key COLLATE Latin1_General_100_BIN2<>CONCAT(N'INTEGER:',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256)))) COLLATE Latin1_General_100_BIN2
     OR ISJSON(@payload_json)<>1 OR ISJSON(@field_presence_json)<>1
     OR @currency_code IS NOT NULL
     OR COALESCE(@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc) IS NULL
     OR @freshness_business_date IS NULL
  ) THROW 51900,N'Campos tipados de Cotações inválidos.',1;
  DECLARE @presence_sequence NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.sequence_code');
  DECLARE @presence_requested NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.requested_at');
  DECLARE @presence_nfse NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_fit_nse_issued_at');
  DECLARE @presence_cte NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_fit_fhe_cte_issued_at');
  DECLARE @presence_total NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_total');
  DECLARE @presence_nickname NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_crn_psn_nickname');
  DECLARE @presence_user NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_uer_name');
  DECLARE @presence_origin NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_ony_sae_code');
  DECLARE @presence_destination NVARCHAR(8)=JSON_VALUE(@field_presence_json,N'$.qoe_qes_diy_sae_code');
  IF @validation_disposition=N'VALID' AND (
     COALESCE(@presence_sequence,N'INVALID') COLLATE Latin1_General_100_BIN2<>N'VALUE'
     OR COALESCE(@presence_requested,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_nfse,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_cte,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_total,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_nickname,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_user,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_origin,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR COALESCE(@presence_destination,N'INVALID') COLLATE Latin1_General_100_BIN2 NOT IN(N'ABSENT',N'NULL',N'VALUE')
     OR (@presence_requested=N'VALUE' AND @requested_at_utc IS NULL)
     OR (@presence_requested IN(N'ABSENT',N'NULL') AND @requested_at_utc IS NOT NULL)
     OR (@presence_nfse=N'VALUE' AND @nfse_issued_at_utc IS NULL)
     OR (@presence_nfse IN(N'ABSENT',N'NULL') AND @nfse_issued_at_utc IS NOT NULL)
     OR (@presence_cte=N'VALUE' AND @cte_issued_at_utc IS NULL)
     OR (@presence_cte IN(N'ABSENT',N'NULL') AND @cte_issued_at_utc IS NOT NULL)
     OR (@presence_total=N'VALUE' AND @total_amount IS NULL)
     OR (@presence_total IN(N'ABSENT',N'NULL') AND @total_amount IS NOT NULL)
     OR (@presence_user IN(N'ABSENT',N'NULL') AND @user_name_normalized IS NOT NULL)
     OR (@presence_origin=N'VALUE' AND @origin_uf IS NULL)
     OR (@presence_origin IN(N'ABSENT',N'NULL') AND @origin_uf IS NOT NULL)
     OR (@presence_destination=N'VALUE' AND @destination_uf IS NULL)
     OR (@presence_destination IN(N'ABSENT',N'NULL') AND @destination_uf IS NOT NULL)
  ) THROW 51900,N'Presença tri-state de Cotações é inválida.',1;
  -- As rejeições puramente contratuais acima não iniciam transação. A escrita
  -- tipada abaixo continua protegida por XACT_ABORT e transação própria.
  SET XACT_ABORT ON;
  BEGIN TRANSACTION;
  DECLARE @entity NVARCHAR(128),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
  SELECT @entity=p.entity_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
  FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
  JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id
  WHERE a.execution_id=@execution_id;
  IF @entity COLLATE Latin1_General_100_BIN2<>N'cotacoes' THROW 51901,N'A execução não pertence a Cotações.',1;
  IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
     OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
    THROW 51903,N'Cotações exige source_instance e tenant_scope explícitos, sem sentinelas.',1;
  DECLARE @hash CHAR(64)=CASE WHEN @validation_disposition=N'VALID' THEN LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARBINARY(MAX),CONCAT(N'cotacoes-attributes-v1|',@payload_json,N'|',@field_presence_json))),2)) END;
  DECLARE @row_fingerprint_version NVARCHAR(128)=
    CASE WHEN @validation_disposition=N'VALID' THEN N'cotacoes-envelope-v1' END;
  DECLARE @presence_fingerprint_version NVARCHAR(128)=
    CASE WHEN @validation_disposition=N'VALID' THEN N'cotacoes-presence-v1' END;
  DECLARE @source_freshness_at_utc DATETIME2(3)=
    COALESCE(@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc);
  EXEC stg.usp_stage_record @execution_id,@input_batch_number,@input_record_ordinal,@source_key,
    @row_fingerprint_version,@hash,@presence_fingerprint_version,@hash,
    @source_freshness_at_utc,@validation_disposition,@quarantine_reason_code,@observed_at_utc;
  IF @validation_disposition=N'QUARANTINE' BEGIN COMMIT TRANSACTION; RETURN; END;
  DECLARE @stage_record_id BIGINT; SELECT @stage_record_id=stage_record_id FROM stg.execution_record WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND input_batch_number=@input_batch_number AND input_record_ordinal=@input_record_ordinal;
  IF EXISTS(SELECT 1 FROM stg.cotacao_record WITH(UPDLOCK,HOLDLOCK) WHERE stage_record_id=@stage_record_id AND attribute_hash<>@hash) THROW 51902,N'Retry de Cotações possui conteúdo divergente.',1;
  IF NOT EXISTS(SELECT 1 FROM stg.cotacao_record WHERE stage_record_id=@stage_record_id)
    INSERT stg.cotacao_record(
      stage_record_id,execution_id,source_key,source_key_wire_type,payload_json,field_presence_json,
      user_name_normalized,nfse_issued_at_utc,cte_issued_at_utc,requested_at_utc,freshness_at_utc,
      freshness_origin,freshness_business_date,total_amount,currency_code,origin_uf,destination_uf,attribute_hash
    ) VALUES(
      @stage_record_id,@execution_id,@source_key,N'INTEGER',@payload_json,@field_presence_json,
      @user_name_normalized,@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc,
      COALESCE(@nfse_issued_at_utc,@cte_issued_at_utc,@requested_at_utc),
      CASE WHEN @nfse_issued_at_utc IS NOT NULL THEN N'NFSE_ISSUED_AT' WHEN @cte_issued_at_utc IS NOT NULL THEN N'CTE_ISSUED_AT' ELSE N'REQUESTED_AT' END,
      @freshness_business_date,@total_amount,@currency_code,@origin_uf,@destination_uf,@hash
    );
  COMMIT TRANSACTION;
END;
GO
CREATE OR ALTER TRIGGER ctl.trg_cotacao_prepare_candidate_set ON ctl.execution_promotion_result AFTER INSERT AS
BEGIN
  SET NOCOUNT ON;
  INSERT ctl.cotacao_promotion_result(execution_id,candidate_rows,typed_candidate_rows,conflicting_root_keys,validation_state)
  SELECT i.execution_id,i.candidate_rows,COALESCE(t.typed,0),COALESCE(c.conflicts,0),CASE WHEN i.candidate_rows=COALESCE(t.typed,0) AND COALESCE(c.conflicts,0)=0 AND i.quarantined_root_keys=0 THEN N'PASSED' ELSE N'BLOCKED' END
  FROM inserted i JOIN ctl.execution_attempt a ON a.execution_id=i.execution_id JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
  OUTER APPLY(SELECT COUNT_BIG(*) typed FROM stg.execution_candidate x JOIN stg.cotacao_record r ON r.stage_record_id=x.winner_stage_record_id WHERE x.execution_id=i.execution_id)t
  OUTER APPLY(SELECT COUNT_BIG(*) conflicts FROM(SELECT source_key FROM stg.cotacao_record WHERE execution_id=i.execution_id GROUP BY source_key,freshness_at_utc HAVING COUNT(DISTINCT attribute_hash)>1)q)c
  WHERE p.entity_name=N'cotacoes';
END;
GO
-- A tarifa é resolvida somente por release explícita; ausência não pode virar zero.
CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_cotacoes
  @execution_id UNIQUEIDENTIFIER,@contract_version NVARCHAR(MAX),@contract_fingerprint NVARCHAR(MAX),@configuration_version NVARCHAR(MAX),@configuration_fingerprint NVARCHAR(MAX),@reference_release_id BIGINT
AS
BEGIN
  SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
  IF @reference_release_id IS NULL THROW 51910,N'COT-02 exige reference_release_id explícito.',1;
  IF NOT EXISTS(SELECT 1 FROM ctl.cotacao_promotion_result WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id AND validation_state=N'PASSED') THROW 51911,N'Candidate set de Cotações não está apto.',1;
  DECLARE @environment_name NVARCHAR(32),@source_instance NVARCHAR(128),@tenant_scope NVARCHAR(128);
  SELECT @environment_name=p.environment_name,@source_instance=p.source_instance,@tenant_scope=p.tenant_scope
  FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
  JOIN ctl.execution_partition p WITH(HOLDLOCK) ON p.partition_id=a.partition_id WHERE a.execution_id=@execution_id;
  IF UPPER(@source_instance) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
     OR UPPER(@tenant_scope) IN(N'DEFAULT',N'GLOBAL',N'SINGLETON')
    THROW 51903,N'Cotações exige source_instance e tenant_scope explícitos, sem sentinelas.',1;
  -- Não há current, wildcard ou fallback: a release precisa ser QUOTE_TARIFF, ratificada para
  -- SHADOW e sem revogação no mesmo escopo.
  IF NOT EXISTS(
    SELECT 1 FROM ref.reference_release r WITH(UPDLOCK,HOLDLOCK)
    JOIN ref.reference_release_ratification rat WITH(UPDLOCK,HOLDLOCK)
      ON rat.reference_release_id=r.reference_release_id AND rat.activation_scope=N'SHADOW'
    LEFT JOIN ref.reference_release_revocation rev WITH(UPDLOCK,HOLDLOCK)
      ON rev.reference_release_id=rat.reference_release_id AND rev.activation_scope=rat.activation_scope
    WHERE r.reference_release_id=@reference_release_id AND r.family_code=N'QUOTE_TARIFF'
      AND rev.reference_release_id IS NULL
  ) THROW 51912,N'Release tarifária ausente, não ratificada ou revogada.',1;

  -- ABSENT consulta somente a mesma raiz física escopada; NULL limpa e VALUE aplica,
  -- inclusive zero. Essa decisão precede obrigatoriamente a resolução tarifária.
  DECLARE @effective_candidates TABLE (
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    stage_record_id BIGINT NOT NULL,
    user_name_normalized NVARCHAR(MAX) NULL,
    total_amount DECIMAL(19,4) NULL,
    origin_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL,
    destination_uf CHAR(2) COLLATE Latin1_General_100_BIN2 NULL
  );
  INSERT @effective_candidates(
    source_key,stage_record_id,user_name_normalized,total_amount,origin_uf,destination_uf
  )
  SELECT candidate.source_key,typed.stage_record_id,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_uer_name')
      WHEN N'ABSENT' THEN current_record.user_name_normalized
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.user_name_normalized END,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_qes_total')
      WHEN N'ABSENT' THEN current_record.total_amount
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.total_amount END,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_qes_ony_sae_code')
      WHEN N'ABSENT' THEN current_record.origin_uf
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.origin_uf END,
    CASE JSON_VALUE(typed.field_presence_json,N'$.qoe_qes_diy_sae_code')
      WHEN N'ABSENT' THEN current_record.destination_uf
      WHEN N'NULL' THEN NULL
      WHEN N'VALUE' THEN typed.destination_uf END
  FROM stg.execution_candidate candidate
  JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
  LEFT JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))
    ON current_record.environment_name=@environment_name
   AND current_record.source_instance=@source_instance
   AND current_record.tenant_scope=@tenant_scope
   AND current_record.entity_name=N'cotacoes'
   AND current_record.source_key=candidate.source_key
  WHERE candidate.execution_id=@execution_id;

  DECLARE @tariff_resolution TABLE (
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    reference_release_id BIGINT NOT NULL, effective_date DATE NOT NULL,
    minimum_amount DECIMAL(19,4) NOT NULL, currency_code CHAR(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    unit_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    rounding_mode NVARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL
  );
  INSERT @tariff_resolution(source_key,reference_release_id,effective_date,minimum_amount,currency_code,unit_code,rounding_mode)
  SELECT candidate.source_key,tariff.reference_release_id,typed.freshness_business_date,
         tariff.minimum_amount,tariff.currency_code,tariff.unit_code,tariff.rounding_mode
  FROM stg.execution_candidate candidate
  JOIN @effective_candidates effective ON effective.source_key=candidate.source_key
  JOIN stg.cotacao_record typed ON typed.stage_record_id=effective.stage_record_id
  CROSS APPLY(
    SELECT COUNT_BIG(*) AS matching_rows FROM ref.tarifa_rota_uf WITH(UPDLOCK,HOLDLOCK,INDEX(IX_ref_tarifa_rota_uf_lookup))
    WHERE reference_release_id=@reference_release_id AND origin_uf=effective.origin_uf
      AND destination_uf=effective.destination_uf AND typed.freshness_business_date>=valid_from
      AND typed.freshness_business_date<valid_to_exclusive
  ) matches
  OUTER APPLY(
    SELECT TOP(1) reference_release_id,coverage_state,minimum_amount,currency_code,unit_code,rounding_mode
    FROM ref.tarifa_rota_uf WITH(UPDLOCK,HOLDLOCK,INDEX(IX_ref_tarifa_rota_uf_lookup))
    WHERE reference_release_id=@reference_release_id AND origin_uf=effective.origin_uf
      AND destination_uf=effective.destination_uf AND typed.freshness_business_date>=valid_from
      AND typed.freshness_business_date<valid_to_exclusive
    ORDER BY valid_from
  ) tariff
  WHERE candidate.execution_id=@execution_id AND effective.origin_uf IS NOT NULL AND effective.destination_uf IS NOT NULL
    AND matches.matching_rows=1 AND tariff.coverage_state=N'PRICED'
    AND tariff.minimum_amount>CONVERT(DECIMAL(19,4),0)
    AND tariff.currency_code COLLATE Latin1_General_100_BIN2 LIKE '[A-Z][A-Z][A-Z]'
    AND tariff.unit_code IN(N'PER_SHIPMENT',N'PER_WEIGHT',N'PER_VOLUME')
    AND tariff.rounding_mode IN(N'HALF_UP',N'HALF_EVEN',N'DOWN',N'UP');
  IF (SELECT COUNT_BIG(*) FROM @tariff_resolution)<>(SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id=@execution_id)
    THROW 51914,N'Tarifa direcional não coberta, ambígua ou inválida: COT-02 falha fechada.',1;

  IF EXISTS(
    SELECT 1 FROM stg.execution_candidate candidate
    JOIN stg.cotacao_record typed ON typed.stage_record_id=candidate.winner_stage_record_id
    JOIN core.cotacao current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))
      ON current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance
     AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'cotacoes'
     AND current_record.source_key=candidate.source_key
    WHERE candidate.execution_id=@execution_id AND current_record.freshness_at_utc=typed.freshness_at_utc
      AND current_record.attribute_hash<>typed.attribute_hash
  ) THROW 51913,N'Empate de frescor com conteúdo divergente exige quarentena.',1;

  DECLARE @common_result TABLE (
    execution_id UNIQUEIDENTIFIER NOT NULL,candidate_rows BIGINT NOT NULL,inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,reactivated_rows BIGINT NOT NULL,noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,reconciled_at_utc DATETIME2(3) NOT NULL,published_at_utc DATETIME2(3) NOT NULL,
    incremental_frontier_before_utc DATETIME2(3) NULL,incremental_frontier_after_utc DATETIME2(3) NULL
  );
  INSERT @common_result EXEC core.usp_apply_reconcile_publish_execution
    @execution_id,@contract_version,@contract_fingerprint,@configuration_version,@configuration_fingerprint;
  INSERT core.cotacao(
    record_state_id,environment_name,source_instance,tenant_scope,entity_name,source_key,payload_json,
    field_presence_json,user_name_normalized,freshness_at_utc,freshness_origin,freshness_business_date,
    total_amount,currency_code,origin_uf,destination_uf,tariff_reference_release_id,tariff_effective_date,
    tariff_minimum_amount,tariff_currency_code,tariff_unit_code,tariff_rounding_mode,attribute_hash,active,
    first_seen_execution_id,last_seen_execution_id
  )
  SELECT app.record_state_id,@environment_name,@source_instance,@tenant_scope,N'cotacoes',typed.source_key,
         typed.payload_json,typed.field_presence_json,effective.user_name_normalized,typed.freshness_at_utc,
         typed.freshness_origin,typed.freshness_business_date,effective.total_amount,CAST(NULL AS CHAR(3)),
         effective.origin_uf,effective.destination_uf,tariff.reference_release_id,tariff.effective_date,
         tariff.minimum_amount,tariff.currency_code,tariff.unit_code,tariff.rounding_mode,typed.attribute_hash,
         1,@execution_id,@execution_id
  FROM stg.execution_candidate x JOIN stg.cotacao_record typed ON typed.stage_record_id=x.winner_stage_record_id
  JOIN @effective_candidates effective ON effective.source_key=x.source_key
  JOIN @tariff_resolution tariff ON tariff.source_key=x.source_key
  JOIN recon.execution_candidate_application app ON app.execution_id=x.execution_id AND app.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND NOT EXISTS(
    SELECT 1 FROM core.cotacao current_record WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))
    WHERE current_record.environment_name=@environment_name AND current_record.source_instance=@source_instance
      AND current_record.tenant_scope=@tenant_scope AND current_record.entity_name=N'cotacoes'
      AND current_record.source_key=x.source_key
  );
  UPDATE current_record SET payload_json=typed.payload_json,field_presence_json=typed.field_presence_json,
    user_name_normalized=effective.user_name_normalized,freshness_at_utc=typed.freshness_at_utc,
    freshness_origin=typed.freshness_origin,freshness_business_date=typed.freshness_business_date,
    total_amount=effective.total_amount,currency_code=NULL,origin_uf=effective.origin_uf,
    destination_uf=effective.destination_uf,tariff_reference_release_id=tariff.reference_release_id,
    tariff_effective_date=tariff.effective_date,tariff_minimum_amount=tariff.minimum_amount,
    tariff_currency_code=tariff.currency_code,tariff_unit_code=tariff.unit_code,
    tariff_rounding_mode=tariff.rounding_mode,attribute_hash=typed.attribute_hash,last_seen_execution_id=@execution_id
  FROM core.cotacao current_record JOIN stg.execution_candidate x ON x.source_key=current_record.source_key
  JOIN stg.cotacao_record typed ON typed.stage_record_id=x.winner_stage_record_id
  JOIN @effective_candidates effective ON effective.source_key=x.source_key
  JOIN @tariff_resolution tariff ON tariff.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND current_record.environment_name=@environment_name
    AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
    AND current_record.entity_name=N'cotacoes' AND typed.freshness_at_utc>current_record.freshness_at_utc;
  UPDATE current_record SET last_seen_execution_id=@execution_id
  FROM core.cotacao current_record JOIN stg.execution_candidate x ON x.source_key=current_record.source_key
  JOIN recon.execution_candidate_application app ON app.execution_id=x.execution_id AND app.source_key=x.source_key
  WHERE x.execution_id=@execution_id AND current_record.environment_name=@environment_name
    AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
    AND current_record.entity_name=N'cotacoes' AND app.application_disposition IN(N'NO_OP',N'STALE_NO_OP');
  INSERT recon.cotacao_root_presence_observation(execution_id,cotacao_id,observed_at_utc,snapshot_completeness,absence_evaluation)
  SELECT @execution_id,current_record.cotacao_id,SYSUTCDATETIME(),N'BLOCKED_NO_COMPLETENESS_PROOF',N'NOT_EVALUATED'
  FROM core.cotacao current_record JOIN stg.execution_candidate x ON x.source_key=current_record.source_key
  WHERE x.execution_id=@execution_id AND current_record.environment_name=@environment_name
    AND current_record.source_instance=@source_instance AND current_record.tenant_scope=@tenant_scope
    AND current_record.entity_name=N'cotacoes'
    AND NOT EXISTS(SELECT 1 FROM recon.cotacao_root_presence_observation observed
                   WHERE observed.execution_id=@execution_id AND observed.cotacao_id=current_record.cotacao_id);
  COMMIT TRANSACTION;
  SELECT execution_id,candidate_rows,inserted_rows,updated_rows,reactivated_rows,noop_rows,stale_noop_rows,
         reconciled_at_utc,published_at_utc,incremental_frontier_before_utc,incremental_frontier_after_utc
  FROM @common_result;
END;
GO
EXEC dbo.usp_publish_v2_procedure_grant N'stg',N'usp_stage_cotacao_record',N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant N'core',N'usp_apply_reconcile_publish_cotacoes',N'v2_runtime';
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::stg TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::core TO v2_runtime;
DENY INSERT,UPDATE,DELETE,SELECT ON SCHEMA::recon TO v2_runtime;
GO
