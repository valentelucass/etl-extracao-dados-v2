-- Exclude inactive component amounts from current root/part reducers; retain observations and history.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
ALTER PROCEDURE core.usp_apply_expansion_laboratory
 @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT=0 THROW 53300,N'EXP_TRANSACTION_REQUIRED',1;
 DECLARE @lock INT,@resource NVARCHAR(255)=CONCAT(N'EXP_APPLY_',@run_id);
 EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1200;
 IF @lock<0 THROW 53302,N'EXP_APPLY_BUSY',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_capture WHERE run_id=@run_id AND execution_id=@execution_id AND state='COMPLETE')
  THROW 53301,N'EXP_COMPLETE_CAPTURE_REQUIRED',1;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_apply_receipt WHERE execution_id=@execution_id)
 BEGIN
  SELECT observations,inserts,updates,noops,stale,quarantine,unbound,duplicates
   FROM ctl.expansion_lab_apply_receipt WHERE execution_id=@execution_id;
  RETURN;
 END;

 UPDATE stg.expansion_lab_observation SET disposition=CASE
  WHEN valid=0 THEN 'QUARANTINE_FIELD'
  WHEN root_key IS NULL THEN 'UNBOUND'
  WHEN fresh_second IS NULL THEN 'QUARANTINE_FRESHNESS'
  ELSE 'CANDIDATE' END
 WHERE execution_id=@execution_id;

 -- Child identifiers are scoped by the declared root; identical local labels across roots are valid.

 ;WITH ranked AS(
  SELECT o.*,DENSE_RANK() OVER(PARTITION BY root_type,root_key,part_type,part_key,component_type,component_key
   ORDER BY fresh_second DESC,fresh_nano DESC,fresh_inclusive DESC,revision DESC) AS freshness_rank
  FROM stg.expansion_lab_observation o WHERE execution_id=@execution_id AND disposition='CANDIDATE'
 )
 SELECT * INTO #ranked FROM ranked;
 CREATE INDEX IX_exp_ranked_key ON #ranked(root_type,root_key,part_type,part_key,component_type,component_key,freshness_rank);
 UPDATE o SET disposition='STALE' FROM stg.expansion_lab_observation o
 JOIN #ranked r ON r.observation_id=o.observation_id WHERE r.freshness_rank>1;
 UPDATE o SET disposition='QUARANTINE_TIE' FROM stg.expansion_lab_observation o
 JOIN #ranked r ON r.observation_id=o.observation_id WHERE r.freshness_rank=1 AND EXISTS(
  SELECT 1 FROM #ranked d WHERE d.freshness_rank=1 AND d.root_type=r.root_type AND d.root_key=r.root_key
   AND d.part_type=r.part_type AND d.part_key=r.part_key AND d.component_type=r.component_type AND d.component_key=r.component_key
   AND (d.comparison_bytes<>r.comparison_bytes OR d.active<>r.active OR d.currency<>r.currency
    OR d.unit<>r.unit OR d.additive_allocation<>r.additive_allocation OR d.binding_evidence<>r.binding_evidence));
 -- Choosing the first technical address is permitted only after exact equality of every tied value.
 SELECT MIN(observation_id) AS observation_id INTO #chosen
 FROM stg.expansion_lab_observation WHERE execution_id=@execution_id AND disposition='CANDIDATE'
 GROUP BY root_type,root_key,part_type,part_key,component_type,component_key;
 UPDATE o SET disposition='DUPLICATE' FROM stg.expansion_lab_observation o
 WHERE execution_id=@execution_id AND disposition='CANDIDATE'
 AND NOT EXISTS(SELECT 1 FROM #chosen c WHERE c.observation_id=o.observation_id);

 UPDATE o SET disposition='QUARANTINE_CURRENCY' FROM stg.expansion_lab_observation o
 JOIN core.expansion_lab_root r ON r.run_id=o.run_id AND r.vertical=o.vertical AND r.root_type=o.root_type AND r.root_key=o.root_key
 WHERE o.execution_id=@execution_id AND o.disposition='CANDIDATE' AND (r.currency<>o.currency OR r.unit<>o.unit);
 UPDATE o SET disposition='QUARANTINE_CURRENCY' FROM stg.expansion_lab_observation o
 WHERE o.execution_id=@execution_id AND o.disposition='CANDIDATE' AND EXISTS(
  SELECT 1 FROM stg.expansion_lab_observation other WHERE other.execution_id=o.execution_id AND other.root_type=o.root_type
  AND other.root_key=o.root_key AND (other.currency<>o.currency OR other.unit<>o.unit));

 INSERT core.expansion_lab_root(run_id,vertical,root_type,root_key,currency,unit)
 SELECT @run_id,o.vertical,o.root_type,o.root_key,MIN(o.currency),MIN(o.unit)
 FROM stg.expansion_lab_observation o
 WHERE o.execution_id=@execution_id AND o.disposition='CANDIDATE'
 AND NOT EXISTS(SELECT 1 FROM core.expansion_lab_root r WHERE r.run_id=@run_id AND r.vertical=o.vertical AND r.root_type=o.root_type AND r.root_key=o.root_key)
 GROUP BY o.vertical,o.root_type,o.root_key;

 SELECT o.observation_id,r.root_id,c.component_id,c.observation_id AS old_id,
 CASE WHEN c.component_id IS NULL THEN 'INSERT'
 WHEN prior.revision>o.revision THEN 'STALE'
 WHEN prior.active=0 AND o.active=1 AND (o.reactivation=0 OR o.revision<=prior.revision) THEN 'QUARANTINE_REACTIVATION'
 WHEN o.fresh_second<prior.fresh_second OR (o.fresh_second=prior.fresh_second AND o.fresh_nano<prior.fresh_nano)
  OR(o.fresh_second=prior.fresh_second AND o.fresh_nano=prior.fresh_nano AND o.fresh_inclusive<prior.fresh_inclusive) THEN 'STALE'
 WHEN o.fresh_second=prior.fresh_second AND o.fresh_nano=prior.fresh_nano AND o.fresh_inclusive=prior.fresh_inclusive AND o.revision=prior.revision
  AND (o.comparison_bytes<>prior.comparison_bytes OR o.active<>prior.active OR o.additive_allocation<>prior.additive_allocation
    OR o.binding_evidence<>prior.binding_evidence) THEN 'QUARANTINE_TIE'
 WHEN o.comparison_bytes=prior.comparison_bytes AND o.active=prior.active AND o.revision=prior.revision
  AND o.additive_allocation=prior.additive_allocation AND o.binding_evidence=prior.binding_evidence THEN 'NOOP'
 ELSE 'UPDATE' END AS action
 INTO #decisions
 FROM stg.expansion_lab_observation o
 JOIN core.expansion_lab_root r ON r.run_id=o.run_id AND r.vertical=o.vertical AND r.root_type=o.root_type AND r.root_key=o.root_key
 LEFT JOIN core.expansion_lab_component c ON c.root_id=r.root_id AND c.part_type=o.part_type AND c.part_key=o.part_key
  AND c.component_type=o.component_type AND c.component_key=o.component_key
 LEFT JOIN stg.expansion_lab_observation prior ON prior.observation_id=c.observation_id
 WHERE o.execution_id=@execution_id AND o.disposition='CANDIDATE';
 UPDATE o SET disposition=d.action FROM stg.expansion_lab_observation o JOIN #decisions d ON d.observation_id=o.observation_id;

 -- INV-03 proof is cumulative even when a later arrival is stale; quarantined evidence is excluded.
 UPDATE c SET proof_attached=1 FROM core.expansion_lab_component c
 JOIN #decisions d ON d.component_id=c.component_id AND d.action IN('UPDATE','NOOP','STALE')
 JOIN stg.expansion_lab_observation o ON o.observation_id=d.observation_id WHERE o.proof_attached=1;
 UPDATE c SET observation_id=d.observation_id,active=o.active FROM core.expansion_lab_component c
 JOIN #decisions d ON d.component_id=c.component_id AND d.action='UPDATE'
 JOIN stg.expansion_lab_observation o ON o.observation_id=d.observation_id;
 INSERT core.expansion_lab_component(root_id,part_type,part_key,component_type,component_key,observation_id,active,proof_attached)
 SELECT d.root_id,o.part_type,o.part_key,o.component_type,o.component_key,o.observation_id,o.active,o.proof_attached
 FROM #decisions d JOIN stg.expansion_lab_observation o ON o.observation_id=d.observation_id WHERE d.action='INSERT';
 INSERT core.expansion_lab_history(component_id,old_observation_id,observation_id,execution_id,action,recorded_at)
 SELECT c.component_id,d.old_id,d.observation_id,@execution_id,d.action,@now FROM #decisions d
 JOIN core.expansion_lab_component c ON c.root_id=d.root_id AND c.observation_id=d.observation_id WHERE d.action IN('INSERT','UPDATE');

 ;WITH reduced AS(
 SELECT c.root_id,MIN(CASE WHEN c.active=1 THEN o.root_amount END) AS amount,COUNT(DISTINCT CASE WHEN c.active=1 THEN o.root_amount END) AS amount_count,
 COUNT(CASE WHEN c.active=1 THEN o.root_amount END) AS present_count,SUM(CONVERT(BIGINT,c.active)) AS components,
 MAX(CONVERT(INT,c.proof_attached)) AS proof,MAX(CONVERT(INT,c.active)) AS active
 FROM core.expansion_lab_component c JOIN stg.expansion_lab_observation o ON o.observation_id=c.observation_id
 JOIN core.expansion_lab_root r ON r.root_id=c.root_id AND r.run_id=@run_id
 WHERE EXISTS(SELECT 1 FROM #decisions d WHERE d.root_id=c.root_id)
 GROUP BY c.root_id)
 UPDATE r SET amount=CASE WHEN d.amount_count=1 AND d.present_count=d.components THEN d.amount ELSE NULL END,
 amount_state=CASE WHEN d.present_count=0 THEN 'NULL' WHEN d.amount_count=1 AND d.present_count=d.components THEN 'VALUE' ELSE 'CONFLICT' END,
 proof_attached=CASE WHEN r.proof_attached=1 OR d.proof=1 THEN 1 ELSE 0 END,active=d.active
 FROM core.expansion_lab_root r JOIN reduced d ON d.root_id=r.root_id;
 SELECT c.root_id,c.part_type,c.part_key,MIN(CASE WHEN c.active=1 THEN o.part_amount END) AS amount,
 CASE WHEN COUNT(CASE WHEN c.active=1 THEN o.part_amount END)=0 THEN 'NULL' WHEN COUNT(DISTINCT CASE WHEN c.active=1 THEN o.part_amount END)=1 AND COUNT(CASE WHEN c.active=1 THEN o.part_amount END)=SUM(CONVERT(BIGINT,c.active)) THEN 'VALUE' ELSE 'CONFLICT' END AS amount_state
 INTO #parts FROM core.expansion_lab_component c JOIN stg.expansion_lab_observation o ON o.observation_id=c.observation_id
 WHERE EXISTS(SELECT 1 FROM #decisions d WHERE d.root_id=c.root_id)
 GROUP BY c.root_id,c.part_type,c.part_key;
 UPDATE p SET amount=CASE WHEN d.amount_state='VALUE' THEN d.amount ELSE NULL END,amount_state=d.amount_state
 FROM core.expansion_lab_part p JOIN #parts d ON d.root_id=p.root_id AND d.part_type=p.part_type AND d.part_key=p.part_key;
 INSERT core.expansion_lab_part(root_id,part_type,part_key,amount,amount_state)
 SELECT d.root_id,d.part_type,d.part_key,CASE WHEN d.amount_state='VALUE' THEN d.amount ELSE NULL END,d.amount_state FROM #parts d
 WHERE NOT EXISTS(SELECT 1 FROM core.expansion_lab_part p WHERE p.root_id=d.root_id AND p.part_type=d.part_type AND p.part_key=d.part_key);

 INSERT ctl.expansion_lab_apply_receipt(execution_id,run_id,observations,inserts,updates,noops,stale,quarantine,unbound,duplicates,recorded_at)
 SELECT @execution_id,@run_id,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='INSERT' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='NOOP' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='STALE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition LIKE 'QUARANTINE_%' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='UNBOUND' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='DUPLICATE' THEN 1 ELSE 0 END)),0),@now
 FROM stg.expansion_lab_observation WHERE execution_id=@execution_id;
 SELECT observations,inserts,updates,noops,stale,quarantine,unbound,duplicates
 FROM ctl.expansion_lab_apply_receipt WHERE execution_id=@execution_id;
END;
GO
