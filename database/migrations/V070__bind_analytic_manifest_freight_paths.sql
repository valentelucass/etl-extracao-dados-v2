-- Synthetic crosswalk and direct relation are explicit inputs, never inferred from equal numeric keys.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE stg.analytic_lab_freight_relation(
 binding_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_source_group(run_id),
 kind VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,
 origin_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,origin_execution UNIQUEIDENTIFIER NOT NULL,
 freight_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,freight_execution UNIQUEIDENTIFIER NOT NULL,
 revision INT NOT NULL,active BIT NOT NULL,previous_freight_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
 evidence VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT FK_analytic_freight_relation_origin FOREIGN KEY(origin_execution,origin_key) REFERENCES stg.relational_lab_root(execution_id,source_key),
 CONSTRAINT FK_analytic_freight_relation_capture FOREIGN KEY(freight_execution) REFERENCES ctl.expansion_lab_dependency_capture(execution_id),
 CONSTRAINT UQ_analytic_freight_relation UNIQUE(run_id,kind,origin_key,freight_key,revision),
 CONSTRAINT CK_analytic_freight_relation CHECK(kind IN('CROSSWALK','DIRECT') AND revision BETWEEN 1 AND 100000
 AND evidence='synthetic-analytic-freight-relation-v1' AND stg.fn_relational_lab_key_valid(origin_key)=1
 AND stg.fn_relational_lab_key_valid(freight_key)=1 AND origin_key LIKE N'INTEGER:%' AND freight_key LIKE N'INTEGER:%'
 AND(kind='CROSSWALK' OR previous_freight_key IS NULL))
);
CREATE UNIQUE INDEX UQ_analytic_freight_crosswalk ON stg.analytic_lab_freight_relation(run_id,origin_key,revision) WHERE kind='CROSSWALK';
CREATE INDEX IX_analytic_freight_relation_current ON stg.analytic_lab_freight_relation(run_id,kind,origin_key,freight_key,revision DESC) INCLUDE(active,origin_execution,freight_execution);
GO
CREATE TABLE ctl.analytic_manifest_composition(
 composition_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_source_group(run_id),
 manifest_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,manifest_execution UNIQUEIDENTIFIER NOT NULL,
 revision INT NOT NULL,expected_direct_freights INT NOT NULL,complete BIT NOT NULL,evidence VARCHAR(64) NOT NULL,
 CONSTRAINT FK_analytic_manifest_composition_source FOREIGN KEY(manifest_execution,manifest_key) REFERENCES stg.relational_lab_root(execution_id,source_key),
 CONSTRAINT UQ_analytic_manifest_composition UNIQUE(run_id,manifest_key,revision),
 CONSTRAINT CK_analytic_manifest_composition CHECK(revision BETWEEN 1 AND 100000 AND expected_direct_freights BETWEEN 0 AND 100000
 AND complete=1 AND evidence='synthetic-manifest-composition-v1')
);
GO
CREATE TRIGGER stg.trg_analytic_freight_relation ON stg.analytic_lab_freight_relation AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53640,N'ANA_FREIGHT_RELATION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_source_group g ON g.run_id=i.run_id
 JOIN ctl.relational_lab_capture origin ON origin.execution_id=i.origin_execution
 JOIN ctl.expansion_lab_dependency_capture target ON target.execution_id=i.freight_execution
 WHERE origin.run_id<>g.relational_run OR origin.entity_name<>CASE i.kind WHEN 'DIRECT' THEN N'manifestos' ELSE N'fretes' END
 OR target.run_id<>g.expansion_run OR target.entity<>'FRETE' OR target.state<>'COMPLETE'
 OR NOT EXISTS(SELECT 1 FROM stg.expansion_lab_dependency_observation o WHERE o.execution_id=i.freight_execution AND o.source_key=i.freight_key
 AND o.disposition IN('INSERTED','UPDATED','NOOP','DUPLICATE','STALE'))
 OR NOT EXISTS(SELECT 1 FROM core.expansion_lab_dependency d WHERE d.run_id=g.expansion_run AND d.entity='FRETE' AND d.source_key=i.freight_key AND d.state='VALID'))
 THROW 53641,N'ANA_FREIGHT_RELATION_CAPTURE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM inserted i WHERE i.kind='DIRECT' AND EXISTS(SELECT 1 FROM ctl.analytic_manifest_composition s
 WHERE s.run_id=i.run_id AND s.manifest_key=i.origin_key AND s.revision>=i.revision)) THROW 53642,N'ANA_MANIFEST_COMPOSITION_SEALED',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN stg.analytic_lab_freight_relation old WITH(UPDLOCK,HOLDLOCK)
 ON old.run_id=i.run_id AND old.kind='CROSSWALK' AND i.kind='CROSSWALK' AND old.origin_key=i.origin_key AND old.revision<i.revision
 WHERE old.freight_key<>i.freight_key AND NOT EXISTS(SELECT 1 FROM stg.analytic_lab_freight_relation newer
 WHERE newer.run_id=old.run_id AND newer.kind=old.kind AND newer.origin_key=old.origin_key AND newer.revision>old.revision AND newer.revision<i.revision)
 AND(i.previous_freight_key IS NULL OR i.previous_freight_key<>old.freight_key)) THROW 53643,N'ANA_CROSSWALK_REKEY_REQUIRED',1;
 -- A crosswalk is one-to-one in this explicit local contract; expansion paths may be many-to-many.
 IF EXISTS(SELECT 1 FROM inserted i JOIN stg.analytic_lab_freight_relation other WITH(UPDLOCK,HOLDLOCK)
 ON other.run_id=i.run_id AND other.kind='CROSSWALK' AND i.kind='CROSSWALK' AND other.freight_key=i.freight_key AND other.origin_key<>i.origin_key
 WHERE i.active=1 AND other.active=1 AND NOT EXISTS(SELECT 1 FROM stg.analytic_lab_freight_relation later
 WHERE later.run_id=other.run_id AND later.kind=other.kind AND later.origin_key=other.origin_key AND later.revision>other.revision))
 THROW 53644,N'ANA_CROSSWALK_CARDINALITY',1;
