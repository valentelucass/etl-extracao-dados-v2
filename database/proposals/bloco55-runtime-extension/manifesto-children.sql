    -- Child observations preserve their own latest cohort; absence does not delete a child.
    IF EXISTS(SELECT 1 FROM stg.manifesto_mdfe_candidate x
      JOIN core.manifesto m ON m.environment_name=@environment_name AND m.source_instance=@source_instance
        AND m.tenant_scope=@tenant_scope AND m.source_key=x.source_key
      JOIN core.manifesto_mdfe c ON c.manifesto_id=m.manifesto_id AND c.mdfe_key=x.mdfe_key
      WHERE x.execution_id=@execution_id AND (c.freshness_at_utc=x.freshness_at_utc OR c.freshness_at_utc IS NULL)
        AND c.mdfe_number<>x.mdfe_number) THROW 52833,N'MDFE_EQUAL_FRESHNESS_CONFLICT',1;
    INSERT core.manifesto_pick(manifesto_id,pick_source_key,first_seen_execution_id,freshness_at_utc)
    SELECT m.manifesto_id,x.pick_source_key,@execution_id,x.freshness_at_utc
    FROM stg.manifesto_pick_candidate x JOIN core.manifesto m ON m.environment_name=@environment_name
      AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.source_key=x.source_key
    WHERE x.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.manifesto_pick c WITH(UPDLOCK,HOLDLOCK)
      WHERE c.manifesto_id=m.manifesto_id AND c.pick_source_key=x.pick_source_key);
    INSERT core.manifesto_mdfe(manifesto_id,mdfe_key,mdfe_number,first_seen_execution_id,freshness_at_utc)
    SELECT m.manifesto_id,x.mdfe_key,x.mdfe_number,@execution_id,x.freshness_at_utc
    FROM stg.manifesto_mdfe_candidate x JOIN core.manifesto m ON m.environment_name=@environment_name
      AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope AND m.source_key=x.source_key
    WHERE x.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.manifesto_mdfe c WITH(UPDLOCK,HOLDLOCK)
      WHERE c.manifesto_id=m.manifesto_id AND c.mdfe_key=x.mdfe_key);
    UPDATE c SET freshness_at_utc=x.freshness_at_utc
    FROM core.manifesto_pick c JOIN core.manifesto m ON m.manifesto_id=c.manifesto_id
    JOIN stg.manifesto_pick_candidate x ON x.execution_id=@execution_id AND x.source_key=m.source_key AND x.pick_source_key=c.pick_source_key
    WHERE m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope
      AND (c.freshness_at_utc IS NULL OR c.freshness_at_utc<x.freshness_at_utc);
    UPDATE c SET mdfe_number=x.mdfe_number,freshness_at_utc=x.freshness_at_utc
    FROM core.manifesto_mdfe c JOIN core.manifesto m ON m.manifesto_id=c.manifesto_id
    JOIN stg.manifesto_mdfe_candidate x ON x.execution_id=@execution_id AND x.source_key=m.source_key AND x.mdfe_key=c.mdfe_key
    WHERE m.environment_name=@environment_name AND m.source_instance=@source_instance AND m.tenant_scope=@tenant_scope
      AND (c.freshness_at_utc IS NULL OR c.freshness_at_utc<x.freshness_at_utc);
