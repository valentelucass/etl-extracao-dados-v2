-- ANA-01/04: declared source groups and temporal dimension assignments, never identity by display value.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.analytic_lab_source_group (
 run_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY REFERENCES ctl.analytic_lab_run(run_id),
 expansion_run UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.expansion_lab_run(run_id),
 relational_run UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT CK_analytic_source_group CHECK(evidence='synthetic-composition-v1')
);
GO
CREATE TRIGGER ctl.trg_analytic_source_group ON ctl.analytic_lab_source_group AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53560,N'ANA_SOURCE_GROUP_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_run a ON a.run_id=i.run_id
 JOIN ctl.expansion_lab_run e ON e.run_id=i.expansion_run JOIN ctl.relational_lab_run r ON r.run_id=i.relational_run
 WHERE a.window_start<>e.window_start OR a.window_end_exclusive<>e.window_end_exclusive
 OR a.window_start<>r.window_start OR a.window_end_exclusive<>DATEADD(day,1,r.window_end))
 THROW 53561,N'ANA_SOURCE_GROUP_WINDOW',1;
END;
GO
CREATE TABLE ctl.analytic_lab_execution_source (
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 execution_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.execution_attempt(execution_id),
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_analytic_execution_source PRIMARY KEY(run_id,entity,execution_id),
 CONSTRAINT CK_analytic_execution_source CHECK(entity IN('USUARIO','COT') AND evidence='synthetic-composition-v1')
);
GO
CREATE TRIGGER ctl.trg_analytic_execution_source ON ctl.analytic_lab_execution_source AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53562,N'ANA_EXECUTION_SOURCE_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.execution_attempt e ON e.execution_id=i.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id JOIN ctl.analytic_lab_run r ON r.run_id=i.run_id
 WHERE p.environment_name<>N'LOCAL_SHADOW' OR p.source_instance<>N'LOCAL_V2' OR p.tenant_scope<>N'LOCAL_V2'
 OR p.entity_name<>CASE i.entity WHEN 'USUARIO' THEN N'usuarios' ELSE N'cotacoes' END
 OR e.current_state<>N'PUBLISHED' OR CONVERT(DATE,p.partition_start_utc)<r.window_start
 OR CONVERT(DATE,p.partition_start_utc)>=r.window_end_exclusive)
 THROW 53563,N'ANA_EXECUTION_SOURCE_SCOPE_OR_STATE',1;
END;
GO
CREATE VIEW core.analytic_lab_source_current AS
 SELECT g.run_id,CONVERT(VARCHAR(8),d.entity) COLLATE Latin1_General_100_BIN2 entity,d.source_key,
 o.execution_id,c.partition_date business_date,CONVERT(BIT,CASE WHEN d.state='VALID' THEN 1 ELSE 0 END) usable
 FROM ctl.analytic_lab_source_group g JOIN core.expansion_lab_dependency d ON d.run_id=g.expansion_run
 JOIN stg.expansion_lab_dependency_observation o ON o.stage_record_id=d.stage_record_id
 JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=o.execution_id AND c.state='COMPLETE'
 UNION ALL
 SELECT g.run_id,CONVERT(VARCHAR(8),CASE r.entity_name WHEN N'manifestos' THEN 'MAN' WHEN N'coletas' THEN 'COL' ELSE 'REL_FRT' END),
 r.source_key,r.execution_id,c.business_date,CONVERT(BIT,1)
 FROM ctl.analytic_lab_source_group g JOIN core.relational_lab_root r ON r.run_id=g.relational_run
 JOIN ctl.relational_lab_capture c ON c.execution_id=r.execution_id
 UNION ALL
 SELECT DISTINCT g.run_id,CONVERT(VARCHAR(8),i.vertical),CONVERT(NVARCHAR(256),CONCAT(i.root_type,':',i.root_key)) COLLATE Latin1_General_100_BIN2,
 i.execution_id,i.partition_start,CONVERT(BIT,1)
 FROM ctl.analytic_lab_source_group g JOIN core.expansion_lab_current_input i ON i.run_id=g.expansion_run
 WHERE i.unresolved_conflict=0
 UNION ALL
 SELECT a.run_id,a.entity,u.source_key,u.last_seen_execution_id,CONVERT(DATE,u.last_seen_at_utc),u.active
 FROM ctl.analytic_lab_execution_source a JOIN core.usuario u ON u.last_seen_execution_id=a.execution_id
 WHERE a.entity='USUARIO'
 UNION ALL
 SELECT t.run_id,CONVERT(VARCHAR(8),'RAS'),CONVERT(NVARCHAR(256),t.trip_key),o.capture_id,
 c.start_date,t.active FROM core.analytic_raster_trip t JOIN stg.analytic_raster_trip o ON o.observation_id=t.observation_id
 JOIN ctl.analytic_raster_capture c ON c.capture_id=o.capture_id;
