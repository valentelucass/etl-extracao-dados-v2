-- MAT-03 lateral synthetic inputs: never inserted as fields of ESL /data or as final revenue.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE stg.expansion_lab_freight_terms (
 term_id BIGINT IDENTITY NOT NULL PRIMARY KEY,execution_id UNIQUEIDENTIFIER NOT NULL,batch_number INT NOT NULL,input_ordinal INT NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,revision INT NOT NULL,
 billing_reference_date DATE NULL,classification NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,courtesy BIT NULL,eligible BIT NULL,
 fallback_volumes INT NULL,payer_token CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,currency CHAR(3) NOT NULL,unit VARCHAR(16) NOT NULL,
 active BIT NOT NULL,evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT UQ_exp_freight_terms UNIQUE(execution_id,batch_number,input_ordinal),
 CONSTRAINT FK_exp_freight_terms_time FOREIGN KEY(execution_id,batch_number,input_ordinal) REFERENCES stg.expansion_lab_dependency_time(execution_id,batch_number,input_ordinal),
 CONSTRAINT CK_exp_freight_terms CHECK(revision BETWEEN 1 AND 1000 AND source_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(source_key,9,256) NOT LIKE N'%[^0-9]%'
 AND DATALENGTH(source_key)=DATALENGTH(RTRIM(source_key)) AND LEN(payer_token)=64 AND payer_token NOT LIKE '%[^0-9a-f]%'
 AND(fallback_volumes IS NULL OR fallback_volumes BETWEEN 0 AND 1000000) AND unit='MAJOR' AND currency COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z]%'
 AND evidence LIKE 'synthetic-%' AND DATALENGTH(evidence)=DATALENGTH(RTRIM(evidence)))
);
CREATE INDEX IX_exp_freight_terms_revision ON stg.expansion_lab_freight_terms(source_key,revision DESC) INCLUDE(execution_id,term_id);
GO
CREATE TRIGGER stg.trg_expansion_freight_terms_guard ON stg.expansion_lab_freight_terms AFTER INSERT,UPDATE,DELETE
AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53460,N'EXP_TERMS_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted t JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=t.execution_id
 WHERE c.state<>'CAPTURING' OR c.entity<>'FRETE') THROW 53461,N'EXP_TERMS_CAPTURE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM inserted t LEFT JOIN stg.execution_record s ON s.execution_id=t.execution_id
 AND s.input_batch_number=t.batch_number AND s.input_record_ordinal=t.input_ordinal
 WHERE s.source_key IS NULL OR s.source_key<>t.source_key OR s.validation_disposition<>N'VALID') THROW 53462,N'EXP_TERMS_SOURCE_BINDING',1;
END;
GO
CREATE FUNCTION core.ufn_expansion_freight_terms(@run_id UNIQUEIDENTIFIER)
RETURNS TABLE AS RETURN (
 WITH ranked AS(
 SELECT t.*,DENSE_RANK() OVER(PARTITION BY t.source_key ORDER BY t.revision DESC) freshness_rank
 FROM stg.expansion_lab_freight_terms t JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=t.execution_id
 WHERE c.run_id=@run_id AND c.state='COMPLETE' AND c.entity='FRETE'),
 variants AS(SELECT DISTINCT source_key,revision,billing_reference_date,CONVERT(VARBINARY(512),classification) classification_bytes,courtesy,eligible,fallback_volumes,
 payer_token,currency,unit,active,evidence FROM ranked WHERE freshness_rank=1),
 cohorts AS(SELECT source_key,COUNT_BIG(*) variant_count FROM variants GROUP BY source_key),
 pointers AS(SELECT r.source_key,MIN(r.term_id) term_id FROM ranked r JOIN cohorts c ON c.source_key=r.source_key AND c.variant_count=1 WHERE r.freshness_rank=1 GROUP BY r.source_key)
 SELECT c.source_key,CASE WHEN c.variant_count=1 THEN 'READY' ELSE 'CONFLICT' END terms_state,t.term_id,t.execution_id,t.revision,
 t.billing_reference_date,t.classification,t.courtesy,t.eligible,t.fallback_volumes,t.payer_token,t.currency,t.unit,t.active,t.evidence
 FROM cohorts c LEFT JOIN pointers p ON p.source_key=c.source_key LEFT JOIN stg.expansion_lab_freight_terms t ON t.term_id=p.term_id
);
GO
