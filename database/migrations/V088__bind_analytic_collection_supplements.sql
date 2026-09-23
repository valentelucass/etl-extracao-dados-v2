-- Typed lateral GraphQL-shaped fixture, tied to a real captured Coleta root. No source identity inference.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE stg.analytic_collection_supplement (
 supplement_id BIGINT IDENTITY NOT NULL CONSTRAINT PK_ana_collection_supplement PRIMARY KEY,
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,comparison_bytes VARBINARY(MAX) NOT NULL,
 cancellation_user_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,destroy_user_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
  [request_hour_p] VARCHAR(6) NOT NULL,[request_hour_w] VARCHAR(8) NOT NULL,[request_hour_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[request_hour] BIGINT NULL,
 [vehicle_type_id_p] VARCHAR(6) NOT NULL,[vehicle_type_id_w] VARCHAR(8) NOT NULL,[vehicle_type_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[vehicle_type_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [customer_name_p] VARCHAR(6) NOT NULL,[customer_name_w] VARCHAR(8) NOT NULL,[customer_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[customer_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [customer_document_p] VARCHAR(6) NOT NULL,[customer_document_w] VARCHAR(8) NOT NULL,[customer_document_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[customer_document] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [address_line_p] VARCHAR(6) NOT NULL,[address_line_w] VARCHAR(8) NOT NULL,[address_line_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[address_line] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [address_number_p] VARCHAR(6) NOT NULL,[address_number_w] VARCHAR(8) NOT NULL,[address_number_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[address_number] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [address_complement_p] VARCHAR(6) NOT NULL,[address_complement_w] VARCHAR(8) NOT NULL,[address_complement_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[address_complement] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [branch_source_id_p] VARCHAR(6) NOT NULL,[branch_source_id_w] VARCHAR(8) NOT NULL,[branch_source_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[branch_source_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [cancellation_user_id_p] VARCHAR(6) NOT NULL,[cancellation_user_id_w] VARCHAR(8) NOT NULL,[cancellation_user_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[cancellation_user_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [destroy_reason_p] VARCHAR(6) NOT NULL,[destroy_reason_w] VARCHAR(8) NOT NULL,[destroy_reason_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[destroy_reason] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [destroy_user_id_p] VARCHAR(6) NOT NULL,[destroy_user_id_w] VARCHAR(8) NOT NULL,[destroy_user_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[destroy_user_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [status_updated_at_p] VARCHAR(6) NOT NULL,[status_updated_at_w] VARCHAR(8) NOT NULL,[status_updated_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[status_updated_at] BIGINT NULL,[status_updated_at_nano] INT NULL,
 CONSTRAINT UQ_ana_collection_supplement UNIQUE(run_id,source_key,source_execution,revision),
 CONSTRAINT FK_ana_collection_supplement_capture FOREIGN KEY(source_execution,source_key) REFERENCES stg.relational_lab_root(execution_id,source_key),
 CONSTRAINT CK_ana_collection_supplement CHECK(revision BETWEEN 1 AND 100000 AND evidence='synthetic-graphql-lateral-v1' AND DATALENGTH(comparison_bytes) BETWEEN 1 AND 262144)
);
GO
CREATE TRIGGER stg.trg_analytic_collection_supplement ON stg.analytic_collection_supplement AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON;THROW 53730,N'ANA_COLLECTION_SUPPLEMENT_IMMUTABLE',1;END;
GO
CREATE TYPE stg.analytic_collection_supplement_batch AS TABLE (
 source_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,source_execution UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,
 comparison_bytes VARBINARY(MAX) NOT NULL,cancellation_user_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,destroy_user_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
  [request_hour_p] VARCHAR(6) NOT NULL,[request_hour_w] VARCHAR(8) NOT NULL,[request_hour_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[request_hour] BIGINT NULL,
 [vehicle_type_id_p] VARCHAR(6) NOT NULL,[vehicle_type_id_w] VARCHAR(8) NOT NULL,[vehicle_type_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[vehicle_type_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [customer_name_p] VARCHAR(6) NOT NULL,[customer_name_w] VARCHAR(8) NOT NULL,[customer_name_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[customer_name] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [customer_document_p] VARCHAR(6) NOT NULL,[customer_document_w] VARCHAR(8) NOT NULL,[customer_document_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[customer_document] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [address_line_p] VARCHAR(6) NOT NULL,[address_line_w] VARCHAR(8) NOT NULL,[address_line_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[address_line] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [address_number_p] VARCHAR(6) NOT NULL,[address_number_w] VARCHAR(8) NOT NULL,[address_number_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[address_number] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [address_complement_p] VARCHAR(6) NOT NULL,[address_complement_w] VARCHAR(8) NOT NULL,[address_complement_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[address_complement] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [branch_source_id_p] VARCHAR(6) NOT NULL,[branch_source_id_w] VARCHAR(8) NOT NULL,[branch_source_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[branch_source_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [cancellation_user_id_p] VARCHAR(6) NOT NULL,[cancellation_user_id_w] VARCHAR(8) NOT NULL,[cancellation_user_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[cancellation_user_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [destroy_reason_p] VARCHAR(6) NOT NULL,[destroy_reason_w] VARCHAR(8) NOT NULL,[destroy_reason_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[destroy_reason] NVARCHAR(1024) COLLATE Latin1_General_100_BIN2 NULL,
 [destroy_user_id_p] VARCHAR(6) NOT NULL,[destroy_user_id_w] VARCHAR(8) NOT NULL,[destroy_user_id_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[destroy_user_id] VARCHAR(40) COLLATE Latin1_General_100_BIN2 NULL,
 [status_updated_at_p] VARCHAR(6) NOT NULL,[status_updated_at_w] VARCHAR(8) NOT NULL,[status_updated_at_raw] NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NULL,[status_updated_at] BIGINT NULL,[status_updated_at_nano] INT NULL,PRIMARY KEY(source_key,source_execution,revision)
);
GO
CREATE PROCEDURE stg.usp_bind_analytic_collection_supplements @run_id UNIQUEIDENTIFIER,@batch stg.analytic_collection_supplement_batch READONLY AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT OFF;
 IF @@TRANCOUNT=0 OR (SELECT COUNT_BIG(*) FROM @batch) NOT BETWEEN 1 AND 16
 OR (SELECT SUM(CONVERT(BIGINT,DATALENGTH(comparison_bytes))) FROM @batch)>262144
 THROW 53731,N'ANA_COLLECTION_SUPPLEMENT_BOUND',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF EXISTS(SELECT 1 FROM @batch b WHERE b.revision NOT BETWEEN 1 AND 100000 OR DATALENGTH(comparison_bytes) NOT BETWEEN 1 AND 262144
 OR NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_source_group g JOIN ctl.relational_lab_capture c ON c.run_id=g.relational_run
 JOIN stg.relational_lab_root r ON r.execution_id=c.execution_id
 WHERE g.run_id=@run_id AND c.entity_name=N'coletas' AND c.execution_id=b.source_execution AND r.source_key=b.source_key))
 THROW 53732,N'ANA_COLLECTION_SUPPLEMENT_SOURCE',1;
 IF EXISTS(SELECT 1 FROM @batch b WHERE b.[request_hour_p] NOT IN('ABSENT','NULL','VALUE') OR b.[request_hour_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[request_hour_p]='VALUE' AND(b.[request_hour] IS NULL OR b.[request_hour_raw] IS NULL OR b.[request_hour] NOT BETWEEN 0 AND 86399999999999))
 OR(b.[request_hour_p]<>'VALUE' AND(b.[request_hour] IS NOT NULL OR b.[request_hour_raw] IS NOT NULL))
 OR b.[vehicle_type_id_p] NOT IN('ABSENT','NULL','VALUE') OR b.[vehicle_type_id_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[vehicle_type_id_p]='VALUE' AND(b.[vehicle_type_id] IS NULL OR b.[vehicle_type_id_raw] IS NULL OR TRY_CONVERT(BIGINT,b.[vehicle_type_id]) IS NULL OR TRY_CONVERT(BIGINT,b.[vehicle_type_id])<0))
 OR(b.[vehicle_type_id_p]<>'VALUE' AND(b.[vehicle_type_id] IS NOT NULL OR b.[vehicle_type_id_raw] IS NOT NULL))
 OR b.[customer_name_p] NOT IN('ABSENT','NULL','VALUE') OR b.[customer_name_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[customer_name_p]='VALUE' AND(b.[customer_name] IS NULL OR b.[customer_name_raw] IS NULL))
 OR(b.[customer_name_p]<>'VALUE' AND(b.[customer_name] IS NOT NULL OR b.[customer_name_raw] IS NOT NULL))
 OR b.[customer_document_p] NOT IN('ABSENT','NULL','VALUE') OR b.[customer_document_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[customer_document_p]='VALUE' AND(b.[customer_document] IS NULL OR b.[customer_document_raw] IS NULL))
 OR(b.[customer_document_p]<>'VALUE' AND(b.[customer_document] IS NOT NULL OR b.[customer_document_raw] IS NOT NULL))
 OR b.[address_line_p] NOT IN('ABSENT','NULL','VALUE') OR b.[address_line_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[address_line_p]='VALUE' AND(b.[address_line] IS NULL OR b.[address_line_raw] IS NULL))
 OR(b.[address_line_p]<>'VALUE' AND(b.[address_line] IS NOT NULL OR b.[address_line_raw] IS NOT NULL))
 OR b.[address_number_p] NOT IN('ABSENT','NULL','VALUE') OR b.[address_number_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[address_number_p]='VALUE' AND(b.[address_number] IS NULL OR b.[address_number_raw] IS NULL))
 OR(b.[address_number_p]<>'VALUE' AND(b.[address_number] IS NOT NULL OR b.[address_number_raw] IS NOT NULL))
 OR b.[address_complement_p] NOT IN('ABSENT','NULL','VALUE') OR b.[address_complement_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[address_complement_p]='VALUE' AND(b.[address_complement] IS NULL OR b.[address_complement_raw] IS NULL))
 OR(b.[address_complement_p]<>'VALUE' AND(b.[address_complement] IS NOT NULL OR b.[address_complement_raw] IS NOT NULL))
 OR b.[branch_source_id_p] NOT IN('ABSENT','NULL','VALUE') OR b.[branch_source_id_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[branch_source_id_p]='VALUE' AND(b.[branch_source_id] IS NULL OR b.[branch_source_id_raw] IS NULL OR TRY_CONVERT(BIGINT,b.[branch_source_id]) IS NULL OR TRY_CONVERT(BIGINT,b.[branch_source_id])<0))
 OR(b.[branch_source_id_p]<>'VALUE' AND(b.[branch_source_id] IS NOT NULL OR b.[branch_source_id_raw] IS NOT NULL))
 OR b.[cancellation_user_id_p] NOT IN('ABSENT','NULL','VALUE') OR b.[cancellation_user_id_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[cancellation_user_id_p]='VALUE' AND(b.[cancellation_user_id] IS NULL OR b.[cancellation_user_id_raw] IS NULL OR TRY_CONVERT(BIGINT,b.[cancellation_user_id]) IS NULL OR TRY_CONVERT(BIGINT,b.[cancellation_user_id])<0))
 OR(b.[cancellation_user_id_p]<>'VALUE' AND(b.[cancellation_user_id] IS NOT NULL OR b.[cancellation_user_id_raw] IS NOT NULL))
 OR b.[destroy_reason_p] NOT IN('ABSENT','NULL','VALUE') OR b.[destroy_reason_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[destroy_reason_p]='VALUE' AND(b.[destroy_reason] IS NULL OR b.[destroy_reason_raw] IS NULL))
 OR(b.[destroy_reason_p]<>'VALUE' AND(b.[destroy_reason] IS NOT NULL OR b.[destroy_reason_raw] IS NOT NULL))
 OR b.[destroy_user_id_p] NOT IN('ABSENT','NULL','VALUE') OR b.[destroy_user_id_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[destroy_user_id_p]='VALUE' AND(b.[destroy_user_id] IS NULL OR b.[destroy_user_id_raw] IS NULL OR TRY_CONVERT(BIGINT,b.[destroy_user_id]) IS NULL OR TRY_CONVERT(BIGINT,b.[destroy_user_id])<0))
 OR(b.[destroy_user_id_p]<>'VALUE' AND(b.[destroy_user_id] IS NOT NULL OR b.[destroy_user_id_raw] IS NOT NULL))
 OR b.[status_updated_at_p] NOT IN('ABSENT','NULL','VALUE') OR b.[status_updated_at_w] NOT IN('ABSENT','NULL','STRING','INTEGER','NUMBER','BOOLEAN')
 OR(b.[status_updated_at_p]='VALUE' AND(b.[status_updated_at] IS NULL OR b.[status_updated_at_raw] IS NULL OR b.[status_updated_at_nano] IS NULL OR b.[status_updated_at_nano] NOT BETWEEN 0 AND 999999999))
 OR(b.[status_updated_at_p]<>'VALUE' AND(b.[status_updated_at] IS NOT NULL OR b.[status_updated_at_raw] IS NOT NULL OR b.[status_updated_at_nano] IS NOT NULL))) THROW 53733,N'ANA_COLLECTION_SUPPLEMENT_VALUE',1;
 IF EXISTS(SELECT 1 FROM @batch b CROSS APPLY(VALUES(b.cancellation_user_key),(b.destroy_user_key)) keys(source_key)
 WHERE keys.source_key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_execution_source attached
 JOIN core.usuario u ON u.last_seen_execution_id=attached.execution_id AND u.source_key=keys.source_key
 WHERE attached.run_id=@run_id AND attached.entity='USUARIO')) THROW 53734,N'ANA_COLLECTION_USER_BINDING_UNRESOLVED',1;
 IF EXISTS(SELECT 1 FROM @batch b JOIN stg.analytic_collection_supplement s ON s.run_id=@run_id AND s.source_key=b.source_key
 AND s.source_execution=b.source_execution AND s.revision=b.revision
 WHERE s.comparison_bytes<>b.comparison_bytes OR EXISTS(SELECT b.cancellation_user_key,b.destroy_user_key,b.[request_hour_p],b.[request_hour_w],b.[request_hour_raw],b.[request_hour],b.[vehicle_type_id_p],b.[vehicle_type_id_w],b.[vehicle_type_id_raw],b.[vehicle_type_id],b.[customer_name_p],b.[customer_name_w],b.[customer_name_raw],b.[customer_name],b.[customer_document_p],b.[customer_document_w],b.[customer_document_raw],b.[customer_document],b.[address_line_p],b.[address_line_w],b.[address_line_raw],b.[address_line],b.[address_number_p],b.[address_number_w],b.[address_number_raw],b.[address_number],b.[address_complement_p],b.[address_complement_w],b.[address_complement_raw],b.[address_complement],b.[branch_source_id_p],b.[branch_source_id_w],b.[branch_source_id_raw],b.[branch_source_id],b.[cancellation_user_id_p],b.[cancellation_user_id_w],b.[cancellation_user_id_raw],b.[cancellation_user_id],b.[destroy_reason_p],b.[destroy_reason_w],b.[destroy_reason_raw],b.[destroy_reason],b.[destroy_user_id_p],b.[destroy_user_id_w],b.[destroy_user_id_raw],b.[destroy_user_id],b.[status_updated_at_p],b.[status_updated_at_w],b.[status_updated_at_raw],b.[status_updated_at],b.[status_updated_at_nano]
 EXCEPT SELECT s.cancellation_user_key,s.destroy_user_key,s.[request_hour_p],s.[request_hour_w],s.[request_hour_raw],s.[request_hour],s.[vehicle_type_id_p],s.[vehicle_type_id_w],s.[vehicle_type_id_raw],s.[vehicle_type_id],s.[customer_name_p],s.[customer_name_w],s.[customer_name_raw],s.[customer_name],s.[customer_document_p],s.[customer_document_w],s.[customer_document_raw],s.[customer_document],s.[address_line_p],s.[address_line_w],s.[address_line_raw],s.[address_line],s.[address_number_p],s.[address_number_w],s.[address_number_raw],s.[address_number],s.[address_complement_p],s.[address_complement_w],s.[address_complement_raw],s.[address_complement],s.[branch_source_id_p],s.[branch_source_id_w],s.[branch_source_id_raw],s.[branch_source_id],s.[cancellation_user_id_p],s.[cancellation_user_id_w],s.[cancellation_user_id_raw],s.[cancellation_user_id],s.[destroy_reason_p],s.[destroy_reason_w],s.[destroy_reason_raw],s.[destroy_reason],s.[destroy_user_id_p],s.[destroy_user_id_w],s.[destroy_user_id_raw],s.[destroy_user_id],s.[status_updated_at_p],s.[status_updated_at_w],s.[status_updated_at_raw],s.[status_updated_at],s.[status_updated_at_nano]))
 THROW 53735,N'ANA_COLLECTION_SUPPLEMENT_RETRY_DIVERGENT',1;
 INSERT stg.analytic_collection_supplement(run_id,source_key,source_execution,revision,comparison_bytes,cancellation_user_key,destroy_user_key,evidence,[request_hour_p],[request_hour_w],[request_hour_raw],[request_hour],[vehicle_type_id_p],[vehicle_type_id_w],[vehicle_type_id_raw],[vehicle_type_id],[customer_name_p],[customer_name_w],[customer_name_raw],[customer_name],[customer_document_p],[customer_document_w],[customer_document_raw],[customer_document],[address_line_p],[address_line_w],[address_line_raw],[address_line],[address_number_p],[address_number_w],[address_number_raw],[address_number],[address_complement_p],[address_complement_w],[address_complement_raw],[address_complement],[branch_source_id_p],[branch_source_id_w],[branch_source_id_raw],[branch_source_id],[cancellation_user_id_p],[cancellation_user_id_w],[cancellation_user_id_raw],[cancellation_user_id],[destroy_reason_p],[destroy_reason_w],[destroy_reason_raw],[destroy_reason],[destroy_user_id_p],[destroy_user_id_w],[destroy_user_id_raw],[destroy_user_id],[status_updated_at_p],[status_updated_at_w],[status_updated_at_raw],[status_updated_at],[status_updated_at_nano])
 SELECT @run_id,b.source_key,b.source_execution,b.revision,b.comparison_bytes,b.cancellation_user_key,b.destroy_user_key,'synthetic-graphql-lateral-v1',b.[request_hour_p],b.[request_hour_w],b.[request_hour_raw],b.[request_hour],b.[vehicle_type_id_p],b.[vehicle_type_id_w],b.[vehicle_type_id_raw],b.[vehicle_type_id],b.[customer_name_p],b.[customer_name_w],b.[customer_name_raw],b.[customer_name],b.[customer_document_p],b.[customer_document_w],b.[customer_document_raw],b.[customer_document],b.[address_line_p],b.[address_line_w],b.[address_line_raw],b.[address_line],b.[address_number_p],b.[address_number_w],b.[address_number_raw],b.[address_number],b.[address_complement_p],b.[address_complement_w],b.[address_complement_raw],b.[address_complement],b.[branch_source_id_p],b.[branch_source_id_w],b.[branch_source_id_raw],b.[branch_source_id],b.[cancellation_user_id_p],b.[cancellation_user_id_w],b.[cancellation_user_id_raw],b.[cancellation_user_id],b.[destroy_reason_p],b.[destroy_reason_w],b.[destroy_reason_raw],b.[destroy_reason],b.[destroy_user_id_p],b.[destroy_user_id_w],b.[destroy_user_id_raw],b.[destroy_user_id],b.[status_updated_at_p],b.[status_updated_at_w],b.[status_updated_at_raw],b.[status_updated_at],b.[status_updated_at_nano]
 FROM @batch b WHERE NOT EXISTS(SELECT 1 FROM stg.analytic_collection_supplement s WHERE s.run_id=@run_id AND s.source_key=b.source_key AND s.source_execution=b.source_execution AND s.revision=b.revision);
 SELECT COUNT_BIG(*) accepted FROM @batch;
END;
GO