GO
CREATE TABLE ref.analytic_lab_dimension_binding (
 binding_id BIGINT IDENTITY NOT NULL PRIMARY KEY,
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NOT NULL,role VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
 dimension_kind VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,entity_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 revision INT NOT NULL,valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,active BIT NOT NULL,
 previous_entity_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT UQ_analytic_dimension_binding UNIQUE(run_id,entity,source_key,role,revision,valid_from),
 CONSTRAINT CK_analytic_dimension_binding CHECK(revision BETWEEN 1 AND 100000 AND valid_from<valid_to_exclusive
 AND entity IN('FRETE','LOC','MAN','COL','CAP','FAT','INV','SIN','RAS','USUARIO','COT')
 AND entity_key LIKE 'synthetic-%' AND evidence LIKE 'synthetic-%'
 AND ((dimension_kind='FILIAL' AND role IN('BRANCH','DEST_BRANCH','PERFORMANCE_BRANCH'))
 OR(dimension_kind='CLIENTE' AND role IN('PAYER','SENDER','RECIPIENT','CLIENT'))
 OR(dimension_kind='VEICULO' AND role IN('TRACTOR','TRAILER1','TRAILER2'))
 OR(dimension_kind='MOTORISTA' AND role='DRIVER') OR(dimension_kind='PLANOCONTAS' AND role='ACCOUNT')
 OR(dimension_kind='USUARIO' AND role IN('USER','CANCEL_USER','DESTROY_USER'))))
);
CREATE INDEX IX_analytic_binding_lookup ON ref.analytic_lab_dimension_binding(run_id,entity,source_key,role,valid_from,revision DESC)
 INCLUDE(entity_key,dimension_kind,valid_to_exclusive,active,source_execution);
GO
CREATE TRIGGER ref.trg_analytic_dimension_binding ON ref.analytic_lab_dimension_binding AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53564,N'ANA_DIMENSION_BINDING_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i WHERE NOT EXISTS(SELECT 1 FROM core.analytic_lab_source_current s
 WHERE s.run_id=i.run_id AND s.entity=i.entity AND s.source_key=i.source_key AND s.execution_id=i.source_execution AND s.usable=1))
 THROW 53565,N'ANA_BINDING_CAPTURE_REQUIRED',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_run r ON r.run_id=i.run_id
 WHERE i.valid_from<r.window_start OR i.valid_to_exclusive>r.window_end_exclusive)
 THROW 53566,N'ANA_BINDING_WINDOW',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ref.analytic_lab_dimension_binding b WITH(UPDLOCK,HOLDLOCK)
 ON b.run_id=i.run_id AND b.entity=i.entity AND b.source_key=i.source_key AND b.role=i.role AND b.revision=i.revision
 AND b.binding_id<>i.binding_id AND b.valid_from<i.valid_to_exclusive AND i.valid_from<b.valid_to_exclusive)
 THROW 53567,N'ANA_BINDING_OVERLAP',1;
 IF EXISTS(SELECT 1 FROM inserted i WHERE EXISTS(SELECT 1 FROM ref.analytic_lab_dimension_binding b
 WHERE b.run_id=i.run_id AND b.entity=i.entity AND b.source_key=i.source_key AND b.role=i.role
 AND b.revision<i.revision AND b.entity_key<>i.entity_key AND b.valid_from<i.valid_to_exclusive AND i.valid_from<b.valid_to_exclusive
 AND NOT EXISTS(SELECT 1 FROM ref.analytic_lab_dimension_binding newer WHERE newer.run_id=b.run_id AND newer.entity=b.entity
 AND newer.source_key=b.source_key AND newer.role=b.role AND newer.revision>b.revision AND newer.revision<i.revision
 AND newer.valid_from<i.valid_to_exclusive AND i.valid_from<newer.valid_to_exclusive)
 AND (i.previous_entity_key IS NULL OR i.previous_entity_key<>b.entity_key)))
 THROW 53568,N'ANA_REKEY_PROOF_REQUIRED',1;