END;
GO
CREATE VIEW core.analytic_lab_freight_relation_current AS
 SELECT b.* FROM stg.analytic_lab_freight_relation b WHERE NOT EXISTS(SELECT 1 FROM stg.analytic_lab_freight_relation later
 WHERE later.run_id=b.run_id AND later.kind=b.kind AND later.origin_key=b.origin_key AND later.revision>b.revision
 AND(b.kind='CROSSWALK' OR later.freight_key=b.freight_key));
GO
CREATE TRIGGER ctl.trg_analytic_manifest_composition ON ctl.analytic_manifest_composition AFTER INSERT,UPDATE,DELETE
AS BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 53645,N'ANA_MANIFEST_COMPOSITION_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN ctl.analytic_lab_source_group g ON g.run_id=i.run_id
 JOIN ctl.relational_lab_capture c ON c.execution_id=i.manifest_execution
 WHERE c.run_id<>g.relational_run OR c.entity_name<>N'manifestos'
 OR i.expected_direct_freights<>(SELECT COUNT_BIG(*) FROM core.analytic_lab_freight_relation_current b
 WHERE b.run_id=i.run_id AND b.kind='DIRECT' AND b.origin_key=i.manifest_key AND b.active=1)
 OR EXISTS(SELECT 1 FROM core.analytic_lab_freight_relation_current b WHERE b.run_id=i.run_id AND b.kind='DIRECT' AND b.origin_key=i.manifest_key AND b.revision>i.revision))
 THROW 53646,N'ANA_MANIFEST_COMPOSITION_COUNT_SCOPE',1;
END;
GO
CREATE TYPE stg.analytic_lab_freight_relation_batch AS TABLE(
 kind VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,origin_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 origin_execution UNIQUEIDENTIFIER NOT NULL,freight_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 freight_execution UNIQUEIDENTIFIER NOT NULL,revision INT NOT NULL,active BIT NOT NULL,
 previous_freight_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
 PRIMARY KEY(kind,origin_key,freight_key,revision)
);
GO
CREATE PROCEDURE stg.usp_bind_analytic_freight_relations @run_id UNIQUEIDENTIFIER,@rows stg.analytic_lab_freight_relation_batch READONLY
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'PLAN';
 IF (SELECT COUNT_BIG(*) FROM @rows) NOT BETWEEN 1 AND 64 THROW 53647,N'ANA_FREIGHT_RELATION_BATCH_BOUND',1;
 IF EXISTS(SELECT 1 FROM @rows i JOIN stg.analytic_lab_freight_relation b ON b.run_id=@run_id AND b.kind=i.kind AND b.origin_key=i.origin_key AND b.freight_key=i.freight_key AND b.revision=i.revision
 WHERE b.origin_execution<>i.origin_execution OR b.freight_execution<>i.freight_execution OR b.active<>i.active
 OR EXISTS(SELECT b.previous_freight_key EXCEPT SELECT i.previous_freight_key)) THROW 53648,N'ANA_FREIGHT_RELATION_REVISION_CONFLICT',1;
 INSERT stg.analytic_lab_freight_relation(run_id,kind,origin_key,origin_execution,freight_key,freight_execution,revision,active,previous_freight_key,evidence)
 SELECT @run_id,i.*,'synthetic-analytic-freight-relation-v1' FROM @rows i WHERE NOT EXISTS(SELECT 1 FROM stg.analytic_lab_freight_relation b
 WHERE b.run_id=@run_id AND b.kind=i.kind AND b.origin_key=i.origin_key AND b.freight_key=i.freight_key AND b.revision=i.revision);
 SELECT COUNT_BIG(*) FROM @rows;
END;
GO
CREATE PROCEDURE ctl.usp_seal_analytic_manifest_composition @run_id UNIQUEIDENTIFIER,@manifest_key NVARCHAR(128),
 @manifest_execution UNIQUEIDENTIFIER,@revision INT,@expected_direct_freights INT
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'PLAN';
 IF EXISTS(SELECT 1 FROM ctl.analytic_manifest_composition WHERE run_id=@run_id AND manifest_key=@manifest_key AND revision=@revision
 AND(manifest_execution<>@manifest_execution OR expected_direct_freights<>@expected_direct_freights)) THROW 53649,N'ANA_COMPOSITION_REVISION_CONFLICT',1;
 INSERT ctl.analytic_manifest_composition(run_id,manifest_key,manifest_execution,revision,expected_direct_freights,complete,evidence)
 SELECT @run_id,@manifest_key,@manifest_execution,@revision,@expected_direct_freights,1,'synthetic-manifest-composition-v1'
 WHERE NOT EXISTS(SELECT 1 FROM ctl.analytic_manifest_composition WHERE run_id=@run_id AND manifest_key=@manifest_key AND revision=@revision);
 SELECT composition_id FROM ctl.analytic_manifest_composition WHERE run_id=@run_id AND manifest_key=@manifest_key AND revision=@revision;
END;
GO