END;
GO
CREATE TYPE ref.analytic_lab_dimension_binding_batch AS TABLE (
 entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NOT NULL,role VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL,
 dimension_kind VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,entity_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 revision INT NOT NULL,valid_from DATE NOT NULL,valid_to_exclusive DATE NOT NULL,active BIT NOT NULL,
 previous_entity_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 PRIMARY KEY(entity,source_key,role,revision,valid_from)
);
GO
CREATE PROCEDURE ref.usp_bind_analytic_dimensions @run_id UNIQUEIDENTIFIER,@rows ref.analytic_lab_dimension_binding_batch READONLY
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF (SELECT COUNT_BIG(*) FROM @rows) NOT BETWEEN 1 AND 64 THROW 53569,N'ANA_BINDING_BATCH_BOUND',1;
 IF EXISTS(SELECT * FROM @rows EXCEPT SELECT entity,source_key,source_execution,role,dimension_kind,entity_key,revision,
 valid_from,valid_to_exclusive,active,previous_entity_key,evidence FROM ref.analytic_lab_dimension_binding WHERE run_id=@run_id)
 BEGIN
  IF EXISTS(SELECT 1 FROM @rows i JOIN ref.analytic_lab_dimension_binding b ON b.run_id=@run_id AND b.entity=i.entity
  AND b.source_key=i.source_key AND b.role=i.role AND b.revision=i.revision AND b.valid_from=i.valid_from
  WHERE b.source_execution<>i.source_execution OR b.dimension_kind<>i.dimension_kind OR b.entity_key<>i.entity_key
  OR b.valid_to_exclusive<>i.valid_to_exclusive OR b.active<>i.active OR b.evidence<>i.evidence
  OR ISNULL(b.previous_entity_key,'')<>ISNULL(i.previous_entity_key,'')) THROW 53570,N'ANA_BINDING_REVISION_CONFLICT',1;
  INSERT ref.analytic_lab_dimension_binding(run_id,entity,source_key,source_execution,role,dimension_kind,entity_key,
  revision,valid_from,valid_to_exclusive,active,previous_entity_key,evidence)
  SELECT @run_id,i.* FROM @rows i WHERE NOT EXISTS(SELECT 1 FROM ref.analytic_lab_dimension_binding b
  WHERE b.run_id=@run_id AND b.entity=i.entity AND b.source_key=i.source_key AND b.role=i.role AND b.revision=i.revision AND b.valid_from=i.valid_from);
 END;
 SELECT COUNT_BIG(*) accepted FROM @rows;
END;
GO
CREATE FUNCTION ref.ufn_analytic_dimension(@run_id UNIQUEIDENTIFIER,@reference_revision INT,@date DATE)
RETURNS TABLE AS RETURN (
 SELECT b.*,r.reference_release_id,r.raw_name,r.normalized_name,r.normalization_version,r.raw_document,r.license_plate,
 r.branch_key,r.capacity,r.capacity_unit,r.classification,r.source_path,r.vehicle_type,r.vehicle_owner,
 CONVERT(VARCHAR(24),CASE WHEN b.active=0 THEN 'INACTIVE_BINDING' WHEN r.entity_key IS NULL THEN 'MISSING_REGISTRY'
 WHEN r.active=0 THEN 'INACTIVE_REGISTRY' WHEN NOT EXISTS(SELECT 1 FROM core.analytic_lab_source_current s
 WHERE s.run_id=b.run_id AND s.entity=b.entity AND s.source_key=b.source_key AND s.usable=1) THEN 'SOURCE_UNAVAILABLE'
 WHEN b.dimension_kind='FILIAL' AND selected.branch_policy='UNRESOLVED' THEN 'BRANCH_UNRESOLVED'
 WHEN b.dimension_kind='MOTORISTA' AND selected.driver_policy='UNRESOLVED' THEN 'DRIVER_UNRESOLVED'
 WHEN b.dimension_kind='MOTORISTA' AND selected.driver_policy='EXCLUDE_GENERIC' AND EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x
 WHERE x.reference_release_id=selected.reference_release_id AND x.kind='GENERIC_DRIVER'
 AND CHARINDEX(x.match_value,r.normalized_name)>0) THEN 'GENERIC_EXCLUDED' ELSE 'RESOLVED' END) disposition
 FROM ref.analytic_lab_dimension_binding b OUTER APPLY ref.ufn_analytic_reference(b.run_id,@reference_revision,@date) selected
 LEFT JOIN ref.analytic_lab_registry r ON r.reference_release_id=selected.reference_release_id AND r.dimension_kind=b.dimension_kind
 AND r.entity_key=b.entity_key AND r.valid_from<=@date AND r.valid_to_exclusive>@date
 WHERE b.run_id=@run_id AND b.valid_from<=@date AND b.valid_to_exclusive>@date
 AND NOT EXISTS(SELECT 1 FROM ref.analytic_lab_dimension_binding later WHERE later.run_id=b.run_id AND later.entity=b.entity
 AND later.source_key=b.source_key AND later.role=b.role AND later.revision>b.revision
 AND later.valid_from<=@date AND later.valid_to_exclusive>@date)
);
GO
CREATE VIEW core.analytic_lab_dimension_usage AS
 WITH digit(n) AS(SELECT n FROM(VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) d(n)),
 day_number(n) AS(SELECT a.n+10*b.n+100*c.n+1000*d.n FROM digit a CROSS JOIN digit b CROSS JOIN digit c CROSS JOIN digit d)
 SELECT DISTINCT selection.run_id,selection.revision reference_revision,selection.valid_from selection_from,
 selection.valid_to_exclusive selection_to,r.reference_release_id,r.dimension_kind,r.entity_key,r.raw_name,r.normalized_name,
 r.normalization_version,r.raw_document,r.license_plate,r.branch_key,r.capacity,r.capacity_unit,r.classification,
 days.business_date valid_from,DATEADD(day,1,days.business_date) valid_to_exclusive,r.source_path,r.vehicle_type,r.vehicle_owner
 FROM ctl.analytic_lab_reference_selection selection JOIN day_number n ON n.n<DATEDIFF(day,selection.valid_from,selection.valid_to_exclusive)
 CROSS APPLY(SELECT DATEADD(day,n.n,selection.valid_from) business_date) days
 CROSS APPLY ref.ufn_analytic_dimension(selection.run_id,selection.revision,days.business_date) r
 WHERE r.disposition='RESOLVED';
GO
CREATE VIEW pub.analytic_lab_sql_14 AS
 SELECT d.normalized_name [NomeFilial],CONVERT(TIME(0),'00:00:00') [Hora (Solicitacao)],
 d.run_id,d.reference_revision,d.entity_key,d.valid_from,d.valid_to_exclusive,d.raw_name,d.normalization_version,d.source_path,d.reference_release_id
 FROM core.analytic_lab_dimension_usage d WHERE d.dimension_kind='FILIAL';
GO
CREATE VIEW pub.analytic_lab_sql_15 AS
 SELECT d.normalized_name [Nome],d.run_id,d.reference_revision,d.entity_key,d.valid_from,d.valid_to_exclusive,d.raw_name,
 d.normalization_version,d.source_path,d.reference_release_id,d.raw_document
 FROM core.analytic_lab_dimension_usage d WHERE d.dimension_kind='CLIENTE';
GO
CREATE VIEW pub.analytic_lab_sql_16 AS
 SELECT UPPER(LTRIM(RTRIM(d.license_plate))) [Placa],UPPER(LTRIM(RTRIM(d.vehicle_type))) [TipoVeiculo],
 UPPER(LTRIM(RTRIM(d.vehicle_owner))) [Proprietario],branch.normalized_name [Filial],
 d.run_id,d.reference_revision,d.entity_key,d.valid_from,d.valid_to_exclusive,d.raw_name,d.normalization_version,
 d.source_path,d.reference_release_id,d.license_plate raw_plate,d.capacity,d.capacity_unit,d.branch_key
 FROM core.analytic_lab_dimension_usage d JOIN ref.analytic_lab_registry branch ON branch.reference_release_id=d.reference_release_id
 AND branch.dimension_kind='FILIAL' AND branch.entity_key=d.branch_key AND branch.active=1
 AND branch.valid_from<=d.valid_from AND branch.valid_to_exclusive>=d.valid_to_exclusive
 WHERE d.dimension_kind='VEICULO';
GO
CREATE VIEW pub.analytic_lab_sql_17 AS
 SELECT d.normalized_name [NomeMotorista],branch.normalized_name [Filial],d.run_id,d.reference_revision,d.entity_key,
 d.valid_from,d.valid_to_exclusive,d.raw_name,d.normalization_version,d.source_path,d.reference_release_id,d.branch_key
 FROM core.analytic_lab_dimension_usage d JOIN ctl.analytic_lab_reference_selection selection ON selection.run_id=d.run_id
 AND selection.revision=d.reference_revision AND selection.valid_from=d.selection_from
 JOIN ref.analytic_lab_registry branch ON branch.reference_release_id=d.reference_release_id AND branch.dimension_kind='FILIAL'
 AND branch.entity_key=d.branch_key AND branch.active=1 AND branch.valid_from<=d.valid_from AND branch.valid_to_exclusive>=d.valid_to_exclusive
 WHERE d.dimension_kind='MOTORISTA' AND selection.driver_policy<>'UNRESOLVED'
 AND (selection.driver_policy='INCLUDE_ALL_BOUND' OR NOT EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x
 WHERE x.reference_release_id=d.reference_release_id AND x.kind='GENERIC_DRIVER' AND CHARINDEX(x.match_value,d.normalized_name)>0));
GO
CREATE VIEW pub.analytic_lab_sql_18 AS
 SELECT d.normalized_name [Descricao],COALESCE(d.classification,N'OUTROS / NÃO CLASSIFICADO') [Classificacao],
 CONVERT(TIME(0),'00:00:00') [Hora (Solicitacao)],d.run_id,d.reference_revision,d.entity_key,d.valid_from,d.valid_to_exclusive,
 d.raw_name,d.normalization_version,d.source_path,d.reference_release_id,d.classification raw_classification
 FROM core.analytic_lab_dimension_usage d WHERE d.dimension_kind='PLANOCONTAS';
GO
CREATE VIEW pub.analytic_lab_sql_19 AS
 SELECT u.source_key_token [User ID],LTRIM(RTRIM(u.usuario_name)) [Nome],u.last_changed_at_utc [Data Atualizacao],
 a.run_id,u.usuario_id,u.source_instance,u.tenant_scope,u.source_key_wire_type,u.name_presence,u.usuario_name raw_name,
 CONVERT(VARCHAR(32),'TRIM_V1') normalization_version,a.execution_id,u.last_seen_at_utc
 FROM ctl.analytic_lab_execution_source a JOIN core.usuario stored_user ON stored_user.last_seen_execution_id=a.execution_id
 JOIN core.v_usuario_dimension_current_v1 u ON u.usuario_id=stored_user.usuario_id WHERE a.entity='USUARIO';
GO
